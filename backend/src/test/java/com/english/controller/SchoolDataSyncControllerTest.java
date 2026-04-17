package com.english.controller;

import com.english.dto.Result;
import com.english.entity.FieldMapping;
import com.english.entity.SyncLog;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import com.english.service.SchoolDataSyncService;
import com.english.service.SyncMonitorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SchoolDataSyncController.
 *
 * Key implementation facts:
 * - All endpoints under /api/admin/sync
 * - @PreAuthorize("hasAnyRole('ADMIN', 'OPS')") on all endpoints
 * - POST for sync operations, GET for status/query
 * - 7 data sources: administrative_class, school_course, teaching_class,
 *   course_schedule, course_selection, level_exam_scores, teaching_plans
 */
@ExtendWith(MockitoExtension.class)
class SchoolDataSyncControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private SchoolDataSyncController controller;

    @Mock
    private SchoolDataSyncService syncService;

    @Mock
    private SchoolDataApiClient apiClient;

    @Mock
    private FieldMappingService fieldMappingService;

    @Mock
    private SyncMonitorService monitorService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ==================== Full Sync tests ====================

    @Nested
    @DisplayName("POST /api/admin/sync/full/{dataSource} 全量同步")
    class FullSyncTests {

        @Test
        @DisplayName("全量同步指定数据源成功")
        void fullSync_success() throws Exception {
            SyncLog syncLog = new SyncLog();
            syncLog.setId(1L);
            syncLog.setStatus("SUCCESS");
            syncLog.setDataSource("administrative_class");

            when(syncService.fullSync(eq("administrative_class"), anyString(), anyString()))
                    .thenReturn(syncLog);

            mockMvc.perform(post("/api/admin/sync/full/administrative_class")
                            .param("triggerType", "MANUAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("全量同步失败返回错误信息")
        void fullSync_failure() throws Exception {
            when(syncService.fullSync(eq("invalid"), anyString(), anyString()))
                    .thenThrow(new RuntimeException("不支持的数据源"));

            mockMvc.perform(post("/api/admin/sync/full/invalid")
                            .param("triggerType", "MANUAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));
        }
    }

    // ==================== Incremental Sync tests ====================

    @Nested
    @DisplayName("POST /api/admin/sync/incremental/{dataSource} 增量同步")
    class IncrementalSyncTests {

        @Test
        @DisplayName("增量同步指定数据源成功")
        void incrementalSync_success() throws Exception {
            SyncLog syncLog = new SyncLog();
            syncLog.setId(2L);
            syncLog.setStatus("SUCCESS");

            when(syncService.incrementalSync(eq("school_course"), anyString(), anyString()))
                    .thenReturn(syncLog);

            mockMvc.perform(post("/api/admin/sync/incremental/school_course")
                            .param("triggerType", "MANUAL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Full Sync All tests ====================

    @Nested
    @DisplayName("POST /api/admin/sync/full-all 全量同步所有数据源")
    class FullSyncAllTests {

        @Test
        @DisplayName("全量同步所有数据源成功")
        void fullSyncAll_success() throws Exception {
            when(syncService.fullSyncAll(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(post("/api/admin/sync/full-all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Status tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/status 同步状态")
    class StatusTests {

        @Test
        @DisplayName("获取同步状态概览成功")
        void getSyncStatus_success() throws Exception {
            when(syncService.getSyncStatusOverview()).thenReturn(new LinkedHashMap<>());

            mockMvc.perform(get("/api/admin/sync/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Health tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/health 健康检查")
    class HealthTests {

        @Test
        @DisplayName("获取健康检查结果成功")
        void getSyncHealth_success() throws Exception {
            when(monitorService.checkSyncHealth()).thenReturn(new LinkedHashMap<>());

            mockMvc.perform(get("/api/admin/sync/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Validate tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/validate 数据校验")
    class ValidateTests {

        @Test
        @DisplayName("数据校验成功")
        void validateData_success() throws Exception {
            when(syncService.validateData()).thenReturn(new LinkedHashMap<>());

            mockMvc.perform(get("/api/admin/sync/validate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Test Connection tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/test-connection 测试连接")
    class TestConnectionTests {

        @Test
        @DisplayName("API连接成功")
        void testConnection_success() throws Exception {
            when(apiClient.testConnection()).thenReturn(true);

            mockMvc.perform(get("/api/admin/sync/test-connection"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(true));
        }

        @Test
        @DisplayName("API连接失败")
        void testConnection_failure() throws Exception {
            when(apiClient.testConnection()).thenReturn(false);

            mockMvc.perform(get("/api/admin/sync/test-connection"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value(false));
        }
    }

    // ==================== Sync Logs tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/logs/{dataSource} 同步日志")
    class SyncLogsTests {

        @Test
        @DisplayName("获取同步日志成功")
        void getSyncLogs_success() throws Exception {
            when(monitorService.getSyncHistory(eq("administrative_class"), anyInt()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/sync/logs/administrative_class")
                            .param("limit", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Alerts tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/alerts 告警列表")
    class AlertsTests {

        @Test
        @DisplayName("获取告警列表成功")
        void getAlerts_success() throws Exception {
            when(monitorService.getRecentAlerts(anyInt()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/sync/alerts")
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    // ==================== Field Mappings tests ====================

    @Nested
    @DisplayName("GET /api/admin/sync/field-mappings/{dataSource} 字段映射")
    class FieldMappingsTests {

        @Test
        @DisplayName("获取字段映射配置成功")
        void getFieldMappings_success() throws Exception {
            when(fieldMappingService.getMappingsByDataSource("administrative_class"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/sync/field-mappings/administrative_class"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
