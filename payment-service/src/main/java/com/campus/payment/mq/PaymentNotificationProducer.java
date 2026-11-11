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
        try {
            rocketMQTemplate.syncSend(MqTopicConstant.PAYMENT_NOTIFICATION, message);
            log.info("支付通知消息已发送, paymentId={}, orderId={}, success={}",
                    message.getPaymentId(), message.getOrderId(), message.getSuccess());
        } catch (Exception e) {
            // MQ 不可用时降级：支付结果已落库，订单侧可通过查询接口补偿，不阻塞主流程
            log.error("支付通知消息发送失败（已降级，不影响主流程）, paymentId={}, orderId={}, success={}",
                    message.getPaymentId(), message.getOrderId(), message.getSuccess(), e);
        }
    }
}
