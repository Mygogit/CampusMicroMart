package com.campus.common.mq;

public interface MqTopicConstant {

    String ORDER_STATUS_CHANGE = "order-status-change";
    String STOCK_ALERT = "stock-alert";
    String PAYMENT_NOTIFICATION = "payment-notification";
    String PAYMENT_EXPIRE = "payment-expire-topic";
    String PAYMENT_RETRY = "payment-retry-topic";
}
