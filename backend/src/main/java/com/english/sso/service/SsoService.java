package com.english.sso.service;

import com.english.sso.dto.SsoUserInfo;

import java.util.Map;

/**
 * SSO 服务接口（协议抽象层）
 *
 * @author LangHack Team
 * @since 2026-04-09
 */
public interface SsoService {

    /**
     * 生成 SSO 登录 URL
     *
     * @param callbackUrl 回调地址
     * @return SSO 登录页面 URL
     */
    String generateLoginUrl(String callbackUrl);

    /**
     * 验证 SSO 回调并获取用户信息
     *
     * @param callbackParams 回调参数 (ticket/code)
     * @param callbackUrl    回调地址
     * @return SSO 用户信息
     * @throws RuntimeException 验证失败
     */
    SsoUserInfo validateCallback(Map<String, String> callbackParams, String callbackUrl);

    /**
     * 获取协议类型
     *
     * @return 协议类型
     */
    SsoProtocolType getProtocolType();

    /**
     * 检查 SSO 是否启用
     *
     * @return true=启用, false=禁用
     */
    boolean isEnabled();
}
