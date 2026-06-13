package com.campus.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderDTO {
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    // 单商品下单字段（向后兼容，与 items 二选一）
    private Long productId;
    private String productName;
    @Min(value = 0, message = "价格不能为负")
    private BigDecimal price;
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity;

    // 多商品下单
    @NotEmpty(message = "订单商品列表不能为空")
    private List<OrderItemDTO> items;

    private String shippingAddress;
    private String buyerPhone;
    private String remark;
}
