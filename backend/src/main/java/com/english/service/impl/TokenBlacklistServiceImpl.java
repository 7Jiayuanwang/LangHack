package com.english.service.impl;

import com.english.service.TokenBlacklistService;
import com.english.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单服务实现。
 * 使用 Redis 存储已注销的 token 的 SHA-256 哈希，TTL 设为 token 剩余有效时间。
 * Redis 不可用时降级为不检查黑名单（不阻断正常认证流程）。
 */
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistServiceImpl.class);
    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public void blacklistToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            logger.warn("Attempted to blacklist null or empty token");
            return;
        }

        try {
            String key = buildRedisKey(token);
            long remainingMillis = jwtUtil.getTokenRemainingTimeMillis(token);

            if (remainingMillis <= 0) {
                // Token 已过期，无需加入黑名单
                logger.debug("Token already expired, skipping blacklist");
                return;
            }

            stringRedisTemplate.opsForValue().set(key, "1", remainingMillis, TimeUnit.MILLISECONDS);
            logger.info("Token blacklisted successfully, TTL={}ms", remainingMillis);
        } catch (Exception e) {
            // Redis 不可用时记录错误但不抛出异常，避免影响登出流程
            logger.error("Failed to blacklist token in Redis", e);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            String key = buildRedisKey(token);
            Boolean exists = stringRedisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // Redis 不可用时降级为不检查黑名单，不阻断正常认证
            logger.error("Failed to check token blacklist in Redis, degrading to allow", e);
            return false;
        }
    }

    /**
     * 构建 Redis key：使用 token 的 SHA-256 哈希避免存储过长的 key。
     */
    private String buildRedisKey(String token) {
        return BLACKLIST_KEY_PREFIX + sha256(token);
    }

    /**
     * 计算字符串的 SHA-256 哈希（十六进制表示）。
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every Java implementation
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
