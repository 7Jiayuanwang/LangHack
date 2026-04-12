package com.english.service;

import com.english.entity.SyncLog;
import com.english.mapper.SyncLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 同步监控服务 - 负责同步健康检查、告警生成和状态监控
 * <p>
 * 告警级别:
 * - INFO: 正常同步完成
 * - WARNING: 同步存在少量失败或延迟
 * - ERROR: 同步大量失败或数据异常
 * - CRITICAL: 同步长时间未执行或完全失败
 */
@Service
public class SyncMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(SyncMonitorService.class);

    /** 数据源列表 */
    private static final List<String> DATA_SOURCES = List.of(
            "administrative_class", "school_course", "teaching_class",
            "course_schedule", "course_selection", "level_exam_scores",
            "teaching_plans"
    );

    /** 告警阈值配置 */
    private static final int WARNING_FAIL_RATE = 10;    // 失败率 > 10% 触发 WARNING
    private static final int ERROR_FAIL_RATE = 30;      // 失败率 > 30% 触发 ERROR
    private static final int STALE_HOURS_WARNING = 24;  // 超过24小时未同步触发 WARNING
    private static final int STALE_HOURS_CRITICAL = 72; // 超过72小时未同步触发 CRITICAL
    private static final int ZERO_RECORD_WARNING = 3;   // 连续3次零记录触发 WARNING

    private final SyncLogMapper syncLogMapper;

    public SyncMonitorService(SyncLogMapper syncLogMapper) {
        this.syncLogMapper = syncLogMapper;
    }

    /**
     * 检查所有数据源的同步健康状况
     *
     * @return 各数据源的健康状态及告警列表
     */
    public Map<String, Object> checkSyncHealth() {
        Map<String, Object> healthReport = new LinkedHashMap<>();
        List<Map<String, Object>> alerts = new ArrayList<>();
        int healthyCount = 0;
        int warningCount = 0;
        int errorCount = 0;
        int criticalCount = 0;

        for (String dataSource : DATA_SOURCES) {
            Map<String, Object> sourceHealth = checkDataSourceHealth(dataSource);
            String level = (String) sourceHealth.get("alertLevel");
            healthReport.put(dataSource, sourceHealth);

            switch (level) {
                case "INFO":
                    healthyCount++;
                    break;
                case "WARNING":
                    warningCount++;
                    alerts.add(sourceHealth);
                    break;
                case "ERROR":
                    errorCount++;
                    alerts.add(sourceHealth);
                    break;
                case "CRITICAL":
                    criticalCount++;
                    alerts.add(sourceHealth);
                    break;
            }
        }

        // 汇总信息
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSources", DATA_SOURCES.size());
        summary.put("healthyCount", healthyCount);
        summary.put("warningCount", warningCount);
        summary.put("errorCount", errorCount);
        summary.put("criticalCount", criticalCount);
        summary.put("overallStatus", criticalCount > 0 ? "CRITICAL"
                : errorCount > 0 ? "ERROR"
                : warningCount > 0 ? "WARNING" : "HEALTHY");
        summary.put("checkedAt", LocalDateTime.now());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("sources", healthReport);
        result.put("alerts", alerts);

        return result;
    }

    /**
     * 检查单个数据源的健康状况
     */
    private Map<String, Object> checkDataSourceHealth(String dataSource) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("dataSource", dataSource);

        SyncLog latestLog = syncLogMapper.selectLatestByDataSource(dataSource);

        if (latestLog == null) {
            // 从未同步过
            health.put("status", "NEVER_SYNCED");
            health.put("alertLevel", "CRITICAL");
            health.put("message", "该数据源从未执行过同步");
            health.put("lastSyncTime", null);
            return health;
        }

        health.put("lastSyncTime", latestLog.getCreatedAt());
        health.put("lastSyncStatus", latestLog.getStatus());
        health.put("lastSyncType", latestLog.getSyncType());
        health.put("totalCount", latestLog.getTotalCount());
        health.put("successCount", latestLog.getSuccessCount());
        health.put("failCount", latestLog.getFailCount());

        // 1. 检查同步状态
        if ("FAILED".equals(latestLog.getStatus())) {
            health.put("status", "SYNC_FAILED");
            health.put("alertLevel", "ERROR");
            health.put("message", "最近一次同步失败: " + truncate(latestLog.getErrorMessage(), 100));
            return health;
        }

        if ("RUNNING".equals(latestLog.getStatus())) {
            // 检查是否卡住
            if (latestLog.getStartTime() != null) {
                long runningMinutes = Duration.between(latestLog.getStartTime(), LocalDateTime.now()).toMinutes();
                if (runningMinutes > 120) {
                    health.put("status", "SYNC_STUCK");
                    health.put("alertLevel", "CRITICAL");
                    health.put("message", "同步已运行超过2小时，可能卡住");
                    return health;
                }
            }
            health.put("status", "SYNC_RUNNING");
            health.put("alertLevel", "INFO");
            health.put("message", "同步正在运行中");
            return health;
        }

        // 2. 检查同步时效性
        if (latestLog.getCreatedAt() != null) {
            long hoursSinceSync = Duration.between(latestLog.getCreatedAt(), LocalDateTime.now()).toHours();
            if (hoursSinceSync > STALE_HOURS_CRITICAL) {
                health.put("status", "STALE_CRITICAL");
                health.put("alertLevel", "CRITICAL");
                health.put("message", String.format("超过%d小时未同步", hoursSinceSync));
                return health;
            }
            if (hoursSinceSync > STALE_HOURS_WARNING) {
                health.put("status", "STALE_WARNING");
                health.put("alertLevel", "WARNING");
                health.put("message", String.format("超过%d小时未同步", hoursSinceSync));
            }
        }

        // 3. 检查失败率
        int totalCount = latestLog.getTotalCount() != null ? latestLog.getTotalCount() : 0;
        int failCount = latestLog.getFailCount() != null ? latestLog.getFailCount() : 0;

        if (totalCount > 0) {
            double failRate = (failCount * 100.0) / totalCount;
            if (failRate > ERROR_FAIL_RATE) {
                health.put("status", "HIGH_FAIL_RATE");
                health.put("alertLevel", "ERROR");
                health.put("message", String.format("失败率 %.1f%% (%d/%d)", failRate, failCount, totalCount));
                return health;
            }
            if (failRate > WARNING_FAIL_RATE) {
                health.put("status", "MODERATE_FAIL_RATE");
                health.put("alertLevel", "WARNING");
                health.put("message", String.format("失败率 %.1f%% (%d/%d)", failRate, failCount, totalCount));
            }
        }

        // 4. 检查零记录同步
        if (totalCount == 0 && "SUCCESS".equals(latestLog.getStatus())) {
            int consecutiveZeroCount = countConsecutiveZeroSyncs(dataSource);
            if (consecutiveZeroCount >= ZERO_RECORD_WARNING) {
                health.put("status", "ZERO_RECORDS");
                health.put("alertLevel", "WARNING");
                health.put("message", String.format("连续%d次同步零记录", consecutiveZeroCount));
                return health;
            }
        }

        // 健康
        if (!health.containsKey("status")) {
            health.put("status", "HEALTHY");
            health.put("alertLevel", "INFO");
            health.put("message", "同步正常");
        }

        return health;
    }

    /**
     * 获取最近的告警列表
     *
     * @param limit 最大返回数量
     * @return 告警列表
     */
    public List<Map<String, Object>> getRecentAlerts(int limit) {
        List<Map<String, Object>> allAlerts = new ArrayList<>();

        for (String dataSource : DATA_SOURCES) {
            Map<String, Object> health = checkDataSourceHealth(dataSource);
            String alertLevel = (String) health.get("alertLevel");

            if (!"INFO".equals(alertLevel)) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("dataSource", dataSource);
                alert.put("level", alertLevel);
                alert.put("status", health.get("status"));
                alert.put("message", health.get("message"));
                alert.put("lastSyncTime", health.get("lastSyncTime"));
                alert.put("detectedAt", LocalDateTime.now());
                allAlerts.add(alert);
            }
        }

        // 按严重程度排序: CRITICAL > ERROR > WARNING
        allAlerts.sort((a, b) -> {
            int levelA = alertLevelOrder((String) a.get("level"));
            int levelB = alertLevelOrder((String) b.get("level"));
            return levelA - levelB;
        });

        return allAlerts.size() > limit ? allAlerts.subList(0, limit) : allAlerts;
    }

    /**
     * 获取指定数据源的同步历史
     *
     * @param dataSource 数据源名称
     * @param limit      最大返回数量
     * @return 同步日志列表
     */
    public List<SyncLog> getSyncHistory(String dataSource, int limit) {
        return syncLogMapper.selectRecentByDataSource(dataSource, limit);
    }

    // =========================================================================
    // 辅助方法
    // =========================================================================

    /**
     * 统计连续零记录同步次数
     */
    private int countConsecutiveZeroSyncs(String dataSource) {
        List<SyncLog> recentLogs = syncLogMapper.selectRecentByDataSource(dataSource, 10);
        int count = 0;
        for (SyncLog log : recentLogs) {
            if (log.getTotalCount() != null && log.getTotalCount() == 0 && "SUCCESS".equals(log.getStatus())) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 告警级别排序值（越小越严重）
     */
    private int alertLevelOrder(String level) {
        switch (level) {
            case "CRITICAL":
                return 0;
            case "ERROR":
                return 1;
            case "WARNING":
                return 2;
            case "INFO":
                return 3;
            default:
                return 4;
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
