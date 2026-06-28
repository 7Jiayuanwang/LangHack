package com.english.service.impl;

import com.english.entity.AiCallLog;
import com.english.entity.User;
import com.english.enums.TaskType;
import com.english.mapper.AiCallLogMapper;
import com.english.service.CampaignStatsService;
import com.english.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final List<String> KNOWN_SERVICES = Arrays.asList(
            "HIAGENT", "OPENAI_COMPATIBLE", "OPENROUTER",
            "TENCENT_TRANSLATE", "HIAGENT_TRANSLATE", "TENCENT_ASR", "BILIBILI"
    );

    private final ConcurrentLinkedQueue<AiCallLog> buffer = new ConcurrentLinkedQueue<>();
    private final AiCallLogMapper aiCallLogMapper;
    private final StringRedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private CampaignStatsService campaignStatsService;

    public AiCallMetricsService(AiCallLogMapper aiCallLogMapper,
                                 StringRedisTemplate redisTemplate) {
        this.aiCallLogMapper = aiCallLogMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Return the list of known service name constants used in recordCall().
     * These MUST match the serviceName parameters passed to recordSuccess/recordFailure.
     */
    private List<String> getActiveServiceNames() {
        return KNOWN_SERVICES;
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
                AiCallLog dropped = buffer.poll();
                logger.warn("AI调用缓冲队列已满，丢弃最早记录: service={}, method={}",
                    dropped != null ? dropped.getServiceName() : "unknown",
                    dropped != null ? dropped.getMethodName() : "unknown");
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
        while (batch.size() < BATCH_SIZE && (item = buffer.poll()) != null) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                aiCallLogMapper.batchInsert(batch);
                logger.debug("Flushed {} AI call logs to DB", batch.size());

                // 事件驱动: 触发活动数据采集（仅针对成功的 QA 调用）
                for (AiCallLog log : batch) {
                    if (log.getSuccess() &&
                        ("QA".equals(log.getServiceName()) || "HIAGENT".equals(log.getServiceName())) &&
                        "chatQA".equals(log.getMethodName())) {

                        String username = log.getTriggeredBy();
                        if (username != null) {
                            try {
                                User user = userService.getUserByUsername(username);
                                if (user != null) {
                                    campaignStatsService.collectUserTasksByTypeAsync(user.getId(), TaskType.AI_QA);
                                }
                            } catch (Exception e) {
                                logger.error("触发活动数据采集失败: username={}, service={}, method={}",
                                    username, log.getServiceName(), log.getMethodName(), e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to flush AI call logs, {} records lost", batch.size(), e);
            }
        }
    }

    public Map<String, Object> getTodayStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        String dateKey = LocalDate.now().format(DATE_KEY);

        for (String service : getActiveServiceNames()) {
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
