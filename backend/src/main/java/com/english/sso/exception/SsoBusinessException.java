package com.english.sso.exception;

import com.english.exception.BusinessException;

/**
 * SSO 业务异常 — 携带 {@link SsoErrorCode}，供控制器映射为 URL errorCode 参数或
 * 响应体 errorCode 字段，让前端能按类型展示不同的友好提示。
 */
public class SsoBusinessException extends BusinessException {

    private final SsoErrorCode errorCode;

    public SsoBusinessException(SsoErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public SsoBusinessException(SsoErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SsoBusinessException(SsoErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public SsoErrorCode getErrorCode() {
        return errorCode;
    }
}
