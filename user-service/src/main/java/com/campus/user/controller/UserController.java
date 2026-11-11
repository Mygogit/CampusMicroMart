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

    @Operation(summary = "获取用户信息（仅本人或管理员）")
    @GetMapping("/info/{userId}")
    public Result<UserVO> getUserInfo(@PathVariable Long userId) {
        Long currentUserId = UserContext.getCurrentUserId();
        if (currentUserId == null) {
            return Result.error(401, "请先登录");
        }
        if (!currentUserId.equals(userId)) {
            User current = userService.getById(currentUserId);
            if (current == null || !"ADMIN".equals(current.getRole())) {
                return Result.error(403, "无权查看他人信息");
            }
        }
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
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
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

    @Operation(summary = "保证金充值（模拟）")
    @PostMapping("/deposit/recharge")
    public Result<Boolean> recharge(@RequestParam("amount") BigDecimal amount) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            return Result.error(401, "请先登录");
        }
        return Result.success(userService.recharge(userId, amount));
    }

    @Operation(summary = "冻结保证金（内部调用）")
    @PostMapping("/deposit/freeze")
    public Result<Boolean> freezeDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.freezeDeposit(userId, amount));
    }

    @Operation(summary = "解冻保证金（内部调用）")
    @PostMapping("/deposit/unfreeze")
    public Result<Boolean> unfreezeDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.unfreezeDeposit(userId, amount));
    }

    @Operation(summary = "扣减保证金（内部调用）")
    @PostMapping("/deposit/deduct")
    public Result<Boolean> deductDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.deductDeposit(userId, amount));
    }

    @Operation(summary = "结算入账（内部调用，给卖家）")
    @PostMapping("/deposit/credit")
    public Result<Boolean> creditDeposit(@RequestParam(name = "userId") Long userId, @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.creditDeposit(userId, amount));
    }

    // ========= Admin endpoints =========

    @Operation(summary = "管理员给任意用户充值保证金")
    @PostMapping("/admin/deposit/recharge")
    public Result<Boolean> adminRecharge(@RequestParam(name = "userId") Long userId,
                                         @RequestParam(name = "amount") BigDecimal amount) {
        return Result.success(userService.recharge(userId, amount));
    }

    @Operation(summary = "管理员查看用户列表")
    @GetMapping("/admin/users")
    public Result<IPage<UserVO>> listUsers(@RequestParam(name = "page", defaultValue = "1") Integer page,
                                            @RequestParam(name = "size", defaultValue = "10") Integer size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreateTime);
        IPage<User> userPage = userService.page(new Page<>(page, size), wrapper);
        return Result.success(userPage.convert(userService::toVO));
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
