package com.campus.payment.constant;

import lombok.Getter;

/**
 * 支付错误码枚举
 * <p>
 * 错误码范围：40001-40010
 */
@Getter
public enum PaymentErrorCode {

    /** 支付记录不存在 */
    PAYMENT_NOT_FOUND(40001, "支付记录不存在"),

    /** 订单不存在 */
    ORDER_NOT_EXIST(40002, "订单不存在"),

    /** 订单状态不允许支付 */
    ORDER_STATUS_INVALID(40003, "订单状态不允许支付"),

    /** 支付金额与订单金额不匹配 */
    AMOUNT_MISMATCH(40004, "支付金额与订单金额不匹配"),

    /** 存在进行中的支付 */
    DUPLICATE_PAYMENT(40005, "存在进行中的支付"),

    /** 支付已过期 */
    PAYMENT_EXPIRED(40006, "支付已过期"),

    /** 重试次数已用尽 */
    RETRY_EXCEEDED(40007, "重试次数已用尽"),

    /** 状态转换非法 */
    STATE_TRANSITION_INVALID(40008, "状态转换非法"),

    /** 回调已处理 */
    CALLBACK_ALREADY_PROCESSED(40009, "回调已处理"),

    /** 当前状态不允许取消 */
    CANCEL_NOT_ALLOWED(40010, "当前状态不允许取消");

    /** 错误码 */
    private final int code;

    /** 错误消息 */
    private final String message;

    PaymentErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据错误码获取枚举值
     *
     * @param code 错误码
     * @return 对应的枚举值，未匹配返回 null
     */
    public static PaymentErrorCode getByCode(int code) {
        for (PaymentErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return null;
    }
}
