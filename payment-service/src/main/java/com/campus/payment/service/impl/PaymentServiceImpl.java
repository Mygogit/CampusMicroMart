package com.campus.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.constant.PaymentStatusConstant;
import com.campus.common.exception.BusinessException;
import com.campus.common.feign.OrderFeignClient;
import com.campus.common.feign.UserFeignClient;
import com.campus.common.result.Result;
import com.campus.common.mq.PaymentNotificationMessage;
import com.campus.payment.dto.CreatePaymentDTO;
import com.campus.payment.entity.Payment;
import com.campus.payment.mapper.PaymentMapper;
import com.campus.payment.mq.PaymentNotificationProducer;
import com.campus.payment.service.PaymentService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.observation.annotation.Observed;

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

    @Override
    @Transactional
    public Payment createPayment(CreatePaymentDTO createPaymentDTO) {
        Result<Boolean> existsResult = orderFeignClient.exists(createPaymentDTO.getOrderId());
        if (existsResult == null || !Integer.valueOf(200).equals(existsResult.getCode())
                || !Boolean.TRUE.equals(existsResult.getData())) {
            throw new BusinessException("订单不存在或状态异常");
        }

        Payment payment = new Payment();
        payment.setPaymentNo(IdUtil.simpleUUID());
        payment.setUserId(createPaymentDTO.getUserId());
        payment.setOrderId(createPaymentDTO.getOrderId());
        payment.setOrderNo(createPaymentDTO.getOrderNo());
        payment.setAmount(createPaymentDTO.getAmount());
        payment.setPaymentMethod(createPaymentDTO.getPaymentMethod());
        payment.setStatus(PaymentStatusConstant.WAITING_PAY);
        save(payment);
        log.info("支付记录创建成功, paymentId={}, paymentNo={}", payment.getId(), payment.getPaymentNo());
        return payment;
    }

    @Override
    @GlobalTransactional(name = "simulate-payment", rollbackFor = Exception.class)
    @Transactional
    public boolean simulatePayment(Long paymentId) {
        Payment payment = getById(paymentId);
        if (payment == null) {
            throw new BusinessException("支付记录不存在");
        }
        if (!payment.getStatus().equals(PaymentStatusConstant.WAITING_PAY)) {
            throw new BusinessException("该支付已处理，不允许重复操作");
        }

        // 冻结保证金
        try {
            Result<Boolean> freezeResult = userFeignClient.freezeDeposit(payment.getUserId(), payment.getAmount());
            if (freezeResult == null || !Integer.valueOf(200).equals(freezeResult.getCode())) {
                throw new BusinessException("保证金冻结失败");
            }
        } catch (BusinessException e) { throw e; } catch (Exception e) {
            log.error("保证金冻结异常, userId={}", payment.getUserId(), e);
            throw new BusinessException("保证金冻结失败: " + e.getMessage());
        }
        log.info("保证金冻结成功, userId={}, amount={}", payment.getUserId(), payment.getAmount());

        boolean success = Math.random() > 0.1;
        if (success) {
            payment.setStatus(PaymentStatusConstant.SUCCESS);
            payment.setThirdPartyNo(IdUtil.simpleUUID());
            updateById(payment);

            // 扣减保证金
            try {
                Result<Boolean> deductResult = userFeignClient.deductDeposit(payment.getUserId(), payment.getAmount());
                if (deductResult == null || !Integer.valueOf(200).equals(deductResult.getCode())) {
                    throw new BusinessException("保证金扣减失败");
                }
            } catch (BusinessException e) { throw e; } catch (Exception e) {
                log.error("保证金扣减异常, userId={}", payment.getUserId(), e);
                throw new BusinessException("保证金扣减失败: " + e.getMessage());
            }
            log.info("保证金扣减成功, userId={}, amount={}", payment.getUserId(), payment.getAmount());

            Result<Boolean> result = orderFeignClient.updateOrderStatus(payment.getOrderId(), OrderStatusConstant.PAID);
            if (result == null || !Integer.valueOf(200).equals(result.getCode())) {
                log.error("更新订单状态失败, paymentId={}, orderId={}, result={}", paymentId, payment.getOrderId(), result);
                throw new BusinessException("支付成功但订单状态更新失败，请联系客服");
            }
            log.info("支付成功, paymentId={}, orderId={}", paymentId, payment.getOrderId());
            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), true,
                        payment.getThirdPartyNo(), LocalDateTime.now()));
            }
        } else {
            payment.setStatus(PaymentStatusConstant.FAIL);
            updateById(payment);

            // 解冻保证金
            try {
                Result<Boolean> unfreezeResult = userFeignClient.unfreezeDeposit(payment.getUserId(), payment.getAmount());
                if (unfreezeResult == null || !Integer.valueOf(200).equals(unfreezeResult.getCode())) {
                    log.error("保证金解冻失败, userId={}", payment.getUserId());
                }
            } catch (Exception e) {
                log.error("保证金解冻异常, userId={}", payment.getUserId(), e);
            }
            log.info("保证金解冻成功, userId={}, amount={}", payment.getUserId(), payment.getAmount());

            if (paymentNotificationProducer != null) {
                paymentNotificationProducer.sendPaymentNotification(new PaymentNotificationMessage(
                        payment.getId(), payment.getPaymentNo(), payment.getOrderId(),
                        payment.getOrderNo(), payment.getAmount(), false,
                        null, LocalDateTime.now()));
            }
            log.info("支付失败, paymentId={}", paymentId);
        }
        return success;
    }
}