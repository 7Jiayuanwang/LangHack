package com.english.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 频率限制管理器 - 基于 IP 地址限流
 */
@Component
public class RateLimitManager {

    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * 解析 IP（通用限流）- 容量30，greedy 补充30/s（均匀分散，防止 intervally 累积突刺）
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createGreedyBucket(30, 30, Duration.ofSeconds(1)));
    }

    /**
     * 解析登录限流 - 每分钟5次
     */
    public Bucket resolveLoginBucket(String key) {
        return cache.computeIfAbsent("login:" + key, k -> createNewBucket(5, 1, Duration.ofMinutes(1)));
    }

    /**
     * 解析注册限流 - 每小时3次
     */
    public Bucket resolveRegisterBucket(String key) {
        return cache.computeIfAbsent("register:" + key, k -> createNewBucket(3, 1, Duration.ofHours(1)));
    }

    private Bucket createGreedyBucket(long capacity, long refillTokens, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.greedy(refillTokens, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createNewBucket(long capacity, long refillTokens, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(refillTokens, refillPeriod));
        return Bucket.builder().addLimit(limit).build();
    }
}
