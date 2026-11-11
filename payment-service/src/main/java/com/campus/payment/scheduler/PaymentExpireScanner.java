package com.campus.payment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.OrderFeignClient;
import com.campus.common.feign.UserFeignClient;
import com.campus.common.result.Result;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 过期支付定时扫描器（每 5 分钟，作为 MQ 延迟消息的兜底）。
 * <p>
 * 扫描 WAITING_PAY/PROCESSING 且已过期的支付记录，流转为 EXPIRED，
 * 并解冻买家保证金、取消订单（回滚库存）。
 */
@Slf4j
@Component
public class PaymentExpireScanner {

    private static final String LOCK_KEY = "payment:expire:scan:lock";
    private static final long LOCK_TTL_SECONDS = 120;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "${payment.expire.scheduler-cron:0 */5 * * * ?}")
    public void scanExpiredPayments() {
        if (stringRedisTemplate != null) {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(locked)) {
                log.debug("获取分布式锁失败，跳过本次过期扫描");
                return;
            }
            try {
                doScan();
            } finally {
                stringRedisTemplate.delete(LOCK_KEY);
            }
        } else {
            doScan();
        }
    }

    private void doScan() {
        log.info("开始扫描过期支付...");
        List<Payment> expiredPayments = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                        .in(Payment::getStatus,
                                PaymentStatusConstant.WAITING_PAY.getCode(),
                                PaymentStatusConstant.PROCESSING.getCode())
                        .lt(Payment::getExpireTime, LocalDateTime.now())
        );

        int expiredCount = 0;
        for (Payment payment : expiredPayments) {
            try {
                paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatusConstant.EXPIRED.getCode());
                payment.setStatus(PaymentStatusConstant.EXPIRED.getCode());
                paymentMapper.updateById(payment);
                expiredCount++;

                // 解冻买家保证金
                try {
                    Result<Boolean> unfreezeResult = userFeignClient.unfreezeDeposit(payment.getUserId(), payment.getAmount());
                    if (unfreezeResult == null || !Integer.valueOf(200).equals(unfreezeResult.getCode())) {
                        log.warn("过期解冻保证金失败, paymentNo={}, userId={}", payment.getPaymentNo(), payment.getUserId());
                    }
                } catch (Exception e) {
                    log.warn("过期解冻保证金异常, paymentNo={}", payment.getPaymentNo(), e);
                }

                // 取消订单（回滚库存）
                try {
                    Result<Boolean> cancelResult = orderFeignClient.cancelOrder(payment.getOrderId());
                    if (cancelResult == null || !Integer.valueOf(200).equals(cancelResult.getCode())) {
                        log.warn("过期取消订单失败, paymentNo={}, orderId={}", payment.getPaymentNo(), payment.getOrderId());
                    }
                } catch (Exception e) {
                    log.warn("过期取消订单异常, paymentNo={}", payment.getPaymentNo(), e);
                }

                log.info("支付已过期, paymentNo={}, orderId={}", payment.getPaymentNo(), payment.getOrderId());
            } catch (BusinessException e) {
                log.warn("支付状态转换失败（可能已被处理）, paymentNo={}, error={}", payment.getPaymentNo(), e.getMessage());
            }
        }
        log.info("过期扫描完成，扫描 {} 条，实际过期 {} 条", expiredPayments.size(), expiredCount);
    }
}
