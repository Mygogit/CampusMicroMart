package com.campus.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment")
public class Payment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 支付单号 */
    private String paymentNo;

    /** 用户ID */
    private Long userId;

    /** 订单ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 支付金额 */
    private BigDecimal amount;

    /** 支付方式（SIMULATE/DEPOSIT） */
    private String paymentMethod;

    /** 支付状态（0-5，对应 PaymentStatusConstant） */
    private Integer status;

    /** 第三方支付流水号 */
    private String thirdPartyNo;

    /** 支付过期时间（创建时间 + 15分钟） */
    private LocalDateTime expireTime;

    /** 重试次数（默认 0） */
    private Integer retryCount;

    /** 回调状态（NULL=未回调, SUCCESS=成功回调, FAIL=失败回调） */
    private String callbackStatus;

    /** 失败原因 */
    private String failReason;

    /** 回调时间 */
    private LocalDateTime callbackTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
