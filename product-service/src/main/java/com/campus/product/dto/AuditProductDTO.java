package com.campus.product.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuditProductDTO {
    @NotNull(message = "商品ID不能为空")
    private Long productId;
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;
    private String reason;
}
