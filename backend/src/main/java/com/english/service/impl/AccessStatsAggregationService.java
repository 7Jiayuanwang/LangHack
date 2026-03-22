package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.AccessStatsDaily;
import com.english.entity.AccessStatsHourly;
import com.english.entity.AiCallStatsDaily;
import com.english.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
public class AccessStatsAggregationService {

    private static final Logger logger = LoggerFactory.getLogger(AccessStatsAggregationService.class);

    private final AccessLogMapper accessLogMapper;
    private final AccessStatsHourlyMapper hourlyMapper;
    private final AccessStatsDailyMapper dailyMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiCallStatsDailyMapper aiCallStatsDailyMapper;

    public AccessStatsAggregationService(AccessLogMapper accessLogMapper,
                                          AccessStatsHourlyMapper hourlyMapper,
                                          AccessStatsDailyMapper dailyMapper,
                                          AiCallLogMapper aiCallLogMapper,
                                          AiCallStatsDailyMapper aiCallStatsDailyMapper) {
        this.accessLogMapper = accessLogMapper;
        this.hourlyMapper = hourlyMapper;
        this.dailyMapper = dailyMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.aiCallStatsDailyMapper = aiCallStatsDailyMapper;
    }

    /**
     * Aggregate access logs into hourly stats (runs at 5 minutes past each hour).
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void aggregateHourly() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfLastHour = now.minusHours(1).withMinute(0).withSecond(0).withNano(0);
            LocalDateTime endOfLastHour = startOfLastHour.plusHours(1);

            // Check if already aggregated
            QueryWrapper<AccessStatsHourly> check = new QueryWrapper<>();
            check.eq("stat_hour", startOfLastHour);
            if (hourlyMapper.selectCount(check) > 0) {
                return;
            }

            List<Map<String, Object>> results = accessLogMapper.aggregateHourly(startOfLastHour, endOfLastHour);
            for (Map<String, Object> row : results) {
                AccessStatsHourly stat = new AccessStatsHourly();
                stat.setStatHour(startOfLastHour);
                stat.setTotalRequests(toInt(row.get("total_requests")));
                stat.setUniqueUsers(toInt(row.get("unique_users")));
                stat.setAvgResponseTimeMs(toInt(row.get("avg_response_time_ms")));
                stat.setMaxResponseTimeMs(toInt(row.get("max_response_time_ms")));
                stat.setErrorCount(toInt(row.get("error_count")));
                stat.setCreatedTime(LocalDateTime.now());
                hourlyMapper.insert(stat);
            }

            if (!results.isEmpty()) {
                logger.info("Aggregated hourly access stats for {}", startOfLastHour);
            }
        } catch (Exception e) {
            logger.error("Failed to aggregate hourly access stats", e);
        }
    }

    /**
     * Aggregate hourly stats into daily stats (runs at 00:15 daily).
     */
    @Scheduled(cron = "0 15 0 * * ?")
    public void aggregateDaily() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);

            // Check if already aggregated
            QueryWrapper<AccessStatsDaily> check = new QueryWrapper<>();
            check.eq("stat_date", yesterday);
            if (dailyMapper.selectCount(check) > 0) {
                return;
            }

            // Aggregate from hourly stats
            LocalDateTime dayStart = yesterday.atStartOfDay();
            LocalDateTime dayEnd = yesterday.atTime(LocalTime.MAX);

            QueryWrapper<AccessStatsHourly> wrapper = new QueryWrapper<>();
            wrapper.between("stat_hour", dayStart, dayEnd);
            List<AccessStatsHourly> hourlyStats = hourlyMapper.selectList(wrapper);

            if (hourlyStats.isEmpty()) {
                return;
            }

            int totalRequests = 0;
            int maxUniqueUsers = 0;
            long totalResponseTime = 0;
            int maxResponseTime = 0;
            int totalErrors = 0;

            for (AccessStatsHourly h : hourlyStats) {
                totalRequests += h.getTotalRequests();
                maxUniqueUsers = Math.max(maxUniqueUsers, h.getUniqueUsers());
                totalResponseTime += (long) h.getAvgResponseTimeMs() * h.getTotalRequests();
                maxResponseTime = Math.max(maxResponseTime, h.getMaxResponseTimeMs());
                totalErrors += h.getErrorCount();
            }

            // Get top pages
            List<Map<String, Object>> topPages = accessLogMapper.topPagesByDateRange(dayStart, dayEnd.plusSeconds(1), 20);
            String topPagesJson = topPages != null ? convertToJson(topPages) : "[]";

            AccessStatsDaily daily = new AccessStatsDaily();
            daily.setStatDate(yesterday);
            daily.setTotalRequests(totalRequests);
            daily.setUniqueUsers(maxUniqueUsers);
            daily.setPeakConcurrent(0); // Will be updated from Redis data if available
            daily.setAvgResponseTimeMs(totalRequests > 0 ? (int) (totalResponseTime / totalRequests) : 0);
            daily.setMaxResponseTimeMs(maxResponseTime);
            daily.setErrorCount(totalErrors);
            daily.setTopPages(topPagesJson);
            daily.setCreatedTime(LocalDateTime.now());

            dailyMapper.insert(daily);
            logger.info("Aggregated daily access stats for {}", yesterday);
        } catch (Exception e) {
            logger.error("Failed to aggregate daily access stats", e);
        }
    }

    /**
     * Aggregate AI call logs into daily stats (runs at 00:20 daily).
     */
    @Scheduled(cron = "0 20 0 * * ?")
    public void aggregateAiCallsDaily() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime dayStart = yesterday.atStartOfDay();
            LocalDateTime dayEnd = yesterday.atTime(LocalTime.MAX);

            List<Map<String, Object>> results = aiCallLogMapper.aggregateDaily(dayStart, dayEnd.plusSeconds(1));
            for (Map<String, Object> row : results) {
                String serviceName = (String) row.get("service_name");

                // Check if already exists
                QueryWrapper<AiCallStatsDaily> check = new QueryWrapper<>();
                check.eq("stat_date", yesterday).eq("service_name", serviceName);
                if (aiCallStatsDailyMapper.selectCount(check) > 0) {
                    continue;
                }

                AiCallStatsDaily stat = new AiCallStatsDaily();
                stat.setStatDate(yesterday);
                stat.setServiceName(serviceName);
                stat.setTotalCalls(toInt(row.get("total_calls")));
                stat.setSuccessCalls(toInt(row.get("success_calls")));
                stat.setFailedCalls(toInt(row.get("failed_calls")));
                stat.setAvgResponseTimeMs(toInt(row.get("avg_response_time_ms")));
                stat.setMaxResponseTimeMs(toInt(row.get("max_response_time_ms")));
                stat.setTotalInputTokens(toLong(row.get("total_input_tokens")));
                stat.setTotalOutputTokens(toLong(row.get("total_output_tokens")));
                stat.setCreatedTime(LocalDateTime.now());
                aiCallStatsDailyMapper.insert(stat);
            }

            if (!results.isEmpty()) {
                logger.info("Aggregated AI call daily stats for {}", yesterday);
            }
        } catch (Exception e) {
            logger.error("Failed to aggregate AI call daily stats", e);
        }
    }

    /**
     * Cleanup old access logs (>7 days) and hourly stats (>90 days).
     * Runs at 03:30 daily.
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanupOldAccessLogs() {
        try {
            // Delete access_log older than 7 days
            LocalDateTime accessCutoff = LocalDateTime.now().minusDays(7);
            int accessDeleted = accessLogMapper.deleteOlderThan(accessCutoff);
            if (accessDeleted > 0) {
                logger.info("Cleaned up {} old access log records", accessDeleted);
            }

            // Delete hourly stats older than 90 days
            LocalDateTime hourlyCutoff = LocalDateTime.now().minusDays(90);
            QueryWrapper<AccessStatsHourly> hourlyWrapper = new QueryWrapper<>();
            hourlyWrapper.lt("stat_hour", hourlyCutoff);
            int hourlyDeleted = hourlyMapper.delete(hourlyWrapper);
            if (hourlyDeleted > 0) {
                logger.info("Cleaned up {} old hourly stats records", hourlyDeleted);
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup old access logs", e);
        }
    }

    /**
     * Cleanup old AI call logs (>30 days). Runs at 03:35 daily.
     */
    @Scheduled(cron = "0 35 3 * * ?")
    public void cleanupOldAiCallLogs() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deleted = aiCallLogMapper.deleteOlderThan(cutoff);
            if (deleted > 0) {
                logger.info("Cleaned up {} old AI call log records", deleted);
            }
        } catch (Exception e) {
            logger.error("Failed to cleanup old AI call logs", e);
        }
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number) return ((Number) val).longValue();
        return Long.parseLong(val.toString());
    }

    private String convertToJson(List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            Map<String, Object> item = list.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"uri\":\"").append(escapeJson(String.valueOf(item.get("uri"))))
              .append("\",\"count\":").append(item.get("visit_count")).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
