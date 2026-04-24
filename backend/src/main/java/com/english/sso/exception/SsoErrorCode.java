package com.english.sso.exception;

/**
 * SSO 错误码枚举
 *
 * 统一描述 SSO 登录流程中可能出现的错误场景，
 * 供前端 `ssoErrorMapper` 映射为带联系方式的友好提示。
 *
 * 每个错误码携带一个默认 message，供后端快速抛出；
 * 前端根据 code 决定展示的标题/副标题/图标主题。
 */
public enum SsoErrorCode {

    /**
     * 用户在本系统中不存在（CAS 认证通过但无授权账号）
     */
    USER_NOT_FOUND("USER_NOT_FOUND", "您尚未获得系统使用权限"),

    /**
     * 账号待审批
     */
    PENDING_APPROVAL("PENDING_APPROVAL", "您的账号正在审批中"),

    /**
     * CAS/OAuth2 票据校验失败
     */
    AUTH_FAILED("AUTH_FAILED", "统一身份认证校验未通过"),

    /**
     * 一次性授权码无效或已过期
     */
    CODE_EXPIRED("CODE_EXPIRED", "授权码无效或已过期"),

    /**
     * SSO 功能未启用
     */
    SSO_DISABLED("SSO_DISABLED", "统一身份认证暂时不可用"),

    /**
     * 缺少必要的认证参数
     */
    MISSING_PARAM("MISSING_PARAM", "缺少必要的认证参数"),

    /**
     * 系统异常（兜底）
     */
    SYSTEM_ERROR("SYSTEM_ERROR", "系统暂时无法访问"),

    /**
     * 未知错误
     */
    UNKNOWN("UNKNOWN", "登录失败");

    private final String code;
    private final String defaultMessage;

    SsoErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 根据字符串 code 反查枚举；未知时返回 UNKNOWN
     */
    public static SsoErrorCode fromCode(String code) {
        if (code == null) {
            return UNKNOWN;
        }
        for (SsoErrorCode ec : values()) {
            if (ec.code.equals(code)) {
                return ec;
            }
        }
        return UNKNOWN;
    }
}
