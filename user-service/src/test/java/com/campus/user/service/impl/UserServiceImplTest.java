package com.campus.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BusinessException;
import com.campus.common.security.JwtUtil;
import com.campus.user.dto.LoginDTO;
import com.campus.user.dto.RegisterDTO;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import com.campus.user.service.CreditLogService;
import com.campus.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务单元测试")
class UserServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CreditLogService creditLogService;
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("用户注册")
    class RegisterTests {

        @Test
        @DisplayName("注册成功")
        void registerSuccess() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("testuser");
            dto.setPassword("123456");
            dto.setNickname("测试用户");
            dto.setPhone("13800138000");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(passwordEncoder.encode("123456")).thenReturn("$2a$10$encodedPassword");
            when(userMapper.insert(any(User.class))).thenReturn(1);
            doNothing().when(creditLogService).addCreditLog(anyLong(), anyString(), anyInt(), anyString());

            UserVO result = userService.register(dto);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
            assertThat(result.getCreditScore()).isEqualTo(100);
            verify(creditLogService).addCreditLog(anyLong(), eq("REGISTER"), eq(100), anyString());
        }

        @Test
        @DisplayName("注册时用户名已存在应抛异常")
        void registerDuplicateUsername() {
            RegisterDTO dto = new RegisterDTO();
            dto.setUsername("existing");
            dto.setPassword("123456");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new User());

            assertThatThrownBy(() -> userService.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名已存在");
        }
    }

    @Nested
    @DisplayName("用户登录")
    class LoginTests {

        @Test
        @DisplayName("登录成功")
        void loginSuccess() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("testuser");
            dto.setPassword("123456");

            User user = buildUser(1L, "testuser", "STUDENT", 1);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("123456", user.getPassword())).thenReturn(true);
            when(jwtUtil.generateToken(1L, "testuser", "STUDENT")).thenReturn("mock-jwt-token");

            String token = userService.login(dto);

            assertThat(token).isEqualTo("mock-jwt-token");
        }

        @Test
        @DisplayName("登录时用户不存在应抛异常")
        void loginUserNotFound() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("nobody");
            dto.setPassword("123456");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        @DisplayName("登录时密码错误应抛异常")
        void loginWrongPassword() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("testuser");
            dto.setPassword("wrong");

            User user = buildUser(1L, "testuser", "STUDENT", 1);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("密码错误");
        }

        @Test
        @DisplayName("登录时账号已禁用应抛异常")
        void loginDisabledAccount() {
            LoginDTO dto = new LoginDTO();
            dto.setUsername("disabled");
            dto.setPassword("123456");

            User user = buildUser(1L, "disabled", "STUDENT", 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("123456", user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号已被禁用");
        }
    }

    @Nested
    @DisplayName("用户信息查询")
    class UserInfoTests {

        @Test
        @DisplayName("根据ID查询用户成功")
        void getUserInfoSuccess() {
            User user = buildUser(1L, "testuser", "STUDENT", 1);
            when(userMapper.selectById(1L)).thenReturn(user);

            UserVO result = userService.getUserInfo(1L);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("查询不存在的用户应抛异常")
        void getUserInfoNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> userService.getUserInfo(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }
    }

    @Nested
    @DisplayName("保证金管理")
    class DepositTests {

        @Test
        @DisplayName("冻结保证金成功")
        void freezeDepositSuccess() {
            User user = buildUserWithDeposit(1L, new BigDecimal("100.00"));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            boolean result = userService.freezeDeposit(1L, new BigDecimal("30.00"));

            assertThat(result).isTrue();
            assertThat(user.getDeposit()).isEqualByComparingTo(new BigDecimal("70.00"));
        }

        @Test
        @DisplayName("冻结保证金余额不足应抛异常")
        void freezeDepositInsufficientBalance() {
            User user = buildUserWithDeposit(1L, new BigDecimal("10.00"));
            when(userMapper.selectById(1L)).thenReturn(user);

            assertThatThrownBy(() -> userService.freezeDeposit(1L, new BigDecimal("50.00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("保证金余额不足");
        }

        @Test
        @DisplayName("解冻保证金成功")
        void unfreezeDepositSuccess() {
            User user = buildUserWithDeposit(1L, new BigDecimal("70.00"));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            boolean result = userService.unfreezeDeposit(1L, new BigDecimal("30.00"));

            assertThat(result).isTrue();
            assertThat(user.getDeposit()).isEqualByComparingTo(new BigDecimal("100.00"));
        }

        @Test
        @DisplayName("扣减保证金成功")
        void deductDepositSuccess() {
            User user = buildUserWithDeposit(1L, new BigDecimal("50.00"));
            when(userMapper.selectById(1L)).thenReturn(user);
            when(userMapper.updateById(any(User.class))).thenReturn(1);

            boolean result = userService.deductDeposit(1L, new BigDecimal("20.00"));

            assertThat(result).isTrue();
            assertThat(user.getDeposit()).isEqualByComparingTo(new BigDecimal("30.00"));
        }

        @Test
        @DisplayName("扣减保证金余额不足应抛异常")
        void deductDepositInsufficientBalance() {
            User user = buildUserWithDeposit(1L, new BigDecimal("5.00"));
            when(userMapper.selectById(1L)).thenReturn(user);

            assertThatThrownBy(() -> userService.deductDeposit(1L, new BigDecimal("10.00")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("保证金余额不足");
        }
    }

    // --- helper methods ---

    private User buildUser(Long id, String username, String role, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("$2a$10$encodedPassword");
        user.setNickname("测试用户");
        user.setPhone("13800138000");
        user.setRole(role);
        user.setStatus(status);
        user.setCreditScore(100);
        user.setDeposit(BigDecimal.ZERO);
        return user;
    }

    private User buildUserWithDeposit(Long id, BigDecimal deposit) {
        User user = buildUser(id, "testuser", "STUDENT", 1);
        user.setDeposit(deposit);
        return user;
    }
}
