package com.english.service.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Short-lived mapping between a user's platform conversation and its HiAgent QA session.
 * Only opaque IDs are cached; no transcript or uploaded content is copied here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaUpstreamSessionStore {

    private final CacheManager cacheManager;
    private final com.github.benmanes.caffeine.cache.Cache<String, String> local = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    public String get(Long userId, Long platformConversationId) {
        String key = key(userId, platformConversationId);
        try {
            Cache cache = cacheManager.getCache("agent:qa-upstream");
            if (cache != null) {
                String value = cache.get(key, String.class);
                if (value != null) {
                    local.put(key, value);
                    return value;
                }
            }
        } catch (RuntimeException e) {
            log.warn("HiAgent QA session cache read unavailable: {}", e.getMessage());
        }
        return local.getIfPresent(key);
    }

    public void put(Long userId, Long platformConversationId, String upstreamId) {
        if (upstreamId == null || upstreamId.isBlank()) return;
        String key = key(userId, platformConversationId);
        local.put(key, upstreamId);
        try {
            Cache cache = cacheManager.getCache("agent:qa-upstream");
            if (cache != null) cache.put(key, upstreamId);
        } catch (RuntimeException e) {
            log.warn("HiAgent QA session cache write unavailable: {}", e.getMessage());
        }
    }

    private String key(Long userId, Long conversationId) {
        if (userId == null || conversationId == null) {
            throw new IllegalArgumentException("userId and conversationId are required");
        }
        return userId + ":" + conversationId;
    }
}
