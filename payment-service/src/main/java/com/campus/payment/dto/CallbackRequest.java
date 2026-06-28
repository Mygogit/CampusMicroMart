package com.campus.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付回调请求 DTO
 */
@Data
public class CallbackRequest {

    /** 支付单号 */
    @NotBlank(message = "支付单号不能为空")
    private String paymentNo;

    /** 回调状态：SUCCESS / FAIL */
    @NotBlank(message = "回调状态不能为空")
    private String callbackStatus;

    /** 失败原因 */
    private String failReason;

    /** 签名占位，模拟环境传 "mock_sign" */
    private String sign;

    /** 时间戳 */
    @NotNull(message = "时间戳不能为空")
    private Long timestamp;
}
