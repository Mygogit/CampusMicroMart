package com.campus.payment.mq;

import com.campus.common.mq.MqTopicConstant;
import com.campus.common.mq.OrderStatusMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = MqTopicConstant.ORDER_STATUS_CHANGE, consumerGroup = "payment-order-status-consumer")
public class OrderStatusConsumer implements RocketMQListener<OrderStatusMessage> {

    @Override
    public void onMessage(OrderStatusMessage message) {
        log.info("收到订单状态变更消息: orderId={}, orderNo={}, oldStatus={}, newStatus={}",
                message.getOrderId(), message.getOrderNo(), message.getOldStatus(), message.getNewStatus());
    }
}
