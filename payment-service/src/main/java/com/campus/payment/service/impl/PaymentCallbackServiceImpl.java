package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.OrderFeignClient;
import com.campus.common.feign.UserFeignClient;
import com.campus.common.result.Result;
import com.campus.payment.constant.PaymentErrorCode;
import com.campus.payment.dto.CallbackRequest;
import com.campus.payment.dto.CallbackResponse;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 支付回调处理服务。
 * <p>
 * 处理第三方支付平台的异步回调，完成 HMAC 签名校验、时间戳防重放、幂等校验、
 * 过期校验与状态转换，并在成功回调时完成扣款与订单状态更新。
 */
@Slf4j
@Service
public class PaymentCallbackServiceImpl {

    /** 回调签名密钥（与"第三方"约定的共享密钥，可通过配置覆盖） */
    @Value("${payment.callback.secret:campus-payment-callback-secret}")
    private String callbackSecret;

    /** 时间戳有效期（毫秒），超时视为重放 */
    private static final long TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private OrderFeignClient orderFeignClient;

    public CallbackResponse handleCallback(CallbackRequest request) {
        // 1. 签名校验（HMAC-SHA256）
        if (!verifySign(request)) {
            log.warn("回调签名校验失败, paymentNo={}", request.getPaymentNo());
            return CallbackResponse.fail("40011", "签名校验失败", request.getPaymentNo());
        }

        // 2. 时间戳防重放
        if (request.getTimestamp() == null
                || Math.abs(System.currentTimeMillis() - request.getTimestamp()) > TIMESTAMP_WINDOW_MS) {
            log.warn("回调时间戳非法或已过期, paymentNo={}, timestamp={}", request.getPaymentNo(), request.getTimestamp());
            return CallbackResponse.fail("40012", "时间戳非法或已过期", request.getPaymentNo());
        }

        // 3. 查找支付记录
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getPaymentNo, request.getPaymentNo()));
        if (payment == null) {
            return CallbackResponse.fail(
                    String.valueOf(PaymentErrorCode.PAYMENT_NOT_FOUND.getCode()),
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage(),
                    request.getPaymentNo());
        }

        // 4. 幂等：已回调过则直接返回成功
        if (payment.getCallbackStatus() != null) {
            log.info("回调已处理（幂等）, paymentNo={}, callbackStatus={}",
                    payment.getPaymentNo(), payment.getCallbackStatus());
            return CallbackResponse.success(payment.getPaymentNo());
        }

        // 5. 过期校验：已过期的支付拒绝成功回调
        if (payment.getExpireTime() != null && LocalDateTime.now().isAfter(payment.getExpireTime())
                && "SUCCESS".equalsIgnoreCase(request.getCallbackStatus())) {
            payment.setStatus(PaymentStatusConstant.EXPIRED.getCode());
            paymentMapper.updateById(payment);
            unfreezeQuietly(payment);
            return CallbackResponse.fail(
                    String.valueOf(PaymentErrorCode.PAYMENT_EXPIRED.getCode()),
                    PaymentErrorCode.PAYMENT_EXPIRED.getMessage(),
                    request.getPaymentNo());
        }

        // 6. 状态机校验
        Integer targetStatus = "SUCCESS".equalsIgnoreCase(request.getCallbackStatus())
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
                    request.getPaymentNo());
        }

        // 7. 原子更新（callbackStatus IS NULL 防并发重复处理）
        LambdaUpdateWrapper<Payment> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Payment::getId, payment.getId())
               .isNull(Payment::getCallbackStatus)
               .set(Payment::getStatus, targetStatus)
               .set(Payment::getCallbackStatus, request.getCallbackStatus().toUpperCase())
               .set(Payment::getCallbackTime, LocalDateTime.now());
        if (request.getFailReason() != null) {
            wrapper.set(Payment::getFailReason, request.getFailReason());
        }
        int rows = paymentMapper.update(null, wrapper);
        if (rows == 0) {
            // 并发下已被其它回调处理
            log.info("回调并发处理（幂等）, paymentNo={}", payment.getPaymentNo());
            return CallbackResponse.success(payment.getPaymentNo());
        }

        // 8. 成功回调：完成扣款 + 订单状态更新
        if (PaymentStatusConstant.SUCCESS.getCode().equals(targetStatus)) {
            settleOnSuccess(payment);
        } else {
            unfreezeQuietly(payment);
        }

        log.info("回调处理成功, paymentNo={}, targetStatus={}", payment.getPaymentNo(), targetStatus);
        return CallbackResponse.success(payment.getPaymentNo());
    }

    /**
     * 成功回调结算：扣减保证金 + 更新订单状态为已支付（失败则补偿退款）。
     */
    private void settleOnSuccess(Payment payment) {
        try {
            Result<Boolean> deductResult = userFeignClient.deductDeposit(payment.getUserId(), payment.getAmount());
            if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                throw new BusinessException("保证金扣减失败");
            }
        } catch (Exception e) {
            log.error("回调扣款失败, paymentNo={}", payment.getPaymentNo(), e);
            return;
        }
        try {
            Result<Boolean> orderResult = orderFeignClient.updateOrderStatus(
                    payment.getOrderId(), OrderStatusConstant.PAID);
            if (orderResult == null || !Integer.valueOf(200).equals(orderResult.getCode())) {
                throw new BusinessException("订单状态更新失败");
            }
        } catch (Exception e) {
            log.error("回调订单状态更新失败，补偿退款, paymentNo={}", payment.getPaymentNo(), e);
            try {
                userFeignClient.creditDeposit(payment.getUserId(), payment.getAmount());
            } catch (Exception refundEx) {
                log.error("补偿退款失败, userId={}", payment.getUserId(), refundEx);
            }
        }
    }

    private void unfreezeQuietly(Payment payment) {
        try {
            Result<Boolean> unfreezeResult = userFeignClient.unfreezeDeposit(payment.getUserId(), payment.getAmount());
            if (unfreezeResult == null || !Integer.valueOf(200).equals(unfreezeResult.getCode())) {
                log.warn("回调解冻失败, userId={}", payment.getUserId());
            }
        } catch (Exception e) {
            log.warn("回调解冻异常, userId={}", payment.getUserId(), e);
        }
    }

    /**
     * HMAC-SHA256 签名校验（恒定时间比较）。
     */
    private boolean verifySign(CallbackRequest request) {
        if (request.getSign() == null || request.getPaymentNo() == null
                || request.getCallbackStatus() == null || request.getTimestamp() == null) {
            return false;
        }
        String raw = request.getPaymentNo() + "|" + request.getCallbackStatus() + "|" + request.getTimestamp();
        String expected = hmacSha256(raw, callbackSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                request.getSign().getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}
