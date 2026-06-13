package com.campus.order.mq;

import com.campus.common.constant.OrderStatusConstant;
import com.campus.common.mq.MqTopicConstant;
import com.campus.common.mq.PaymentNotificationMessage;
import com.campus.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = MqTopicConstant.PAYMENT_NOTIFICATION, consumerGroup = "order-payment-notification-consumer")
public class PaymentNotificationConsumer implements RocketMQListener<PaymentNotificationMessage> {

    @Autowired
    private OrderService orderService;

    @Override
    public void onMessage(PaymentNotificationMessage message) {
        log.info("收到支付通知消息: paymentId={}, orderId={}, success={}",
                message.getPaymentId(), message.getOrderId(), message.getSuccess());
        if (Boolean.TRUE.equals(message.getSuccess())) {
            try {
                orderService.updateOrderStatus(message.getOrderId(), OrderStatusConstant.PAID);
                log.info("支付回调更新订单状态成功, orderId={}", message.getOrderId());
            } catch (Exception e) {
                log.error("支付回调更新订单状态失败, orderId={}", message.getOrderId(), e);
            }
        }
    }
}
