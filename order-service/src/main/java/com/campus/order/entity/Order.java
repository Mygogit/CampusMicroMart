package com.campus.order.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("订单ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ExcelProperty("订单编号")
    private String orderNo;

    @ExcelProperty("用户ID")
    private Long userId;

    @ExcelProperty("商品ID")
    private Long productId;

    @ExcelProperty("商品名称")
    private String productName;

    @ExcelProperty("单价")
    @NumberFormat("#.##")
    private BigDecimal price;

    @ExcelProperty("数量")
    private Integer quantity;

    @ExcelProperty("总金额")
    @NumberFormat("#.##")
    private BigDecimal totalAmount;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("收货地址")
    private String shippingAddress;

    @ExcelProperty("联系电话")
    private String buyerPhone;

    @ExcelProperty("备注")
    private String remark;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @ExcelProperty("更新时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @ExcelIgnore
    @TableLogic
    private Integer deleted;
}
