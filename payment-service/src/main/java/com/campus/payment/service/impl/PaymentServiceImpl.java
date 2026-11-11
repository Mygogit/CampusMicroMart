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
import com.campus.common.security.UserContext;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        Long buyerId = UserContext.getCurrentUserId();
        if (buyerId == null) {
            throw new BusinessException("请先登录");
        }

        // 1. 校验订单存在
        Result<Boolean> existsResult = orderFeignClient.exists(createPaymentDTO.getOrderId());
        if (existsResult == null || !Integer.valueOf(200).equals(existsResult.getCode())
                || !Boolean.TRUE.equals(existsResult.getData())) {
            throw new BusinessException(
                    PaymentErrorCode.ORDER_NOT_EXIST.getCode(),
                    PaymentErrorCode.ORDER_NOT_EXIST.getMessage());
        }

        // 2. 服务端金额校验：支付金额必须与订单应付金额一致
        Result<BigDecimal> amountResult = orderFeignClient.getAmount(createPaymentDTO.getOrderId());
        if (amountResult == null || !Integer.valueOf(200).equals(amountResult.getCode())
                || amountResult.getData() == null) {
            throw new BusinessException(
                    PaymentErrorCode.ORDER_NOT_EXIST.getCode(),
                    PaymentErrorCode.ORDER_NOT_EXIST.getMessage());
        }
        BigDecimal orderAmount = amountResult.getData();
        if (createPaymentDTO.getAmount() == null
                || createPaymentDTO.getAmount().compareTo(orderAmount) != 0) {
            throw new BusinessException(
                    PaymentErrorCode.AMOUNT_MISMATCH.getCode(),
                    PaymentErrorCode.AMOUNT_MISMATCH.getMessage());
        }

        // 3. 冻结买家保证金（可用余额 → 冻结余额）
        try {
            Result<Boolean> freezeResult = userFeignClient.freezeDeposit(buyerId, orderAmount);
            if (freezeResult == null || !Integer.valueOf(200).equals(freezeResult.getCode())) {
                throw new BusinessException("保证金冻结失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("保证金冻结异常, userId={}", buyerId, e);
            throw new BusinessException("保证金冻结失败");
        }

        // 4. 构建并保存支付记录（保存失败则补偿解冻）
        Payment payment = new Payment();
        payment.setPaymentNo(IdUtil.simpleUUID());
        payment.setUserId(buyerId);
        payment.setOrderId(createPaymentDTO.getOrderId());
        payment.setOrderNo(createPaymentDTO.getOrderNo());
        payment.setAmount(orderAmount);
        payment.setPaymentMethod(createPaymentDTO.getPaymentMethod() != null
                ? createPaymentDTO.getPaymentMethod() : "SIMULATE");
        payment.setStatus(PaymentStatusConstant.WAITING_PAY.getCode());
        payment.setRetryCount(0);
        payment.setExpireTime(LocalDateTime.now().plusMinutes(retryProperties.getExpire().getMinutes()));
        try {
            save(payment);
        } catch (Exception e) {
            log.error("支付记录保存失败，补偿解冻, userId={}, amount={}", buyerId, orderAmount, e);
            try {
                userFeignClient.unfreezeDeposit(buyerId, orderAmount);
            } catch (Exception ex) {
                log.error("补偿解冻失败, userId={}", buyerId, ex);
            }
            throw e;
        }

        // 5. 发送 RocketMQ 延迟消息（超时取消用，未接入 MQ 时由定时扫表兜底）
        if (rocketMQTemplate != null) {
            try {
                rocketMQTemplate.syncSend("payment-expire-topic",
                        MessageBuilder.withPayload(payment.getId()).build(), 3000, 14);
                log.info("支付过期延迟消息已发送, paymentId={}", payment.getId());
            } catch (Exception e) {
                log.error("发送支付过期延迟消息失败, paymentId={}", payment.getId(), e);
            }
        }

        log.info("支付记录创建成功并冻结保证金, paymentId={}, paymentNo={}, amount={}",
                payment.getId(), payment.getPaymentNo(), orderAmount);
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
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage());
        }

        // 过期校验：过期未扫表的支付不允许再支付
        if (payment.getExpireTime() != null && LocalDateTime.now().isAfter(payment.getExpireTime())) {
            throw new BusinessException(
                    PaymentErrorCode.PAYMENT_EXPIRED.getCode(),
                    PaymentErrorCode.PAYMENT_EXPIRED.getMessage());
        }

        // 状态校验：当前状态 → PROCESSING
        paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatusConstant.PROCESSING.getCode());
        payment.setStatus(PaymentStatusConstant.PROCESSING.getCode());
        updateById(payment);

        double failRate = retryProperties.getSimulate().getFailRate();
        boolean success = Math.random() > failRate;

        if (success) {
            // ===== 成功路径 =====
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatusConstant.SUCCESS.getCode());

            // 1. 扣减保证金（从冻结余额扣除）
            try {
                Result<Boolean> deductResult = userFeignClient.deductDeposit(payment.getUserId(), payment.getAmount());
                if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                    throw new BusinessException("保证金扣减失败");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("保证金扣减异常, userId={}", payment.getUserId(), e);
                throw new BusinessException("保证金扣减失败");
            }

            // 2. 更新订单状态为已支付；失败则补偿退款并抛异常
            Result<Boolean> result = orderFeignClient.updateOrderStatus(payment.getOrderId(), OrderStatusConstant.PAID);
            if (result == null || !Integer.valueOf(200).equals(result.getCode())) {
                log.error("更新订单状态失败, paymentId={}, orderId={}, result={}",
                        paymentId, payment.getOrderId(), result);
                try {
                    userFeignClient.creditDeposit(payment.getUserId(), payment.getAmount());
                } catch (Exception refundEx) {
                    log.error("补偿退款失败, userId={}, amount={}", payment.getUserId(), payment.getAmount(), refundEx);
                }
                throw new BusinessException("支付成功但订单状态更新失败，请稍后重试");
            }

            // 3. 标记支付成功
            payment.setStatus(PaymentStatusConstant.SUCCESS.getCode());
            payment.setCallbackStatus("SUCCESS");
            payment.setThirdPartyNo(IdUtil.simpleUUID());
            updateById(payment);

            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), true,
                        payment.getThirdPartyNo(), LocalDateTime.now()));
            }
            log.info("支付成功, paymentId={}, orderId={}", paymentId, payment.getOrderId());

        } else {
            // ===== 失败路径 =====
            paymentStateMachine.validateTransition(payment.getStatus(), PaymentStatusConstant.FAIL.getCode());

            payment.setStatus(PaymentStatusConstant.FAIL.getCode());
            payment.setFailReason("模拟支付失败");
            updateById(payment);

            if (rocketMQTemplate != null) {
                try {
                    rocketMQTemplate.syncSend("payment-retry-topic",
                            MessageBuilder.withPayload(payment.getId()).build(), 3000, 2);
                    log.info("支付重试延迟消息已发送, paymentId={}", payment.getId());
                } catch (Exception e) {
                    log.error("发送支付重试延迟消息失败, paymentId={}", payment.getId(), e);
                }
            }

            // 解冻保证金（冻结余额 → 可用余额）
            try {
                Result<Boolean> unfreezeResult = userFeignClient.unfreezeDeposit(payment.getUserId(), payment.getAmount());
                if (unfreezeResult == null || !Integer.valueOf(200).equals(unfreezeResult.getCode())) {
                    log.error("保证金解冻失败, userId={}", payment.getUserId());
                }
            } catch (Exception e) {
                log.error("保证金解冻异常, userId={}", payment.getUserId(), e);
            }

            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), false,
                        null, LocalDateTime.now()));
            }
            log.info("支付失败, paymentId={}", paymentId);
        }

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
                    PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage());
        }
        return PaymentVO.from(payment);
    }
}
