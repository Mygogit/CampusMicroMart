package com.campus.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付回调响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallbackResponse {

    /** 响应码 */
    private String code;

    /** 响应消息 */
    private String message;

    /** 支付单号 */
    private String paymentNo;

    /**
     * 成功响应
     *
     * @param paymentNo 支付单号
     * @return 成功回调响应
     */
    public static CallbackResponse success(String paymentNo) {
        return new CallbackResponse("200", "success", paymentNo);
    }

    /**
     * 失败响应
     *
     * @param code      错误码
     * @param message   错误消息
     * @param paymentNo 支付单号
     * @return 失败回调响应
     */
    public static CallbackResponse fail(String code, String message, String paymentNo) {
        return new CallbackResponse(code, message, paymentNo);
    }
}
