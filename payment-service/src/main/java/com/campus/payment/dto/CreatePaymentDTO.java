package com.campus.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentDTO {

    /** 用户ID */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** 订单ID */
    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /** 支付金额 */
    @NotNull(message = "支付金额不能为空")
    @Positive(message = "支付金额必须大于0")
    private BigDecimal amount;

    /** 支付方式（可选，默认 SIMULATE） */
    private String paymentMethod;
}
