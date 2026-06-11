package com.english.service;

/**
 * Token 黑名单服务接口。
 * 用于在用户登出时将 JWT Token 加入黑名单，使其立即失效。
 * 基于 Redis 实现，黑名单条目在 token 过期后自动清除。
 */
public interface TokenBlacklistService {

    /**
     * 将 token 加入黑名单。
     * Redis key 使用 token 的 SHA-256 哈希，TTL 设为 token 的剩余有效时间。
     *
     * @param token JWT token 字符串
     */
    void blacklistToken(String token);

    /**
     * 将 token 加入黑名单（指定过期时间）。
     *
     * @param token JWT token 字符串
     * @param expirationMillis 过期时间（毫秒）
     */
    void addToBlacklist(String token, long expirationMillis);

    /**
     * 检查 token 是否在黑名单中。
     * 当 Redis 不可用时降级返回 false（不阻断正常认证流程）。
     *
     * @param token JWT token 字符串
     * @return true 表示 token 已被加入黑名单
     */
    boolean isBlacklisted(String token);
}
