package com.campus.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.CreditChangeTypeConstant;
import com.campus.common.constant.RedisConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.security.JwtUtil;
import com.campus.common.security.UserContext;
import com.campus.user.dto.LoginDTO;
import com.campus.user.dto.RegisterDTO;
import com.campus.user.dto.UpdateProfileDTO;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.CreditLogService;
import com.campus.user.service.UserService;
import com.campus.user.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    @Lazy
    private CreditLogService creditLogService;

    @Override
    @Transactional
    public UserVO register(RegisterDTO registerDTO) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, registerDTO.getUsername());
        User existUser = getOne(wrapper);
        if (existUser != null) {
            throw new BusinessException("用户名已存在");
        }

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
        user.setNickname(registerDTO.getNickname() != null ? registerDTO.getNickname() : registerDTO.getUsername());
        user.setPhone(registerDTO.getPhone());
        user.setStatus(1);
        user.setRole("STUDENT");
        user.setCreditScore(100);
        user.setDeposit(BigDecimal.ZERO);
        user.setFrozenDeposit(BigDecimal.ZERO);
        save(user);

        creditLogService.addCreditLog(user.getId(), CreditChangeTypeConstant.REGISTER, 100, "注册初始信用分");

        return toVO(user);
    }

    @Override
    public String login(LoginDTO loginDTO) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = getOne(wrapper);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        try {
            if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
                throw new BusinessException("密码错误");
            }
        } catch (IllegalArgumentException e) {
            log.error("密码验证失败，数据库中密码格式异常, userId={}", user.getId(), e);
            throw new BusinessException("账号数据异常，请联系管理员重置密码");
        }
        if (user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用");
        }

        String role = user.getRole() != null ? user.getRole() : "STUDENT";
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), role);
        if (stringRedisTemplate != null) {
            stringRedisTemplate.opsForValue().set(RedisConstant.USER_TOKEN_PREFIX + token, user.getId().toString(), 7, TimeUnit.DAYS);
        }

        return token;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return toVO(user);
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException("未登录");
        }
        return getUserInfo(userId);
    }

    @Override
    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (dto.getNickname() != null) user.setNickname(dto.getNickname());
        if (dto.getAvatar() != null) user.setAvatar(dto.getAvatar());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        updateById(user);
        return toVO(user);
    }

    // ==================== 保证金账户（可用余额 deposit / 冻结余额 frozenDeposit） ====================

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("金额必须大于0");
        }
    }

    /**
     * 充值：增加可用余额（模拟，真实场景应接支付渠道）。
     */
    @Override
    @Transactional
    public boolean recharge(Long userId, BigDecimal amount) {
        validateAmount(amount);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
               .setSql("deposit = deposit + {0}", amount);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("用户不存在");
        }
        log.info("保证金充值成功, userId={}, amount={}", userId, amount);
        return true;
    }

    /**
     * 冻结：可用余额 → 冻结余额（原子条件更新，防止并发超冻）。
     */
    @Override
    @Transactional
    public boolean freezeDeposit(Long userId, BigDecimal amount) {
        validateAmount(amount);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
               .ge(User::getDeposit, amount)
               .setSql("deposit = deposit - {0}", amount)
               .setSql("frozen_deposit = frozen_deposit + {0}", amount);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("保证金余额不足");
        }
        log.info("保证金冻结成功, userId={}, amount={}", userId, amount);
        return true;
    }

    /**
     * 解冻：冻结余额 → 可用余额（原子条件更新，杜绝"无冻结即解冻"凭空加钱）。
     */
    @Override
    @Transactional
    public boolean unfreezeDeposit(Long userId, BigDecimal amount) {
        validateAmount(amount);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
               .ge(User::getFrozenDeposit, amount)
               .setSql("frozen_deposit = frozen_deposit - {0}", amount)
               .setSql("deposit = deposit + {0}", amount);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("冻结金额不足，无法解冻");
        }
        log.info("保证金解冻成功, userId={}, amount={}", userId, amount);
        return true;
    }

    /**
     * 扣减：从冻结余额中永久扣除（资金离开买家，进入平台托管）。
     */
    @Override
    @Transactional
    public boolean deductDeposit(Long userId, BigDecimal amount) {
        validateAmount(amount);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
               .ge(User::getFrozenDeposit, amount)
               .setSql("frozen_deposit = frozen_deposit - {0}", amount);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("冻结金额不足，无法扣减");
        }
        log.info("保证金扣减成功, userId={}, amount={}", userId, amount);
        return true;
    }

    /**
     * 结算：给卖家入账（确认收货后由 order-service 调用）。
     */
    @Override
    @Transactional
    public boolean creditDeposit(Long userId, BigDecimal amount) {
        validateAmount(amount);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userId)
               .setSql("deposit = deposit + {0}", amount);
        int rows = baseMapper.update(null, wrapper);
        if (rows == 0) {
            throw new BusinessException("用户不存在");
        }
        log.info("保证金结算入账成功, userId={}, amount={}", userId, amount);
        return true;
    }

    @Override
    public UserVO toVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
