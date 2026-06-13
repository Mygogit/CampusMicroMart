package com.campus.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.user.dto.LoginDTO;
import com.campus.user.dto.RegisterDTO;
import com.campus.user.dto.UpdateProfileDTO;
import com.campus.user.entity.User;
import com.campus.user.vo.UserVO;

import java.math.BigDecimal;

public interface UserService extends IService<User> {
    UserVO register(RegisterDTO registerDTO);
    String login(LoginDTO loginDTO);
    UserVO getUserInfo(Long userId);
    UserVO getCurrentUser();
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);
    boolean freezeDeposit(Long userId, BigDecimal amount);
    boolean unfreezeDeposit(Long userId, BigDecimal amount);
    boolean deductDeposit(Long userId, BigDecimal amount);
}
