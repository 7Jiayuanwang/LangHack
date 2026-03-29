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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Stores blacklisted tokens in Redis and keeps a local fallback so logout
 * still takes effect when Redis is temporarily unavailable.
 */
@Service
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final Logger logger = LoggerFactory.getLogger(TokenBlacklistServiceImpl.class);
    private static final String BLACKLIST_KEY_PREFIX = "token:blacklist:";

    private final Map<String, Long> localBlacklist = new ConcurrentHashMap<>();

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

        String key = buildRedisKey(token);
        long remainingMillis = jwtUtil.getTokenRemainingTimeMillis(token);
        if (remainingMillis <= 0) {
            logger.debug("Token already expired, skipping blacklist");
            return;
        }

        localBlacklist.put(key, System.currentTimeMillis() + remainingMillis);

        try {
            stringRedisTemplate.opsForValue().set(key, "1", remainingMillis, TimeUnit.MILLISECONDS);
            logger.info("Token blacklisted successfully, TTL={}ms", remainingMillis);
        } catch (Exception e) {
            logger.error("Failed to blacklist token in Redis, local fallback remains active", e);
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        String key = buildRedisKey(token);
        cleanupExpiredLocalEntries();

        Long localExpiry = localBlacklist.get(key);
        if (localExpiry != null) {
            if (localExpiry > System.currentTimeMillis()) {
                return true;
            }
            localBlacklist.remove(key);
        }

        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Failed to check token blacklist in Redis, using local fallback", e);
            return false;
        }
    }

    private String buildRedisKey(String token) {
        return BLACKLIST_KEY_PREFIX + sha256(token);
    }

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
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private void cleanupExpiredLocalEntries() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = localBlacklist.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
            }
        }
    }
}
