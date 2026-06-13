package com.campus.order.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderDTO {
    @NotNull(message = "订单ID不能为空")
    private Long id;
    private String remark;
}
