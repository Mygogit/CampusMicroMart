package com.campus.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymentDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "订单ID不能为空")
    private Long orderId;
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;
    @NotNull(message = "支付金额不能为空")
    @Min(value = 0, message = "支付金额不能为负")
    private BigDecimal amount;
    @NotNull(message = "支付方式不能为空")
    private Integer paymentMethod;
}
