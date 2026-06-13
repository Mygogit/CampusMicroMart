package com.campus.payment.mq;

import com.campus.common.mq.MqTopicConstant;
import com.campus.common.mq.PaymentNotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class PaymentNotificationProducer {

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    public void sendPaymentNotification(PaymentNotificationMessage message) {
        rocketMQTemplate.syncSend(MqTopicConstant.PAYMENT_NOTIFICATION, message);
        log.info("支付通知消息已发送, paymentId={}, orderId={}, success={}",
                message.getPaymentId(), message.getOrderId(), message.getSuccess());
    }
}
