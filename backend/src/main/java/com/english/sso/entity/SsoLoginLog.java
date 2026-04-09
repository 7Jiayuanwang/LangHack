package com.english.sso.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 登录日志实体
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
@TableName("sso_login_logs")
public class SsoLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * SSO提供商
     */
    private String ssoProvider;

    /**
     * SSO用户ID
     */
    private String ssoUserId;

    /**
     * SSO用户名
     */
    private String ssoUsername;

    /**
     * 登录结果: SUCCESS, PENDING_APPROVAL, FAILED
     */
    private String loginResult;

    /**
     * 失败原因
     */
    private String failureReason;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 用户代理
     */
    private String userAgent;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
