package com.campus.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDTO {
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @NotNull(message = "价格不能为空")
    @Min(value = 0, message = "价格不能为负")
    private BigDecimal unitPrice;
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity;
}
