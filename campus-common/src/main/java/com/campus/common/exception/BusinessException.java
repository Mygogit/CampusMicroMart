package com.campus.common.exception;

/**
 * 统一业务异常
 * <p>
 * 支持两种构造方式：仅传 message（默认 code=400），或同时传 code 和 message
 */
public class BusinessException extends RuntimeException {

    /** 错误码 */
    private final int code;

    /**
     * 使用错误消息构造（默认 code=400）
     */
    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    /**
     * 使用错误码和消息构造
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
