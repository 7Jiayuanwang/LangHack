package com.english.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 提权状态管理服务
 * 使用 Redis 存储临时提权状态，TTL 2 小时。
 * 支持 ADMIN ↔ OPS 双向提权。
 * Key 格式: elevation:user:{username} -> targetRole
 */
@Service("elevationService")
public class ElevationService {

    private static final Logger logger = LoggerFactory.getLogger(ElevationService.class);
    private static final String ELEVATION_KEY_PREFIX = "elevation:user:";
    private static final String ELEVATION_ATTEMPT_PREFIX = "elevation:attempts:";
    private static final long ELEVATION_TTL_HOURS = 2;
    private static final int MAX_ELEVATION_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;
    private static final Set<String> VALID_ELEVATION_ROLES = Set.of("ADMIN", "OPS");

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 写入提权状态
     *
     * @param username   用户名
     * @param targetRole 目标角色（OPS 或 ADMIN）
     */
    public void elevate(String username, String targetRole) {
        if (username == null || !VALID_ELEVATION_ROLES.contains(targetRole)) {
            throw new IllegalArgumentException("Invalid elevation parameters");
        }
        String key = ELEVATION_KEY_PREFIX + username;
        stringRedisTemplate.opsForValue().set(key, targetRole, ELEVATION_TTL_HOURS, TimeUnit.HOURS);
        logger.info("User {} elevated to role {}, TTL={}h", username, targetRole, ELEVATION_TTL_HOURS);
    }

    /**
     * 获取用户当前的提权角色（如果有效）
     *
     * @param username 用户名
     * @return 提权后的角色，null 表示无有效提权
     */
    public String getElevatedRole(String username) {
        if (username == null) {
            return null;
        }
        try {
            String key = ELEVATION_KEY_PREFIX + username;
            String storedRole = stringRedisTemplate.opsForValue().get(key);
            if (storedRole != null && VALID_ELEVATION_ROLES.contains(storedRole)) {
                return storedRole;
            }
            return null;
        } catch (Exception e) {
            logger.debug("Elevation check failed for user {}: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * 撤销提权
     *
     * @param username 用户名
     */
    public void revoke(String username) {
        if (username == null) {
            logger.warn("Attempted to revoke elevation for null username");
            return;
        }
        String key = ELEVATION_KEY_PREFIX + username;
        stringRedisTemplate.delete(key);
        logger.info("User {} elevation revoked", username);
    }

    /**
     * 检查提权尝试次数是否超过限制
     *
     * @param username 用户名
     * @return true 如果允许尝试，false 如果已被锁定
     */
    public boolean checkRateLimit(String username) {
        if (username == null) {
            return false;
        }
        try {
            String key = ELEVATION_ATTEMPT_PREFIX + username;
            Long attempts = stringRedisTemplate.opsForValue().increment(key);
            if (attempts != null && attempts == 1) {
                stringRedisTemplate.expire(key, LOCKOUT_DURATION_MINUTES, TimeUnit.MINUTES);
            }
            return attempts == null || attempts <= MAX_ELEVATION_ATTEMPTS;
        } catch (Exception e) {
            // Redis 不可用时放行（降级策略）
            logger.debug("Rate limit check failed: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 提权成功后清除失败计数
     *
     * @param username 用户名
     */
    public void clearRateLimit(String username) {
        if (username == null) return;
        try {
            stringRedisTemplate.delete(ELEVATION_ATTEMPT_PREFIX + username);
        } catch (Exception e) {
            logger.debug("Failed to clear rate limit: {}", e.getMessage());
        }
    }

    /**
     * 获取提权剩余时间（秒）
     *
     * @param username 用户名
     * @return 剩余秒数，-1 表示无提权或已过期
     */
    public long getRemainingSeconds(String username) {
        String key = ELEVATION_KEY_PREFIX + username;
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null && ttl > 0 ? ttl : -1;
    }
}
