package com.english.scheduler;

import com.english.mapper.AccessLogMapper;
import com.english.mapper.AccessStatsDailyMapper;
import com.english.mapper.AiCallLogMapper;
import com.english.mapper.AiCallStatsDailyMapper;
import com.english.entity.AccessStatsDaily;
import com.english.entity.AiCallStatsDaily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class StatsAggregationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StatsAggregationScheduler.class);

    private final AccessLogMapper accessLogMapper;
    private final AccessStatsDailyMapper dailyMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AiCallStatsDailyMapper aiCallStatsDailyMapper;

    public StatsAggregationScheduler(AccessLogMapper accessLogMapper,
                                      AccessStatsDailyMapper dailyMapper,
                                      AiCallLogMapper aiCallLogMapper,
                                      AiCallStatsDailyMapper aiCallStatsDailyMapper) {
        this.accessLogMapper = accessLogMapper;
        this.dailyMapper = dailyMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.aiCallStatsDailyMapper = aiCallStatsDailyMapper;
    }

    @Scheduled(cron = "0 10 2 * * ?")
    public void aggregateDailyStats() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime startTime = yesterday.atStartOfDay();
            LocalDateTime endTime = yesterday.plusDays(1).atStartOfDay();

            List<Map<String, Object>> hourlyData = accessLogMapper.aggregateHourly(startTime, endTime);

            if (!hourlyData.isEmpty()) {
                int totalRequests = 0;
                int uniqueUsers = 0;
                long totalResponseTime = 0;
                int maxResponseTime = 0;
                int errorCount = 0;

                for (Map<String, Object> data : hourlyData) {
                    totalRequests += ((Number) data.get("total_requests")).intValue();
                    uniqueUsers = Math.max(uniqueUsers, ((Number) data.get("unique_users")).intValue());
                    totalResponseTime += ((Number) data.get("avg_response_time_ms")).intValue() *
                                        ((Number) data.get("total_requests")).intValue();
                    maxResponseTime = Math.max(maxResponseTime, ((Number) data.get("max_response_time_ms")).intValue());
                    errorCount += ((Number) data.get("error_count")).intValue();
                }

                AccessStatsDaily daily = new AccessStatsDaily();
                daily.setStatDate(yesterday);
                daily.setTotalRequests(totalRequests);
                daily.setUniqueUsers(uniqueUsers);
                daily.setAvgResponseTimeMs(totalRequests > 0 ? (int) (totalResponseTime / totalRequests) : 0);
                daily.setMaxResponseTimeMs(maxResponseTime);
                daily.setErrorCount(errorCount);
                daily.setCreatedTime(LocalDateTime.now());

                dailyMapper.insert(daily);
                log.info("聚合日统计完成: {}", yesterday);
            }
        } catch (Exception e) {
            log.error("聚合日统计失败", e);
        }
    }

    @Scheduled(cron = "0 15 2 * * ?")
    public void aggregateAiCallDaily() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LocalDateTime startTime = yesterday.atStartOfDay();
            LocalDateTime endTime = yesterday.plusDays(1).atStartOfDay();

            List<Map<String, Object>> aiData = aiCallLogMapper.aggregateDaily(startTime, endTime);

            for (Map<String, Object> data : aiData) {
                AiCallStatsDaily stats = new AiCallStatsDaily();
                stats.setStatDate(yesterday);
                stats.setServiceName((String) data.get("service_name"));
                stats.setTotalCalls(((Number) data.get("total_calls")).intValue());
                stats.setSuccessCalls(((Number) data.get("success_calls")).intValue());
                stats.setFailedCalls(((Number) data.get("failed_calls")).intValue());
                stats.setAvgResponseTimeMs(((Number) data.get("avg_response_time_ms")).intValue());
                stats.setMaxResponseTimeMs(((Number) data.get("max_response_time_ms")).intValue());

                Object inputTokens = data.get("total_input_tokens");
                stats.setTotalInputTokens(inputTokens != null ? ((Number) inputTokens).longValue() : 0L);

                Object outputTokens = data.get("total_output_tokens");
                stats.setTotalOutputTokens(outputTokens != null ? ((Number) outputTokens).longValue() : 0L);

                stats.setCreatedTime(LocalDateTime.now());

                aiCallStatsDailyMapper.insert(stats);
            }

            log.info("聚合AI调用日统计完成: {}", aiData.size());
        } catch (Exception e) {
            log.error("聚合AI调用日统计失败", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupOldLogs() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            int deleted = accessLogMapper.deleteOlderThan(cutoff);
            log.info("清理访问日志: {} 条记录", deleted);
        } catch (Exception e) {
            log.error("清理访问日志失败", e);
        }
    }
}
