package com.campus.user.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.user.entity.User;
import com.campus.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 数据初始化器：启动时自动创建管理员账号
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 检查管理员账号是否存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, "admin");
        User adminUser = userMapper.selectOne(wrapper);

        if (adminUser == null) {
            // 创建管理员账号
            User user = new User();
            user.setUsername("admin");
            user.setPassword(passwordEncoder.encode("admin123"));
            user.setNickname("系统管理员");
            user.setPhone("13800138000");
            user.setEmail("admin@campus.edu");
            user.setStatus(1);
            user.setRole("ADMIN");
            user.setCreditScore(100);
            user.setDeposit(BigDecimal.ZERO);
            userMapper.insert(user);
            log.info("========================================");
            log.info("管理员账号创建成功！");
            log.info("用户名: admin");
            log.info("密码: admin123");
            log.info("========================================");
        } else {
            // 确保管理员角色正确
            if (!"ADMIN".equals(adminUser.getRole())) {
                adminUser.setRole("ADMIN");
                userMapper.updateById(adminUser);
                log.info("管理员角色已更新！");
            }
            log.info("管理员账号已存在！");
            log.info("用户名: admin");
            log.info("默认密码: admin123 (如已修改请使用您的密码)");
        }
    }
}
