package com.english.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 管理员登录令牌生成器
 * 用于生成和验证管理员专用登录入口的访问令牌
 */
@Component
public class AdminLoginTokenGenerator {

    @Value("${admin.login.secret:default-secret}")
    private String secret;

    /**
     * 生成管理员登录令牌
     *
     * @param timestamp 时间戳
     * @return 令牌字符串
     */
    public String generateToken(long timestamp) {
        String str = timestamp + secret;
        int hash = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            hash = ((hash << 5) - hash) + c;
            hash = hash & hash;
        }
        return Integer.toString(Math.abs(hash), 36);
    }

    /**
     * 验证管理员登录令牌
     *
     * @param token     令牌
     * @param timestamp 时间戳
     * @return 是否有效
     */
    public boolean validateToken(String token, long timestamp) {
        // 检查时间戳是否在有效期内（5分钟）
        long now = System.currentTimeMillis();
        if (now - timestamp > 5 * 60 * 1000) {
            return false;
        }

        // 验证令牌
        String expectedToken = generateToken(timestamp);
        return expectedToken.equals(token);
    }

    /**
     * 生成完整的管理员登录 URL
     *
     * @param baseUrl 基础 URL
     * @return 完整的登录 URL
     */
    public String generateAdminLoginUrl(String baseUrl) {
        long timestamp = System.currentTimeMillis();
        String token = generateToken(timestamp);
        return String.format("%s/admin-portal?token=%s&t=%d", baseUrl, token, timestamp);
    }
}
