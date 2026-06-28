package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.payment.constant.PaymentErrorCode;
import com.campus.payment.dto.CallbackRequest;
import com.campus.payment.dto.CallbackResponse;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 支付回调处理服务
 * <p>
 * 处理第三方支付平台的异步回调通知，完成签名校验、幂等校验、状态转换
 */
@Slf4j
@Service
public class PaymentCallbackServiceImpl {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    /**
     * 处理支付回调
     *
     * @param request 回调请求
     * @return 回调响应（成功或失败）
     */
    public CallbackResponse handleCallback(CallbackRequest request) {
        // 1. 签名校验（P2 占位：非 "mock_sign" 则返回失败）
        if (!"mock_sign".equals(request.getSign())) {
            log.warn("回调签名校验失败, paymentNo={}, sign={}", request.getPaymentNo(), request.getSign());
            return CallbackResponse.fail("40011", "签名校验失败", request.getPaymentNo());
        }

        // 2. 根据 paymentNo 查找支付记录
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPaymentNo, request.getPaymentNo())
        );
        if (payment == null) {
            log.warn("回调支付记录不存在, paymentNo={}", request.getPaymentNo());
            return CallbackResponse.fail(
                    String.valueOf(PaymentErrorCode.PAYMENT_NOT_FOUND.getCode()),
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage(),
                    request.getPaymentNo()
            );
        }

        // 3. 幂等校验：已回调过则直接返回成功
        if (payment.getCallbackStatus() != null) {
            log.info("回调已处理（幂等）, paymentNo={}, callbackStatus={}",
                    payment.getPaymentNo(), payment.getCallbackStatus());
            return CallbackResponse.success(payment.getPaymentNo());
        }

        // 4. 状态转换校验（当前状态 → 回调目标状态）
        Integer targetStatus = "SUCCESS".equals(request.getCallbackStatus())
                ? PaymentStatusConstant.SUCCESS.getCode()
                : PaymentStatusConstant.FAIL.getCode();
        try {
            paymentStateMachine.validateTransition(payment.getStatus(), targetStatus);
        } catch (BusinessException e) {
            log.warn("回调状态转换非法, paymentNo={}, from={}, to={}",
                    payment.getPaymentNo(), payment.getStatus(), targetStatus);
            return CallbackResponse.fail(
                    String.valueOf(PaymentErrorCode.STATE_TRANSITION_INVALID.getCode()),
                    PaymentErrorCode.STATE_TRANSITION_INVALID.getMessage() + ": " + e.getMessage(),
                    request.getPaymentNo()
            );
        }

        // 5. 更新支付状态
        payment.setStatus(targetStatus);
        payment.setCallbackStatus(request.getCallbackStatus());
        payment.setCallbackTime(LocalDateTime.now());
        if (request.getFailReason() != null) {
            payment.setFailReason(request.getFailReason());
        }
        paymentMapper.updateById(payment);

        log.info("回调处理成功, paymentNo={}, targetStatus={}, callbackStatus={}",
                payment.getPaymentNo(), targetStatus, request.getCallbackStatus());
        return CallbackResponse.success(payment.getPaymentNo());
    }
}
