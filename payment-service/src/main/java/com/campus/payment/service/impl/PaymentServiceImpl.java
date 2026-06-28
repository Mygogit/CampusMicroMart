package com.campus.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.OrderFeignClient;
import com.campus.common.feign.UserFeignClient;
import com.campus.common.result.Result;
import com.campus.common.mq.PaymentNotificationMessage;
import com.campus.payment.config.RetryProperties;
import com.campus.payment.constant.PaymentErrorCode;
import com.campus.payment.dto.CreatePaymentDTO;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.mq.PaymentNotificationProducer;
import com.campus.payment.service.PaymentService;
import com.campus.payment.statemachine.PaymentStateMachine;
import com.campus.payment.vo.PaymentVO;
import io.micrometer.observation.annotation.Observed;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 支付服务实现
 * <p>
 * 提供支付创建、模拟支付、按订单查询等核心支付功能
 */
@Slf4j
@Observed
@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired(required = false)
    private PaymentNotificationProducer paymentNotificationProducer;

    @Autowired
    private PaymentStateMachine paymentStateMachine;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private RetryProperties retryProperties;

    @Override
    @Transactional
    public Payment createPayment(CreatePaymentDTO createPaymentDTO) {
        // 1. 校验订单是否存在
        Result<Boolean> existsResult = orderFeignClient.exists(createPaymentDTO.getOrderId());
        if (existsResult == null || !Integer.valueOf(200).equals(existsResult.getCode())
                || !Boolean.TRUE.equals(existsResult.getData())) {
            throw new BusinessException(
                    PaymentErrorCode.ORDER_NOT_EXIST.getCode(),
                    PaymentErrorCode.ORDER_NOT_EXIST.getMessage()
            );
        }

        // 2. 构建 Payment 实体
        Payment payment = new Payment();
        payment.setPaymentNo(IdUtil.simpleUUID());
        payment.setUserId(createPaymentDTO.getUserId());
        payment.setOrderId(createPaymentDTO.getOrderId());
        payment.setOrderNo(createPaymentDTO.getOrderNo());
        payment.setAmount(createPaymentDTO.getAmount());
        payment.setPaymentMethod(createPaymentDTO.getPaymentMethod() != null
                ? createPaymentDTO.getPaymentMethod() : "SIMULATE");
        payment.setStatus(PaymentStatusConstant.WAITING_PAY.getCode());
        payment.setRetryCount(0);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(retryProperties.getExpire().getMinutes()));
        save(payment);

        // 3. 发送 RocketMQ 延迟消息（超时取消用）
        if (rocketMQTemplate != null) {
            try {
                rocketMQTemplate.syncSend("payment-expire-topic",
                        MessageBuilder.withPayload(payment.getId()).build(), 3000, 14);
                log.info("支付过期延迟消息已发送, paymentId={}", payment.getId());
            } catch (Exception e) {
                log.error("发送支付过期延迟消息失败, paymentId={}", payment.getId(), e);
            }
        }

        log.info("支付记录创建成功, paymentId={}, paymentNo={}", payment.getId(), payment.getPaymentNo());
        return payment;
    }

    @Override
    @GlobalTransactional(name = "simulate-payment", rollbackFor = Exception.class)
    @Transactional
    public PaymentVO simulatePayment(Long paymentId) {
        Payment payment = getById(paymentId);
        if (payment == null) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getCode(),
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()
            );
        }

        // 状态校验：当前状态 → PROCESSING
        paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatusConstant.PROCESSING.getCode());

        // 更新为处理中
        payment.setStatus(PaymentStatusConstant.PROCESSING.getCode());
        updateById(payment);

        // 使用配置的模拟失败率判断结果
        double failRate = retryProperties.getSimulate().getFailRate();
        boolean success = Math.random() > failRate;

        if (success) {
            // ===== 成功路径 =====
            paymentStateMachine.validateTransition(
                    payment.getStatus(), PaymentStatusConstant.SUCCESS.getCode());

            payment.setStatus(PaymentStatusConstant.SUCCESS.getCode());
            payment.setCallbackStatus("SUCCESS");
            payment.setThirdPartyNo(IdUtil.simpleUUID());
            updateById(payment);

            // 扣减保证金
            try {
                Result<Boolean> deductResult = userFeignClient.deductDeposit(
                        payment.getUserId(), payment.getAmount());
                if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                    throw new BusinessException("保证金扣减失败");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("保证金扣减异常, userId={}", payment.getUserId(), e);
                throw new BusinessException("保证金扣减失败: " + e.getMessage());
            }
            log.info("保证金扣减成功, userId={}, amount={}", payment.getUserId(), payment.getAmount());

            // 更新订单状态为已支付
            Result<Boolean> result = orderFeignClient.updateOrderStatus(
                    payment.getOrderId(), OrderStatusConstant.PAID);
            if (result == null || !Integer.valueOf(200).equals(result.getCode())) {
                log.error("更新订单状态失败, paymentId={}, orderId={}, result={}",
                        paymentId, payment.getOrderId(), result);
                throw new BusinessException("支付成功但订单状态更新失败，请联系客服");
            }

            // 发送支付成功通知
            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), true,
                        payment.getThirdPartyNo(), LocalDateTime.now()));
            }
            log.info("支付成功, paymentId={}, orderId={}", paymentId, payment.getOrderId());

        } else {
            // ===== 失败路径 =====
            paymentStateMachine.validateTransition(
                    payment.getStatus(), PaymentStatusConstant.FAIL.getCode());

            payment.setStatus(PaymentStatusConstant.FAIL.getCode());
            payment.setFailReason("模拟支付失败");
            updateById(payment);

            // 发送重试延迟消息
            if (rocketMQTemplate != null) {
                try {
                    rocketMQTemplate.syncSend("payment-retry-topic",
                            MessageBuilder.withPayload(payment.getId()).build(), 3000, 2);
                    log.info("支付重试延迟消息已发送, paymentId={}", payment.getId());
                } catch (Exception e) {
                    log.error("发送支付重试延迟消息失败, paymentId={}", payment.getId(), e);
                }
            }

            // 解冻保证金
            try {
                Result<Boolean> unfreezeResult = userFeignClient.unfreezeDeposit(
                        payment.getUserId(), payment.getAmount());
                if (unfreezeResult == null || !Integer.valueOf(200).equals(unfreezeResult.getCode())) {
                    log.error("保证金解冻失败, userId={}", payment.getUserId());
                }
            } catch (Exception e) {
                log.error("保证金解冻异常, userId={}", payment.getUserId(), e);
            }
            log.info("保证金解冻成功, userId={}, amount={}", payment.getUserId(), payment.getAmount());

            // 发送支付失败通知
            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), false,
                        null, LocalDateTime.now()));
            }
            log.info("支付失败, paymentId={}", paymentId);
        }

        // 重新查询最新状态并返回 PaymentVO
        return PaymentVO.from(getById(paymentId));
    }

    @Override
    public PaymentVO queryByOrderId(Long orderId) {
        Payment payment = getBaseMapper().selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .orderByDesc(Payment::getCreateTime)
                        .last("LIMIT 1")
        );
        if (payment == null) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getCode(),
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()
            );
        }
        return PaymentVO.from(payment);
    }
}
