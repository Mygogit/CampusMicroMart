package com.campus.payment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.result.Result;
import com.campus.payment.dto.CreatePaymentDTO;
import com.campus.payment.entity.Payment;
import com.campus.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支付管理")
@RestController
@RequestMapping("/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Operation(summary = "获取支付记录列表")
    @GetMapping("/list")
    public Result<IPage<Payment>> list(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(paymentService.page(new Page<>(page, size)));
    }

    @Operation(summary = "获取支付详情")
    @GetMapping("/{id}")
    public Result<Payment> getById(@PathVariable Long id) {
        return Result.success(paymentService.getById(id));
    }

    @Operation(summary = "创建支付")
    @PostMapping
    public Result<Payment> createPayment(@Valid @RequestBody CreatePaymentDTO createPaymentDTO) {
        return Result.success(paymentService.createPayment(createPaymentDTO));
    }

    @Operation(summary = "模拟支付")
    @PostMapping("/simulate")
    public Result<Boolean> simulatePayment(@RequestParam Long paymentId) {
        return Result.success(paymentService.simulatePayment(paymentId));
    }
}

