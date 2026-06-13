package com.campus.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class BatchUpdateOrderStatusDTO {
    @NotEmpty(message = "订单ID列表不能为空")
    private List<Long> orderIds;
    @NotNull(message = "状态不能为空")
    private Integer status;
}
