package com.campus.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.Result;
import com.campus.common.security.UserContext;
import com.campus.user.dto.LoginDTO;
import com.campus.user.dto.RegisterDTO;
import com.campus.user.dto.UpdateProfileDTO;
import com.campus.user.entity.CreditLog;
import com.campus.user.entity.User;
import com.campus.user.service.CreditLogService;
import com.campus.user.service.UserService;
import com.campus.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;
    @Autowired
    private CreditLogService creditLogService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO registerDTO) {
        return Result.success(userService.register(registerDTO));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(userService.login(loginDTO));
    }

    @Operation(summary = "获取用户信息")
    @GetMapping("/info/{userId}")
    public Result<UserVO> getUserInfo(@PathVariable Long userId) {
        return Result.success(userService.getUserInfo(userId));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/profile")
    public Result<UserVO> getProfile() {
        return Result.success(userService.getCurrentUser());
    }

    @Operation(summary = "更新个人信息")
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestBody UpdateProfileDTO dto) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(userService.updateProfile(userId, dto));
    }

    @Operation(summary = "我的信用记录")
    @GetMapping("/credit/log")
    public Result<List<CreditLog>> getCreditLog() {
        Long userId = UserContext.getCurrentUserId();
        LambdaQueryWrapper<CreditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CreditLog::getUserId, userId)
               .orderByDesc(CreditLog::getCreateTime);
        return Result.success(creditLogService.list(wrapper));
    }

    // ========= Deposit endpoints =========

    @Operation(summary = "冻结保证金")
    @PostMapping("/deposit/freeze")
    public Result<Boolean> freezeDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.freezeDeposit(userId, amount));
    }

    @Operation(summary = "解冻保证金")
    @PostMapping("/deposit/unfreeze")
    public Result<Boolean> unfreezeDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.unfreezeDeposit(userId, amount));
    }

    @Operation(summary = "扣减保证金")
    @PostMapping("/deposit/deduct")
    public Result<Boolean> deductDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.deductDeposit(userId, amount));
    }

    // ========= Admin endpoints =========

    @Operation(summary = "管理员查看用户列表")
    @GetMapping("/admin/users")
    public Result<IPage<User>> listUsers(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                          @RequestParam(name = "size", defaultValue = "10") Integer size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreateTime);
        return Result.success(userService.page(new Page<>(page, size), wrapper));
    }

    @Operation(summary = "管理员禁用/启用用户")
    @PutMapping("/admin/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam(name = "status") Integer status) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setStatus(status);
        userService.updateById(user);
        return Result.success();
    }

    @Operation(summary = "管理员修改用户角色")
    @PutMapping("/admin/users/{id}/role")
    public Result<Void> updateUserRole(@PathVariable Long id, @RequestParam(name = "role") String role) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setRole(role);
        userService.updateById(user);
        return Result.success();
    }
}
