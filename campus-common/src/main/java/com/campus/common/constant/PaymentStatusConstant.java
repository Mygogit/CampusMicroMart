package com.campus.common.constant;

import lombok.Getter;

/**
 * 支付状态常量枚举
 * <p>
 * 状态流转：WAITING_PAY → PROCESSING → SUCCESS/FAIL
 * WAITING_PAY → EXPIRED
 * FAIL → RETRYING → PROCESSING → SUCCESS/FAIL
 */
@Getter
public enum PaymentStatusConstant {

    /** 待支付 */
    WAITING_PAY(0, "待支付"),

    /** 处理中 */
    PROCESSING(1, "处理中"),

    /** 支付成功 */
    SUCCESS(2, "支付成功"),

    /** 支付失败 */
    FAIL(3, "支付失败"),

    /** 已过期 */
    EXPIRED(4, "已过期"),

    /** 重试中 */
    RETRYING(5, "重试中");

    /** 状态码 */
    private final Integer code;

    /** 状态中文描述 */
    private final String description;

    PaymentStatusConstant(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态码获取枚举值
     *
     * @param code 状态码
     * @return 对应的枚举值，未匹配返回 null
     */
    public static PaymentStatusConstant getByCode(Integer code) {
        for (PaymentStatusConstant status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
