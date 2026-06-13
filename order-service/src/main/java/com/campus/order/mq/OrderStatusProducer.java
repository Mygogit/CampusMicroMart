package com.campus.order.mq;

import com.campus.common.mq.MqTopicConstant;
import com.campus.common.mq.OrderStatusMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(RocketMQTemplate.class)
public class OrderStatusProducer {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public void sendOrderStatusChange(OrderStatusMessage message) {
        rocketMQTemplate.syncSend(MqTopicConstant.ORDER_STATUS_CHANGE, message);
        log.info("订单状态变更消息已发送, orderId={}, oldStatus={}, newStatus={}",
                message.getOrderId(), message.getOldStatus(), message.getNewStatus());
    }
}
