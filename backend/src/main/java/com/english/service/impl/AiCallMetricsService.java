package com.english.service.impl;

import com.english.entity.AiCallLog;
import com.english.mapper.AiCallLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

@Service
public class AiCallMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(AiCallMetricsService.class);
    private static final int MAX_QUEUE_SIZE = 500;
    private static final int BATCH_SIZE = 200;
    private static final DateTimeFormatter DATE_KEY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final List<String> ALL_SERVICES = Arrays.asList(
            "HIAGENT", "OPENROUTER", "TENCENT_TRANSLATE", "TENCENT_ASR", "BILIBILI"
    );

    private final ConcurrentLinkedQueue<AiCallLog> buffer = new ConcurrentLinkedQueue<>();
    private final AiCallLogMapper aiCallLogMapper;
    private final StringRedisTemplate redisTemplate;

    public AiCallMetricsService(AiCallLogMapper aiCallLogMapper,
                                 StringRedisTemplate redisTemplate) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.redisTemplate = redisTemplate;
    }

    public long startTimer() {
        return System.currentTimeMillis();
    }

    public void recordSuccess(String service, String method, long startTime, String user) {
        recordCall(service, method, null, true, System.currentTimeMillis() - startTime,
                null, null, null, user);
    }

    public void recordSuccess(String service, String method, String modelName,
                               long startTime, String user) {
        recordCall(service, method, modelName, true, System.currentTimeMillis() - startTime,
                null, null, null, user);
    }

    public void recordFailure(String service, String method, long startTime,
                               String error, String user) {
        String safeError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        recordCall(service, method, null, false, System.currentTimeMillis() - startTime,
                safeError, null, null, user);
    }

    public void recordCall(String serviceName, String methodName, String modelName,
                            boolean success, long responseTimeMs,
                            String errorMessage, Integer inputTokens, Integer outputTokens,
                            String triggeredBy) {
        try {
            AiCallLog log = new AiCallLog();
            log.setServiceName(serviceName);
            log.setMethodName(methodName);
            log.setModelName(modelName);
            log.setSuccess(success);
            log.setResponseTimeMs(responseTimeMs);
            log.setErrorMessage(errorMessage);
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            log.setTriggeredBy(triggeredBy);
            log.setCreatedTime(LocalDateTime.now());

            if (buffer.size() >= MAX_QUEUE_SIZE) {
                buffer.poll();
            }
            buffer.offer(log);

            // Update Redis counters
            updateRedisCounters(serviceName, success, responseTimeMs);
        } catch (Exception e) {
            logger.debug("Failed to record AI call metric", e);
        }
    }

    private void updateRedisCounters(String service, boolean success, long responseTimeMs) {
        try {
            String dateKey = LocalDate.now().format(DATE_KEY);

            String countKey = "ai:today:" + service + ":count:" + dateKey;
            redisTemplate.opsForValue().increment(countKey);
            redisTemplate.expire(countKey, 2, TimeUnit.DAYS);

            if (success) {
                String successKey = "ai:today:" + service + ":success:" + dateKey;
                redisTemplate.opsForValue().increment(successKey);
                redisTemplate.expire(successKey, 2, TimeUnit.DAYS);
            }

            String timeKey = "ai:today:" + service + ":total_time:" + dateKey;
            redisTemplate.opsForValue().increment(timeKey, responseTimeMs);
            redisTemplate.expire(timeKey, 2, TimeUnit.DAYS);
        } catch (Exception e) {
            logger.debug("Failed to update Redis AI call counters", e);
        }
    }

    @Scheduled(fixedRate = 10000)
    public void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        List<AiCallLog> batch = new ArrayList<>();
        AiCallLog item;
        while ((item = buffer.poll()) != null && batch.size() < BATCH_SIZE) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                aiCallLogMapper.batchInsert(batch);
                logger.debug("Flushed {} AI call logs to DB", batch.size());
            } catch (Exception e) {
                logger.warn("Failed to flush AI call logs, {} records lost", batch.size(), e);
            }
        }
    }

    public Map<String, Object> getTodayStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        String dateKey = LocalDate.now().format(DATE_KEY);

        for (String service : ALL_SERVICES) {
            Map<String, Object> serviceStats = new LinkedHashMap<>();
            try {
                String countStr = redisTemplate.opsForValue().get("ai:today:" + service + ":count:" + dateKey);
                String successStr = redisTemplate.opsForValue().get("ai:today:" + service + ":success:" + dateKey);
                String timeStr = redisTemplate.opsForValue().get("ai:today:" + service + ":total_time:" + dateKey);

                long totalCalls = countStr != null ? Long.parseLong(countStr) : 0;
                long successCalls = successStr != null ? Long.parseLong(successStr) : 0;
                long totalTime = timeStr != null ? Long.parseLong(timeStr) : 0;

                serviceStats.put("totalCalls", totalCalls);
                serviceStats.put("successCalls", successCalls);
                serviceStats.put("failedCalls", totalCalls - successCalls);
                serviceStats.put("successRate", totalCalls > 0
                        ? Math.round((double) successCalls / totalCalls * 100) : 100);
                serviceStats.put("avgResponseTimeMs", totalCalls > 0
                        ? Math.round((double) totalTime / totalCalls) : 0);
            } catch (Exception e) {
                serviceStats.put("totalCalls", 0);
                serviceStats.put("successCalls", 0);
                serviceStats.put("failedCalls", 0);
                serviceStats.put("successRate", 100);
                serviceStats.put("avgResponseTimeMs", 0);
            }
            result.put(service, serviceStats);
        }

        return result;
    }

    /**
     * Get current user from SecurityContext.
     */
    public static String getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {
        }
        return "SYSTEM";
    }
}
