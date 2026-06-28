package com.campus.payment.consumer;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.mq.MqTopicConstant;
import com.campus.payment.config.RetryProperties;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.statemachine.PaymentStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 支付重试消息消费者
 * <p>
 * 消费 payment-retry-topic 延迟消息，执行支付重试逻辑。
 * 重试成功 → SUCCESS；重试失败 → 递增 retryCount 并继续发延迟消息，
 * 直到达到最大重试次数后标记最终失败。
 * <p>
 * RocketMQ 开源版固定 18 级延迟（1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h）
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${payment.retry.topic:" + MqTopicConstant.PAYMENT_RETRY + "}",
        consumerGroup = "${spring.application.name}-retry-consumer"
)
public class PaymentRetryProcessor implements RocketMQListener<String> {

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    @Autowired
    private RetryProperties retryProperties;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Override
    public void onMessage(String message) {
        Long paymentId = Long.parseLong(message);
        log.info("收到重试消息: paymentId={}", paymentId);

        Payment payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            log.warn("支付记录不存在: paymentId={}", paymentId);
            return;
        }

        // 校验当前状态必须为 FAIL
        if (!PaymentStatusConstant.FAIL.getCode().equals(payment.getStatus())) {
            log.info("支付非 FAIL 状态，跳过重试: paymentId={}, status={}",
                    paymentId, payment.getStatus());
            return;
        }

        // 检查重试次数
        int maxRetries = retryProperties.getRetry().getMaxRetries();
        if (payment.getRetryCount() >= maxRetries) {
            log.info("已达最大重试次数，标记为最终失败: paymentId={}, retryCount={}/{}",
                    paymentId, payment.getRetryCount(), maxRetries);
            payment.setFailReason("重试" + maxRetries + "次后仍失败");
            paymentMapper.updateById(payment);
            return;
        }

        try {
            // 状态转换：FAIL → RETRYING
            paymentStateMachine.validateTransition(
                    payment.getStatus(),
                    PaymentStatusConstant.RETRYING.getCode()
            );
            payment.setStatus(PaymentStatusConstant.RETRYING.getCode());
            paymentMapper.updateById(payment);

            // 执行重试支付
            simulateRetry(payment);

        } catch (BusinessException e) {
            log.error("重试状态转换失败: paymentId={}, error={}", paymentId, e.getMessage());
        }
    }

    /**
     * 模拟重试支付
     * <p>
     * 与 PaymentServiceImpl.simulatePayment 共享相同的失败率配置，
     * 成功则标记 SUCCESS，失败则递增 retryCount 并发送下次重试延迟消息
     */
    private void simulateRetry(Payment payment) {
        double failRate = retryProperties.getSimulate().getFailRate();
        boolean success = Math.random() > failRate;

        if (success) {
            // 成功：RETRYING → SUCCESS
            paymentStateMachine.validateTransition(
                    PaymentStatusConstant.RETRYING.getCode(),
                    PaymentStatusConstant.SUCCESS.getCode()
            );
            payment.setStatus(PaymentStatusConstant.SUCCESS.getCode());
            payment.setCallbackStatus("SUCCESS");
            paymentMapper.updateById(payment);

            log.info("重试成功: paymentId={}, paymentNo={}",
                    payment.getId(), payment.getPaymentNo());

            // TODO: 实际扣款 + 通知（与 PaymentServiceImpl.simulatePayment 成功路径一致）

        } else {
            // 失败：RETRYING → FAIL，递增 retryCount，继续发重试消息
            paymentStateMachine.validateTransition(
                    PaymentStatusConstant.RETRYING.getCode(),
                    PaymentStatusConstant.FAIL.getCode()
            );
            payment.setStatus(PaymentStatusConstant.FAIL.getCode());
            payment.setRetryCount(payment.getRetryCount() + 1);
            payment.setFailReason("第" + payment.getRetryCount() + "次重试失败");
            paymentMapper.updateById(payment);

            // 达到最大重试次数则不再发消息
            int maxRetries = retryProperties.getRetry().getMaxRetries();
            if (payment.getRetryCount() >= maxRetries) {
                log.info("已达最大重试次数，停止重试: paymentId={}, retryCount={}",
                        payment.getId(), payment.getRetryCount());
                return;
            }

            // 计算下次重试的延迟级别
            int delayLevel = calcDelayLevel(payment.getRetryCount());

            if (rocketMQTemplate != null) {
                try {
                    rocketMQTemplate.syncSend(
                            MqTopicConstant.PAYMENT_RETRY,
                            MessageBuilder.withPayload(String.valueOf(payment.getId())).build(),
                            3000,
                            delayLevel
                    );
                    log.info("重试失败，已安排下次重试: paymentId={}, retryCount={}/{}, delayLevel={}",
                            payment.getId(), payment.getRetryCount(), maxRetries, delayLevel);
                } catch (Exception e) {
                    log.error("发送重试延迟消息失败: paymentId={}", payment.getId(), e);
                }
            } else {
                log.warn("RocketMQTemplate 不可用，无法发送重试消息: paymentId={}", payment.getId());
            }
        }
    }

    /**
     * 将秒数映射到 RocketMQ 开源版固定 18 级延迟级别
     * <p>
     * 1s/5s/10s/30s/1m/2m/3m/4m/5m/6m/7m/8m/9m/10m/20m/30m/1h/2h
     *
     * @param retryCount 当前重试次数（1-based），用于索引 RetryProperties.intervals
     * @return RocketMQ 延迟级别（1-18）
     */
    private int calcDelayLevel(int retryCount) {
        // 从配置中获取重试间隔（毫秒），按索引取对应值
        java.util.List<Long> intervals = retryProperties.getRetry().getIntervals();
        long targetMs;
        if (retryCount - 1 < intervals.size()) {
            targetMs = intervals.get(retryCount - 1);
        } else {
            // 超出配置索引，使用最后一个值
            targetMs = intervals.get(intervals.size() - 1);
        }

        int seconds = (int) (targetMs / 1000);
        return mapSecondsToDelayLevel(seconds);
    }

    /**
     * 按秒数映射 RocketMQ 开源版延迟级别
     */
    private int mapSecondsToDelayLevel(int seconds) {
        if (seconds <= 1) return 1;
        if (seconds <= 5) return 2;
        if (seconds <= 10) return 3;
        if (seconds <= 30) return 4;
        if (seconds <= 60) return 5;
        if (seconds <= 120) return 6;
        if (seconds <= 180) return 7;
        if (seconds <= 240) return 8;
        if (seconds <= 300) return 9;
        if (seconds <= 360) return 10;
        if (seconds <= 420) return 11;
        if (seconds <= 480) return 12;
        if (seconds <= 540) return 13;
        if (seconds <= 600) return 14;
        if (seconds <= 1200) return 15;
        if (seconds <= 1800) return 16;
        if (seconds <= 3600) return 17;
        return 18; // 最大 2h
    }
}
