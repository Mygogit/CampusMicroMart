package com.campus.common.feign;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 服务间共享的商品快照（用于 order/payment 服务服务端取价与属主识别）。
 */
@Data
public class ProductInfo {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer stock;
    /** 发布者（卖家）用户 ID */
    private Long userId;
    private Integer productStatus;
    private Integer auditStatus;
}
