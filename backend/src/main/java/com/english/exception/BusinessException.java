package com.english.exception;

/**
 * 业务逻辑异常 — 用于传递可安全展示给前端的错误信息。
 *
 * 与 RuntimeException 的区别：
 * - BusinessException 的 message 会直接返回给客户端
 * - RuntimeException 的 message 会被通用错误信息替代，防止信息泄漏
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
