package com.english.controller;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.BackupOperationLog;
import com.english.entity.BackupRecord;
import com.english.exception.BusinessException;
import com.english.service.BackupOperationLogService;
import com.english.service.BackupService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * BackupController unit tests.
 *
 * Covers:
 * - GET    /api/ops/backup/list       (paginated backup list)
 * - POST   /api/ops/backup/create     (create manual backup)
 * - GET    /api/ops/backup/{id}       (backup detail)
 * - DELETE /api/ops/backup/{id}       (delete backup)
 * - POST   /api/ops/backup/{id}/restore (restore from backup)
 * - GET    /api/ops/backup/stats      (backup statistics)
 * - GET    /api/ops/backup/config     (backup configuration)
 * - POST   /api/ops/backup/cleanup    (trigger cleanup)
 * - GET    /api/ops/backup/logs       (operation logs)
 *
 * All endpoints require ADMIN role.
 */
@ExtendWith(MockitoExtension.class)
class BackupControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private BackupService backupService;

    @Mock
    private BackupOperationLogService operationLogService;

    @InjectMocks
    private BackupController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();
        // BackupController requires hasRole('ADMIN')
        TestHelper.createMockAuthentication("admin1", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== Helper ==========

    private BackupRecord createMockBackupRecord(Long id, String filename) {
        BackupRecord record = new BackupRecord();
        record.setId(id);
        record.setFilename(filename);
        record.setFileSize(1024L);
        record.setBackupType("MANUAL");
        record.setBackupMode("FULL");
        record.setStatus("COMPLETED");
        record.setCreatedBy("admin1");
        record.setCreatedTime(LocalDateTime.now());
        return record;
    }

    // ========== GET /api/ops/backup/list ==========

    @Test
    @DisplayName("GET /api/ops/backup/list - success with default params")
    void getBackupList_success() throws Exception {
        IPage<BackupRecord> page = new Page<>(1, 10);
        when(backupService.getBackupsByPage(1, 10, null, null)).thenReturn(page);

        mockMvc.perform(get("/api/ops/backup/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(backupService).getBackupsByPage(1, 10, null, null);
    }

    @Test
    @DisplayName("GET /api/ops/backup/list - success with filters")
    void getBackupList_withFilters() throws Exception {
        IPage<BackupRecord> page = new Page<>(2, 5);
        when(backupService.getBackupsByPage(2, 5, "MANUAL", "COMPLETED")).thenReturn(page);

        mockMvc.perform(get("/api/ops/backup/list")
                        .param("page", "2")
                        .param("size", "5")
                        .param("backupType", "MANUAL")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(backupService).getBackupsByPage(2, 5, "MANUAL", "COMPLETED");
    }

    @Test
    @DisplayName("GET /api/ops/backup/list - BusinessException returns error message")
    void getBackupList_businessException_returnsError() throws Exception {
        when(backupService.getBackupsByPage(anyInt(), anyInt(), any(), any()))
                .thenThrow(new BusinessException("查询参数无效"));

        mockMvc.perform(get("/api/ops/backup/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询参数无效"));
    }

    @Test
    @DisplayName("GET /api/ops/backup/list - RuntimeException returns generic error")
    void getBackupList_runtimeException_returnsGenericError() throws Exception {
        when(backupService.getBackupsByPage(anyInt(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/ops/backup/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取备份列表失败，请稍后重试"));
    }

    // ========== POST /api/ops/backup/create ==========

    @Test
    @DisplayName("POST /api/ops/backup/create - success with FULL mode (default)")
    void createBackup_success_fullMode() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup_2026_03_16.sql.gz");
        when(backupService.createManualBackup("admin1", "FULL")).thenReturn(record);

        mockMvc.perform(post("/api/ops/backup/create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("备份任务已创建"));

        verify(backupService).createManualBackup("admin1", "FULL");
        verify(operationLogService).log(eq("admin1"), eq("BACKUP_CREATE"), eq(1L),
                eq("backup_2026_03_16.sql.gz"), contains("全量"), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/create - success with INCREMENTAL mode")
    void createBackup_success_incrementalMode() throws Exception {
        BackupRecord record = createMockBackupRecord(2L, "backup_incr.sql.gz");
        when(backupService.createManualBackup("admin1", "INCREMENTAL")).thenReturn(record);

        mockMvc.perform(post("/api/ops/backup/create").param("mode", "INCREMENTAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("备份任务已创建"));

        verify(backupService).createManualBackup("admin1", "INCREMENTAL");
        verify(operationLogService).log(eq("admin1"), eq("BACKUP_CREATE"), eq(2L),
                eq("backup_incr.sql.gz"), contains("增量"), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/create - invalid mode returns error")
    void createBackup_invalidMode_returnsError() throws Exception {
        mockMvc.perform(post("/api/ops/backup/create").param("mode", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无效的备份模式，仅支持 FULL 或 INCREMENTAL"));

        verify(backupService, never()).createManualBackup(anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/create - BusinessException returns error and logs")
    void createBackup_businessException_returnsError() throws Exception {
        when(backupService.createManualBackup("admin1", "FULL"))
                .thenThrow(new BusinessException("备份目录不存在"));

        mockMvc.perform(post("/api/ops/backup/create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("备份目录不存在"));

        verify(operationLogService).log(eq("admin1"), eq("BACKUP_CREATE"), isNull(),
                isNull(), contains("失败"), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/create - RuntimeException returns generic error")
    void createBackup_runtimeException_returnsGenericError() throws Exception {
        when(backupService.createManualBackup("admin1", "FULL"))
                .thenThrow(new RuntimeException("IO error"));

        mockMvc.perform(post("/api/ops/backup/create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建备份失败，请稍后重试"));
    }

    // ========== GET /api/ops/backup/{id} ==========

    @Test
    @DisplayName("GET /api/ops/backup/{id} - success")
    void getBackupDetail_success() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);

        mockMvc.perform(get("/api/ops/backup/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.filename").value("backup.sql.gz"));
    }

    @Test
    @DisplayName("GET /api/ops/backup/{id} - not found returns BusinessException error")
    void getBackupDetail_notFound_returnsError() throws Exception {
        when(backupService.getBackupById(99L)).thenThrow(new BusinessException("备份记录不存在"));

        mockMvc.perform(get("/api/ops/backup/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("备份记录不存在"));
    }

    @Test
    @DisplayName("GET /api/ops/backup/{id} - RuntimeException returns generic error")
    void getBackupDetail_runtimeException_returnsGenericError() throws Exception {
        when(backupService.getBackupById(1L)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/ops/backup/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取备份详情失败，请稍后重试"));
    }

    // ========== DELETE /api/ops/backup/{id} ==========

    @Test
    @DisplayName("DELETE /api/ops/backup/{id} - success")
    void deleteBackup_success() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);

        mockMvc.perform(delete("/api/ops/backup/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("备份已删除"));

        verify(backupService).deleteBackup(1L);
        verify(operationLogService).log(eq("admin1"), eq("BACKUP_DELETE"), eq(1L),
                eq("backup.sql.gz"), eq("删除备份文件"), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("DELETE /api/ops/backup/{id} - not found returns BusinessException error")
    void deleteBackup_notFound_returnsError() throws Exception {
        when(backupService.getBackupById(99L)).thenThrow(new BusinessException("备份记录不存在"));

        mockMvc.perform(delete("/api/ops/backup/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("备份记录不存在"));

        verify(operationLogService).log(eq("admin1"), eq("BACKUP_DELETE"), eq(99L),
                isNull(), contains("失败"), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("DELETE /api/ops/backup/{id} - RuntimeException returns generic error")
    void deleteBackup_runtimeException_returnsGenericError() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);
        doThrow(new RuntimeException("IO error")).when(backupService).deleteBackup(1L);

        mockMvc.perform(delete("/api/ops/backup/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("删除备份失败，请稍后重试"));
    }

    // ========== POST /api/ops/backup/{id}/restore ==========

    @Test
    @DisplayName("POST /api/ops/backup/{id}/restore - success")
    void restoreBackup_success() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);

        Map<String, String> body = Map.of("confirmDatabaseName", "english_db");

        mockMvc.perform(post("/api/ops/backup/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("数据库恢复成功"));

        verify(backupService).restoreFromBackup(1L, "english_db");
        verify(operationLogService).log(eq("admin1"), eq("BACKUP_RESTORE"), eq(1L),
                eq("backup.sql.gz"), contains("english_db"), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/{id}/restore - missing confirmDatabaseName returns error")
    void restoreBackup_missingConfirmName_returnsError() throws Exception {
        Map<String, String> body = new HashMap<>();
        // confirmDatabaseName is null

        mockMvc.perform(post("/api/ops/backup/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请输入数据库名称以确认恢复操作"));

        verify(backupService, never()).restoreFromBackup(anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/{id}/restore - empty confirmDatabaseName returns error")
    void restoreBackup_emptyConfirmName_returnsError() throws Exception {
        Map<String, String> body = Map.of("confirmDatabaseName", "   ");

        mockMvc.perform(post("/api/ops/backup/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请输入数据库名称以确认恢复操作"));
    }

    @Test
    @DisplayName("POST /api/ops/backup/{id}/restore - BusinessException returns error")
    void restoreBackup_businessException_returnsError() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);
        doThrow(new BusinessException("数据库名称不匹配"))
                .when(backupService).restoreFromBackup(1L, "wrong_db");

        Map<String, String> body = Map.of("confirmDatabaseName", "wrong_db");

        mockMvc.perform(post("/api/ops/backup/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("数据库名称不匹配"));

        verify(operationLogService).log(eq("admin1"), eq("BACKUP_RESTORE"), eq(1L),
                eq("backup.sql.gz"), contains("失败"), eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/{id}/restore - RuntimeException returns generic error")
    void restoreBackup_runtimeException_returnsGenericError() throws Exception {
        BackupRecord record = createMockBackupRecord(1L, "backup.sql.gz");
        when(backupService.getBackupById(1L)).thenReturn(record);
        doThrow(new RuntimeException("mysqldump error"))
                .when(backupService).restoreFromBackup(eq(1L), anyString());

        Map<String, String> body = Map.of("confirmDatabaseName", "english_db");

        mockMvc.perform(post("/api/ops/backup/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("恢复数据库失败，请稍后重试"));
    }

    // ========== GET /api/ops/backup/stats ==========

    @Test
    @DisplayName("GET /api/ops/backup/stats - success")
    void getBackupStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalBackups", 10, "totalSize", 5242880L);
        when(backupService.getBackupStats()).thenReturn(stats);

        mockMvc.perform(get("/api/ops/backup/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalBackups").value(10))
                .andExpect(jsonPath("$.data.totalSize").value(5242880));
    }

    @Test
    @DisplayName("GET /api/ops/backup/stats - exception returns generic error")
    void getBackupStats_exception_returnsError() throws Exception {
        when(backupService.getBackupStats()).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/ops/backup/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取备份统计失败，请稍后重试"));
    }

    // ========== GET /api/ops/backup/config ==========

    @Test
    @DisplayName("GET /api/ops/backup/config - success")
    void getBackupConfig_success() throws Exception {
        Map<String, Object> config = Map.of("backupDir", "/var/backups", "maxRetention", 30);
        when(backupService.getBackupConfig()).thenReturn(config);

        mockMvc.perform(get("/api/ops/backup/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.backupDir").value("/var/backups"))
                .andExpect(jsonPath("$.data.maxRetention").value(30));
    }

    @Test
    @DisplayName("GET /api/ops/backup/config - exception returns generic error")
    void getBackupConfig_exception_returnsError() throws Exception {
        when(backupService.getBackupConfig()).thenThrow(new RuntimeException("Config error"));

        mockMvc.perform(get("/api/ops/backup/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取备份配置失败，请稍后重试"));
    }

    // ========== POST /api/ops/backup/cleanup ==========

    @Test
    @DisplayName("POST /api/ops/backup/cleanup - success")
    void triggerCleanup_success() throws Exception {
        when(backupService.cleanupOldBackups()).thenReturn(3);

        mockMvc.perform(post("/api/ops/backup/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("清理完成，共删除 3 个过期备份"))
                .andExpect(jsonPath("$.data").value(3));

        verify(operationLogService).log(eq("admin1"), eq("BACKUP_CLEANUP"), isNull(),
                isNull(), contains("3"), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("POST /api/ops/backup/cleanup - BusinessException returns error")
    void triggerCleanup_businessException_returnsError() throws Exception {
        when(backupService.cleanupOldBackups()).thenThrow(new BusinessException("清理失败"));

        mockMvc.perform(post("/api/ops/backup/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("清理失败"));
    }

    @Test
    @DisplayName("POST /api/ops/backup/cleanup - RuntimeException returns generic error")
    void triggerCleanup_runtimeException_returnsGenericError() throws Exception {
        when(backupService.cleanupOldBackups()).thenThrow(new RuntimeException("IO error"));

        mockMvc.perform(post("/api/ops/backup/cleanup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("清理备份失败，请稍后重试"));
    }

    // ========== GET /api/ops/backup/logs ==========

    @Test
    @DisplayName("GET /api/ops/backup/logs - success with default params")
    void getOperationLogs_success() throws Exception {
        IPage<BackupOperationLog> page = new Page<>(1, 10);
        when(operationLogService.getLogsByPage(1, 10, null)).thenReturn(page);

        mockMvc.perform(get("/api/ops/backup/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(operationLogService).getLogsByPage(1, 10, null);
    }

    @Test
    @DisplayName("GET /api/ops/backup/logs - success with operationType filter")
    void getOperationLogs_withFilter() throws Exception {
        IPage<BackupOperationLog> page = new Page<>(1, 20);
        when(operationLogService.getLogsByPage(1, 20, "BACKUP_CREATE")).thenReturn(page);

        mockMvc.perform(get("/api/ops/backup/logs")
                        .param("page", "1")
                        .param("size", "20")
                        .param("operationType", "BACKUP_CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(operationLogService).getLogsByPage(1, 20, "BACKUP_CREATE");
    }

    @Test
    @DisplayName("GET /api/ops/backup/logs - exception returns generic error")
    void getOperationLogs_exception_returnsError() throws Exception {
        when(operationLogService.getLogsByPage(anyInt(), anyInt(), any()))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/ops/backup/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取操作日志失败，请稍后重试"));
    }
}
