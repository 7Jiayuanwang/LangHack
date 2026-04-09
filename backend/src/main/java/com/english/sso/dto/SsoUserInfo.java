package com.english.sso.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SSO 用户信息 DTO
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
@Data
public class SsoUserInfo {

    /**
     * SSO提供商
     */
    private String provider;

    /**
     * SSO用户唯一标识
     */
    private String ssoUserId;

    /**
     * SSO用户名
     */
    private String ssoUsername;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 组织单位列表
     */
    private List<String> memberOf;

    /**
     * 原始属性
     */
    private Map<String, Object> attributes;
}
