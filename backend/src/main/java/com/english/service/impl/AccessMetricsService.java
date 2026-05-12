package com.english.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccessMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(AccessMetricsService.class);
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;
    private final SystemMetricsService systemMetricsService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final AtomicLong concurrentRequests = new AtomicLong(0);
    private final AtomicLong peakConcurrentToday = new AtomicLong(0);
    private volatile String lastPeakDate = LocalDate.now().format(DATE_KEY);

    /** 缓存 SystemMetrics 5 秒，避免 SSE 每 5s 为每个订阅者重复做 Redis INFO + JMX + HikariPool + 磁盘 IO。 */
    private static final long SYSTEM_METRICS_CACHE_TTL_MS = 5000L;
    private volatile Map<String, Object> cachedSystemMetrics;
    private volatile long cachedSystemMetricsAt = 0L;

    public AccessMetricsService(StringRedisTemplate redisTemplate,
                                 SystemMetricsService systemMetricsService) {
        this.redisTemplate = redisTemplate;
        this.systemMetricsService = systemMetricsService;
    }

    public void recordRequestStart(String username) {
        String dateKey = LocalDate.now().format(DATE_KEY);

        if (!dateKey.equals(lastPeakDate)) {
            peakConcurrentToday.set(0);
            lastPeakDate = dateKey;
        }

        long current = concurrentRequests.incrementAndGet();
        peakConcurrentToday.updateAndGet(prev -> Math.max(prev, current));

        try {
            // Increment today's request count
            String countKey = "access:today:count:" + dateKey;
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, 2, TimeUnit.DAYS);

            // Track unique users
            if (username != null) {
                String usersKey = "access:today:users:" + dateKey;
                redisTemplate.opsForSet().add(usersKey, username);
                redisTemplate.expire(usersKey, 2, TimeUnit.DAYS);

                // Track active users (5-minute window)
                String activeKey = "access:active:users";
                redisTemplate.opsForZSet().add(activeKey, username, System.currentTimeMillis());
                redisTemplate.expire(activeKey, 10, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            logger.debug("Failed to record request start metrics", e);
        }
    }

    public void recordRequestEnd() {
        concurrentRequests.updateAndGet(value -> value > 0 ? value - 1 : 0);
    }

    public void recordError() {
        try {
            String dateKey = LocalDate.now().format(DATE_KEY);
            String errorKey = "access:today:errors:" + dateKey;
            redisTemplate.opsForValue().increment(errorKey);
            redisTemplate.expire(errorKey, 2, TimeUnit.DAYS);
        } catch (Exception e) {
            logger.debug("Failed to record error metric", e);
        }
    }

    public Map<String, Object> getRealTimeMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        try {
            String dateKey = LocalDate.now().format(DATE_KEY);

            // Today's request count
            String countStr = redisTemplate.opsForValue().get("access:today:count:" + dateKey);
            metrics.put("todayRequests", countStr != null ? Long.parseLong(countStr) : 0L);

            // Today's unique users
            Long uniqueUsers = redisTemplate.opsForSet().size("access:today:users:" + dateKey);
            metrics.put("todayUniqueUsers", uniqueUsers != null ? uniqueUsers : 0L);

            // Active users (last 5 minutes)
            long fiveMinAgo = System.currentTimeMillis() - 5 * 60 * 1000;
            // Clean expired entries
            redisTemplate.opsForZSet().removeRangeByScore("access:active:users", 0, fiveMinAgo);
            Long activeUsers = redisTemplate.opsForZSet().zCard("access:active:users");
            metrics.put("activeUsers", activeUsers != null ? activeUsers : 0L);

            // Concurrent requests
            metrics.put("concurrentRequests", concurrentRequests.get());
            metrics.put("peakConcurrentToday", peakConcurrentToday.get());

            // Today's error count (status >= 400)
            String errorCountStr = redisTemplate.opsForValue().get("access:today:errors:" + dateKey);
            metrics.put("todayErrorCount", errorCountStr != null ? Long.parseLong(errorCountStr) : 0L);

        } catch (Exception e) {
            logger.debug("Failed to get realtime metrics", e);
            metrics.put("todayRequests", 0L);
            metrics.put("todayUniqueUsers", 0L);
            metrics.put("activeUsers", 0L);
            metrics.put("concurrentRequests", concurrentRequests.get());
            metrics.put("peakConcurrentToday", peakConcurrentToday.get());
            metrics.put("todayErrorCount", 0L);
        }
        return metrics;
    }

    public SseEmitter registerEmitter() {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial data immediately
        try {
            Map<String, Object> initialData = new LinkedHashMap<>();
            initialData.put("realtime", getRealTimeMetrics());
            initialData.put("system", getCachedSystemMetrics());
            emitter.send(SseEmitter.event().data(initialData));
        } catch (Exception e) {
            logger.debug("Failed to send initial SSE data", e);
        }

        return emitter;
    }

    /**
     * 返回最多 5 秒旧的 SystemMetrics 快照。
     * 调用方（SSE 广播 + initial emit）共享同一份缓存，
     * 避免 N 个 SSE 订阅者时每秒 N 次 Redis INFO/JMX 查询。
     */
    private Map<String, Object> getCachedSystemMetrics() {
        long now = System.currentTimeMillis();
        Map<String, Object> snapshot = cachedSystemMetrics;
        if (snapshot != null && now - cachedSystemMetricsAt < SYSTEM_METRICS_CACHE_TTL_MS) {
            return snapshot;
        }
        try {
            snapshot = systemMetricsService.getSystemMetrics();
            cachedSystemMetrics = snapshot;
            cachedSystemMetricsAt = now;
        } catch (Exception e) {
            logger.debug("Failed to refresh system metrics cache", e);
            if (snapshot == null) {
                snapshot = Collections.emptyMap();
            }
        }
        return snapshot;
    }

    @Scheduled(fixedRate = 5000)
    public void pushMetrics() {
        if (emitters.isEmpty()) {
            return;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("realtime", getRealTimeMetrics());
        data.put("system", getCachedSystemMetrics());
        data.put("timestamp", System.currentTimeMillis());

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
