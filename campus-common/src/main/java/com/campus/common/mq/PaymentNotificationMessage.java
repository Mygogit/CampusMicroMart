package com.campus.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationMessage implements Serializable {

    private Long paymentId;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private BigDecimal amount;
    private Boolean success;
    private String thirdPartyNo;
    private LocalDateTime timestamp;
}
