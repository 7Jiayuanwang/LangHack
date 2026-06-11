package com.english.util;

/**
 * 敏感数据脱敏工具类 — 用于日志输出时隐藏敏感信息
 */
public class SensitiveDataMasker {

    /**
     * 脱敏密码 — 完全隐藏
     */
    public static String maskPassword(String password) {
        return password == null ? null : "******";
    }

    /**
     * 脱敏 Token — 仅保留前后各4位
     */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * 脱敏手机号 — 保留前3后4位
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 脱敏邮箱 — 保留前2位和@域名
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***@***";
        }
        int atIndex = email.indexOf('@');
        String prefix = email.substring(0, Math.min(2, atIndex));
        return prefix + "***@" + email.substring(atIndex + 1);
    }

    /**
     * 脱敏身份证 — 保留前6后4位
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return "******";
        }
        return idCard.substring(0, 6) + "****" + idCard.substring(idCard.length() - 4);
    }
}
