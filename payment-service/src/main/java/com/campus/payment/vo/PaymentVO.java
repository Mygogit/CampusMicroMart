package com.campus.payment.vo;

import com.campus.common.constant.PaymentStatusConstant;
import com.campus.payment.entity.Payment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 前端支付视图对象
 */
@Data
public class PaymentVO {

    /** 支付ID */
    private Long id;

    /** 支付单号 */
    private String paymentNo;

    /** 订单ID */
    private Long orderId;

    /** 支付金额 */
    private BigDecimal amount;

    /** 状态码 */
    private Integer status;

    /** 状态中文描述 */
    private String statusText;

    /** 当前重试次数 */
    private Integer retryCount;

    /** 最大重试次数（来自配置，需调用方设置） */
    private Integer maxRetries;

    /** 失败原因 */
    private String failReason;

    /** 剩余支付秒数（expireTime - now，已过期时为 0 或负数） */
    private Long remainingSeconds;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从 Payment 实体转换为 PaymentVO
     * <p>
     * 注意：maxRetries 来自系统配置，需调用方通过 setter 单独设置
     *
     * @param payment 支付实体
     * @return 支付视图对象
     */
    public static PaymentVO from(Payment payment) {
        PaymentVO vo = new PaymentVO();
        vo.setId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderId(payment.getOrderId());
        vo.setAmount(payment.getAmount());
        vo.setStatus(payment.getStatus());

        // 状态码 → 状态文本映射
        PaymentStatusConstant statusConst = PaymentStatusConstant.getByCode(payment.getStatus());
        vo.setStatusText(statusConst != null ? statusConst.getDescription() : "未知状态");

        vo.setRetryCount(payment.getRetryCount());
        vo.setFailReason(payment.getFailReason());
        vo.setExpireTime(payment.getExpireTime());
        vo.setCreateTime(payment.getCreateTime());

        // 计算剩余秒数
        if (payment.getExpireTime() != null) {
            long seconds = Duration.between(LocalDateTime.now(), payment.getExpireTime()).getSeconds();
            vo.setRemainingSeconds(Math.max(0, seconds));
        } else {
            vo.setRemainingSeconds(0L);
        }

        return vo;
    }
}
