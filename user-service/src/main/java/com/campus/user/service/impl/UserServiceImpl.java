package com.campus.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        save(user);

        creditLogService.addCreditLog(user.getId(), CreditChangeTypeConstant.REGISTER, 100, "注册初始信用分");

        return convertToVO(user);
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
        return convertToVO(user);
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
        return convertToVO(user);
    }

    @Override
    @Transactional
    public boolean freezeDeposit(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getDeposit().compareTo(amount) < 0) {
            throw new BusinessException("保证金余额不足");
        }
        user.setDeposit(user.getDeposit().subtract(amount));
        updateById(user);
        log.info("保证金冻结成功, userId={}, amount={}, balance={}", userId, amount, user.getDeposit());
        return true;
    }

    @Override
    @Transactional
    public boolean unfreezeDeposit(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setDeposit(user.getDeposit().add(amount));
        updateById(user);
        log.info("保证金解冻成功, userId={}, amount={}, balance={}", userId, amount, user.getDeposit());
        return true;
    }

    @Override
    @Transactional
    public boolean deductDeposit(Long userId, BigDecimal amount) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getDeposit().compareTo(amount) < 0) {
            throw new BusinessException("保证金余额不足");
        }
        user.setDeposit(user.getDeposit().subtract(amount));
        updateById(user);
        log.info("保证金额扣减成功, userId={}, amount={}, balance={}", userId, amount, user.getDeposit());
        return true;
    }

    private UserVO convertToVO(User user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        return vo;
    }
}
