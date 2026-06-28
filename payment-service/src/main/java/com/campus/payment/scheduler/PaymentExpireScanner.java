package com.campus.payment.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
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
 * 过期支付定时扫描器
 * <p>
 * 按 cron 表达式定时扫描 WAITING_PAY 和 PROCESSING 状态且已过期的支付记录，
 * 将其状态流转为 EXPIRED。使用 Redis 分布式锁防止多实例重复执行。
 */
@Slf4j
@Component
public class PaymentExpireScanner {

    /** 分布式锁 key */
    private static final String LOCK_KEY = "payment:expire:scan:lock";
    /** 锁超时时间（秒） */
    private static final long LOCK_TTL_SECONDS = 120;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 定时扫描过期支付
     * <p>
     * cron 表达式从 payment.expire.scheduler-cron 读取，默认每 5 分钟执行一次
     */
    @Scheduled(cron = "${payment.expire.scheduler-cron:0 */5 * * * ?}")
    public void scanExpiredPayments() {
        // 分布式锁：防止多实例重复执行
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
            // 无 Redis 时直接扫描（单实例场景或开发环境）
            // TODO: 生产环境需加分布式锁
            doScan();
        }
    }

    private void doScan() {
        log.info("开始扫描过期支付...");

        // 查询 WAITING_PAY 或 PROCESSING 状态且已过期的支付
        List<Payment> expiredPayments = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>()
                        .in(Payment::getStatus,
                                PaymentStatusConstant.WAITING_PAY.getCode(),
                                PaymentStatusConstant.PROCESSING.getCode())
                        .lt(Payment::getExpireTime, LocalDateTime.now())
        );

        int expiredCount = 0;
        for (Payment payment : expiredPayments) {
            Integer oldStatus = payment.getStatus();
            try {
                // 校验状态转换合法性
                paymentStateMachine.validateTransition(
                        payment.getStatus(),
                        PaymentStatusConstant.EXPIRED.getCode()
                );

                // 更新为 EXPIRED
                payment.setStatus(PaymentStatusConstant.EXPIRED.getCode());
                paymentMapper.updateById(payment);

                expiredCount++;
                log.info("支付已过期, paymentNo={}, 原状态={}, expireTime={}",
                        payment.getPaymentNo(),
                        PaymentStatusConstant.getByCode(oldStatus) != null
                                ? PaymentStatusConstant.getByCode(oldStatus).getDescription()
                                : oldStatus,
                        payment.getExpireTime());

            } catch (BusinessException e) {
                log.warn("支付状态转换失败（可能已被处理）, paymentNo={}, oldStatus={}, error={}",
                        payment.getPaymentNo(), oldStatus, e.getMessage());
            }
        }

        log.info("过期扫描完成，扫描 {} 条，实际过期 {} 条", expiredPayments.size(), expiredCount);
    }
}
