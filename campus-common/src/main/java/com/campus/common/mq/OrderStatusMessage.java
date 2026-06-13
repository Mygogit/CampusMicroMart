package com.campus.common.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusMessage implements Serializable {

    private Long orderId;
    private String orderNo;
    private Integer oldStatus;
    private Integer newStatus;
    private LocalDateTime timestamp;
}
