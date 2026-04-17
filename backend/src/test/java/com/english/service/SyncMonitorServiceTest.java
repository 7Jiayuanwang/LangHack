package com.english.service;

import com.english.entity.SyncLog;
import com.english.mapper.SyncLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SyncMonitorService.
 *
 * Key implementation facts:
 * - 4-level alerts: INFO, WARNING, ERROR, CRITICAL
 * - Health checks: sync status, staleness, fail rate, zero records
 * - Thresholds: WARNING fail rate > 10%, ERROR fail rate > 30%
 *   STALE WARNING > 24h, STALE CRITICAL > 72h
 *   Zero record WARNING >= 3 consecutive
 */
@ExtendWith(MockitoExtension.class)
class SyncMonitorServiceTest {

    private SyncMonitorService monitorService;

    @Mock
    private SyncLogMapper syncLogMapper;

    @BeforeEach
    void setUp() {
        monitorService = new SyncMonitorService(syncLogMapper);
    }

    // ==================== checkSyncHealth tests ====================

    @Nested
    @DisplayName("checkSyncHealth 同步健康检查")
    class CheckSyncHealthTests {

        @Test
        @DisplayName("所有数据源从未同步时返回CRITICAL状态")
        void checkSyncHealth_neverSynced_returnsCritical() {
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("CRITICAL", summary.get("overallStatus"));
            assertEquals(7, summary.get("criticalCount"));
            assertEquals(0, summary.get("healthyCount"));
        }

        @Test
        @DisplayName("最近同步失败返回ERROR状态")
        void checkSyncHealth_recentFailure_returnsError() {
            SyncLog failedLog = new SyncLog();
            failedLog.setStatus("FAILED");
            failedLog.setErrorMessage("连接超时");
            failedLog.setCreatedAt(LocalDateTime.now());
            failedLog.setTotalCount(0);
            failedLog.setFailCount(0);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(failedLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("ERROR", summary.get("overallStatus"));
            assertEquals(7, summary.get("errorCount"));
        }

        @Test
        @DisplayName("最近同步成功返回HEALTHY状态")
        void checkSyncHealth_recentSuccess_returnsHealthy() {
            SyncLog successLog = new SyncLog();
            successLog.setStatus("SUCCESS");
            successLog.setCreatedAt(LocalDateTime.now());
            successLog.setTotalCount(100);
            successLog.setFailCount(0);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(successLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("HEALTHY", summary.get("overallStatus"));
            assertEquals(7, summary.get("healthyCount"));
        }

        @Test
        @DisplayName("超过24小时未同步返回WARNING状态")
        void checkSyncHealth_stale24h_returnsWarning() {
            SyncLog staleLog = new SyncLog();
            staleLog.setStatus("SUCCESS");
            staleLog.setCreatedAt(LocalDateTime.now().minusHours(30));
            staleLog.setTotalCount(50);
            staleLog.setFailCount(0);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(staleLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("WARNING", summary.get("overallStatus"));
        }

        @Test
        @DisplayName("失败率超过30%返回ERROR状态")
        void checkSyncHealth_highFailRate_returnsError() {
            SyncLog highFailLog = new SyncLog();
            highFailLog.setStatus("SUCCESS");
            highFailLog.setCreatedAt(LocalDateTime.now());
            highFailLog.setTotalCount(100);
            highFailLog.setFailCount(35);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(highFailLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("ERROR", summary.get("overallStatus"));
        }

        @Test
        @DisplayName("失败率10%-30%之间返回WARNING状态")
        void checkSyncHealth_moderateFailRate_returnsWarning() {
            SyncLog moderateFailLog = new SyncLog();
            moderateFailLog.setStatus("SUCCESS");
            moderateFailLog.setCreatedAt(LocalDateTime.now());
            moderateFailLog.setTotalCount(100);
            moderateFailLog.setFailCount(15);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(moderateFailLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("WARNING", summary.get("overallStatus"));
        }

        @Test
        @DisplayName("同步正在运行中返回INFO状态")
        void checkSyncHealth_running_returnsInfo() {
            SyncLog runningLog = new SyncLog();
            runningLog.setStatus("RUNNING");
            runningLog.setStartTime(LocalDateTime.now().minusMinutes(5));
            runningLog.setCreatedAt(LocalDateTime.now());

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(runningLog);

            Map<String, Object> result = monitorService.checkSyncHealth();

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertEquals("HEALTHY", summary.get("overallStatus"));
        }
    }

    // ==================== getRecentAlerts tests ====================

    @Nested
    @DisplayName("getRecentAlerts 获取最近告警")
    class GetRecentAlertsTests {

        @Test
        @DisplayName("所有健康时返回空告警列表")
        void getRecentAlerts_allHealthy_returnsEmptyList() {
            SyncLog healthyLog = new SyncLog();
            healthyLog.setStatus("SUCCESS");
            healthyLog.setCreatedAt(LocalDateTime.now());
            healthyLog.setTotalCount(100);
            healthyLog.setFailCount(0);

            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(healthyLog);

            List<Map<String, Object>> alerts = monitorService.getRecentAlerts(10);

            assertTrue(alerts.isEmpty());
        }

        @Test
        @DisplayName("存在问题时返回告警列表")
        void getRecentAlerts_hasIssues_returnsAlerts() {
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            List<Map<String, Object>> alerts = monitorService.getRecentAlerts(5);

            assertFalse(alerts.isEmpty());
            assertTrue(alerts.size() <= 5);
        }

        @Test
        @DisplayName("告警按严重程度排序")
        void getRecentAlerts_sortedBySeverity() {
            // FAILED log for administrative_class (ERROR level)
            SyncLog failedLog = new SyncLog();
            failedLog.setStatus("FAILED");
            failedLog.setErrorMessage("错误");
            failedLog.setCreatedAt(LocalDateTime.now());
            failedLog.setTotalCount(0);
            failedLog.setFailCount(0);

            // Stale log for school_course (WARNING level - 30h old)
            SyncLog staleLog = new SyncLog();
            staleLog.setStatus("SUCCESS");
            staleLog.setCreatedAt(LocalDateTime.now().minusHours(30));
            staleLog.setTotalCount(50);
            staleLog.setFailCount(0);

            // Never synced for remaining sources (CRITICAL level)
            when(syncLogMapper.selectLatestByDataSource("administrative_class")).thenReturn(failedLog);
            when(syncLogMapper.selectLatestByDataSource("school_course")).thenReturn(staleLog);
            when(syncLogMapper.selectLatestByDataSource("teaching_class")).thenReturn(null);
            when(syncLogMapper.selectLatestByDataSource("course_schedule")).thenReturn(null);
            when(syncLogMapper.selectLatestByDataSource("course_selection")).thenReturn(null);
            when(syncLogMapper.selectLatestByDataSource("level_exam_scores")).thenReturn(null);
            when(syncLogMapper.selectLatestByDataSource("teaching_plans")).thenReturn(null);

            List<Map<String, Object>> alerts = monitorService.getRecentAlerts(10);

            // CRITICAL alerts should come before ERROR, ERROR before WARNING
            if (alerts.size() >= 2) {
                String firstLevel = (String) alerts.get(0).get("level");
                String lastLevel = (String) alerts.get(alerts.size() - 1).get("level");
                assertTrue(alertLevelOrder(firstLevel) <= alertLevelOrder(lastLevel));
            }
        }
    }

    // ==================== getSyncHistory tests ====================

    @Nested
    @DisplayName("getSyncHistory 获取同步历史")
    class GetSyncHistoryTests {

        @Test
        @DisplayName("正确返回同步历史记录")
        void getSyncHistory_returnsRecords() {
            SyncLog log = new SyncLog();
            log.setId(1L);
            log.setDataSource("administrative_class");
            log.setStatus("SUCCESS");

            when(syncLogMapper.selectRecentByDataSource("administrative_class", 10))
                    .thenReturn(List.of(log));

            List<SyncLog> history = monitorService.getSyncHistory("administrative_class", 10);

            assertEquals(1, history.size());
            assertEquals("SUCCESS", history.get(0).getStatus());
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private int alertLevelOrder(String level) {
        switch (level) {
            case "CRITICAL": return 0;
            case "ERROR": return 1;
            case "WARNING": return 2;
            case "INFO": return 3;
            default: return 4;
        }
    }
}
