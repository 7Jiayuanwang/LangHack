package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.BackupRecord;
import com.english.exception.BusinessException;
import com.english.mapper.BackupRecordMapper;
import com.english.service.impl.BackupServiceImpl;
import com.english.service.BackupConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BackupServiceImpl.
 *
 * Key observations from source code:
 * - BackupServiceImpl extends ServiceImpl<BackupRecordMapper, BackupRecord>
 * - Constructor: @Qualifier("versionGeneratorExecutor") Executor, @Qualifier("masterDataSource") DataSource, BackupOperationLogService
 * - Static AtomicBoolean backupInProgress guards concurrent backup/restore operations
 * - @Value fields: backupEnabled, backupDir, retentionDays, maxCount, mysqldumpPath, incrementalEnabled, fullBackupIntervalDays, jdbcUrl, dbUsername, dbPassword
 * - createManualBackup: checks enabled, count limit, disk space, lock, mode validation, incremental needs full backup
 * - executeScheduledBackup: checks enabled, lock, determines mode, auto-cleanup after
 * - getBackupsByPage: page/size clamping, optional type/status filter
 * - getBackupById: throws BusinessException if null
 * - deleteBackup: prevents deleting IN_PROGRESS, path traversal check, deletes file + record
 * - getBackupFilePath: only SUCCESS status, path traversal check, file existence check
 * - restoreFromBackup: database name confirmation, lock, status check, path traversal check
 * - getBackupStats: total/success count, total size, last backup time, config flags
 * - getBackupConfig: returns config map
 * - cleanupOldBackups: skips if backup in progress, protects latest full backup, retention + max-count cleanup
 */
@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private BackupRecordMapper baseMapper;

    @Mock
    private Executor executor;

    @Mock
    private DataSource masterDataSource;

    @Mock
    private BackupOperationLogService operationLogService;

    @Mock
    private BackupConfigService backupConfigService;

    private BackupServiceImpl backupService;
    private Path testBackupDir;

    @BeforeEach
    void setUp() throws Exception {
        backupService = new BackupServiceImpl(executor, masterDataSource, operationLogService, backupConfigService);

        // Inject the baseMapper required by ServiceImpl
        Field mapperField = findField(backupService.getClass(), "baseMapper");
        if (mapperField == null) {
            // ServiceImpl stores mapper in a field called "baseMapper"
            mapperField = findField(com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class, "baseMapper");
        }
        if (mapperField != null) {
            mapperField.setAccessible(true);
            mapperField.set(backupService, baseMapper);
        }

        // Create the test backup directory so disk space checks work
        testBackupDir = Paths.get(System.getProperty("java.io.tmpdir"), "test-backups-" + System.nanoTime());
        Files.createDirectories(testBackupDir);

        // Set @Value fields via reflection
        setField(backupService, "backupEnabled", true);
        setField(backupService, "backupDir", testBackupDir.toString());
        setField(backupService, "retentionDays", 30);
        setField(backupService, "maxCount", 50);
        setField(backupService, "mysqldumpPath", "mysqldump");
        setField(backupService, "incrementalEnabled", false);
        setField(backupService, "fullBackupIntervalDays", 7);
        setField(backupService, "jdbcUrl", "jdbc:mysql://localhost:3306/english_test?useSSL=false");
        setField(backupService, "dbUsername", "testuser");
        setField(backupService, "dbPassword", "testpass");

        // Reset the static backupInProgress lock
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset the static backupInProgress lock to avoid leaking state
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(false);

        // Clean up test backup directory
        if (testBackupDir != null && Files.exists(testBackupDir)) {
            try {
                Files.walk(testBackupDir)
                     .sorted(java.util.Comparator.reverseOrder())
                     .forEach(p -> {
                         try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                     });
            } catch (IOException ignored) {}
        }
    }

    // ========== createManualBackup() tests ==========

    @Test
    void createManualBackup_backupDisabled_throwsBusinessException() throws Exception {
        setField(backupService, "backupEnabled", false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "FULL"));
        assertEquals("备份功能已禁用", ex.getMessage());
    }

    @Test
    void createManualBackup_countExceedsMaxCount_throwsBusinessException() {
        when(baseMapper.selectCount(any())).thenReturn((long) 50);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "FULL"));
        assertTrue(ex.getMessage().contains("备份数量已达上限"));
    }

    @Test
    void createManualBackup_invalidMode_throwsBusinessException() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "INVALID_MODE"));
        assertTrue(ex.getMessage().contains("无效的备份模式"));
    }

    @Test
    void createManualBackup_concurrentBackup_throwsBusinessException() throws Exception {
        // Simulate another backup in progress
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(true);

        when(baseMapper.selectCount(any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "FULL"));
        assertTrue(ex.getMessage().contains("已有备份任务正在执行"));
    }

    @Test
    void createManualBackup_incrementalModeNoFullBackup_throwsBusinessException() throws Exception {
        setField(backupService, "incrementalEnabled", true);
        lenient().when(baseMapper.selectCount(any())).thenReturn(0L);
        // getOne(wrapper, false) delegates to selectList, not selectOne
        // Default Mockito returns empty list → getOne returns null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "INCREMENTAL"));
        assertTrue(ex.getMessage().contains("尚无全量备份"));
    }

    @Test
    void createManualBackup_fullMode_createsRecordAndSubmitsTask() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(BackupRecord.class))).thenAnswer(invocation -> {
            BackupRecord r = invocation.getArgument(0);
            r.setId(1L);
            return 1;
        });

        BackupRecord result = backupService.createManualBackup("admin", "FULL");

        assertNotNull(result);
        assertEquals("MANUAL", result.getBackupType());
        assertEquals("FULL", result.getBackupMode());
        assertEquals("admin", result.getCreatedBy());
        assertEquals("IN_PROGRESS", result.getStatus());
        verify(baseMapper).insert(any(BackupRecord.class));
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void createManualBackup_incrementalModeWithFullBackupExists_createsRecord() throws Exception {
        setField(backupService, "incrementalEnabled", true);
        when(baseMapper.selectCount(any())).thenReturn(0L);

        // getOne(wrapper, false) delegates to selectList, not selectOne
        BackupRecord lastFull = new BackupRecord();
        lastFull.setId(10L);
        lastFull.setCreatedTime(LocalDateTime.now().minusDays(2));
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(lastFull));
        when(baseMapper.insert(any(BackupRecord.class))).thenAnswer(invocation -> {
            BackupRecord r = invocation.getArgument(0);
            r.setId(2L);
            return 1;
        });

        BackupRecord result = backupService.createManualBackup("admin", "INCREMENTAL");

        assertNotNull(result);
        assertEquals("INCREMENTAL", result.getBackupMode());
        assertEquals("MANUAL", result.getBackupType());
    }

    @Test
    void createManualBackup_saveThrowsException_releasesLockAndThrows() {
        when(baseMapper.selectCount(any())).thenReturn(0L);
        when(baseMapper.insert(any(BackupRecord.class))).thenThrow(new RuntimeException("DB error"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.createManualBackup("admin", "FULL"));
        assertTrue(ex.getMessage().contains("创建备份记录失败"));
    }

    // ========== executeScheduledBackup() tests ==========

    @Test
    void executeScheduledBackup_backupDisabled_skipsExecution() throws Exception {
        setField(backupService, "backupEnabled", false);

        backupService.executeScheduledBackup();

        verify(baseMapper, never()).insert(any(BackupRecord.class));
    }

    @Test
    void executeScheduledBackup_anotherBackupInProgress_skips() throws Exception {
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(true);

        backupService.executeScheduledBackup();

        verify(baseMapper, never()).insert(any(BackupRecord.class));
    }

    // ========== getBackupsByPage() tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_normalParameters_returnsPage() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupRecord> result = backupService.getBackupsByPage(1, 20, null, null);

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_withTypeFilter_appliesFilter() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupRecord> result = backupService.getBackupsByPage(1, 10, "MANUAL", null);

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_withStatusFilter_appliesFilter() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupRecord> result = backupService.getBackupsByPage(1, 10, null, "SUCCESS");

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_pageLessThan1_clampsTo1() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        backupService.getBackupsByPage(0, 10, null, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupRecord> p = (Page<BackupRecord>) page;
            return p.getCurrent() == 1;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_sizeLessThan1_clampsTo10() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        backupService.getBackupsByPage(1, -5, null, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupRecord> p = (Page<BackupRecord>) page;
            return p.getSize() == 10;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getBackupsByPage_sizeGreaterThan100_clampsTo100() {
        IPage<BackupRecord> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        backupService.getBackupsByPage(1, 200, null, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupRecord> p = (Page<BackupRecord>) page;
            return p.getSize() == 100;
        }), any(QueryWrapper.class));
    }

    // ========== getBackupById() tests ==========

    @Test
    void getBackupById_exists_returnsRecord() {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setFilename("backup.sql");
        when(baseMapper.selectById(1L)).thenReturn(record);

        BackupRecord result = backupService.getBackupById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("backup.sql", result.getFilename());
    }

    @Test
    void getBackupById_notExists_throwsBusinessException() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.getBackupById(999L));
        assertEquals("备份记录不存在", ex.getMessage());
    }

    // ========== deleteBackup() tests ==========

    @Test
    void deleteBackup_statusInProgress_throwsBusinessException() {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setStatus("IN_PROGRESS");
        when(baseMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.deleteBackup(1L));
        assertEquals("备份正在执行中，无法删除", ex.getMessage());
    }

    @Test
    void deleteBackup_recordNotFound_throwsBusinessException() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.deleteBackup(999L));
        assertEquals("备份记录不存在", ex.getMessage());
    }

    // ========== getBackupFilePath() tests ==========

    @Test
    void getBackupFilePath_statusNotSuccess_throwsBusinessException() {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setStatus("FAILED");
        when(baseMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.getBackupFilePath(1L));
        assertEquals("仅可下载状态为成功的备份文件", ex.getMessage());
    }

    @Test
    void getBackupFilePath_recordNotFound_throwsBusinessException() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.getBackupFilePath(999L));
        assertEquals("备份记录不存在", ex.getMessage());
    }

    @Test
    void getBackupFilePath_statusInProgress_throwsBusinessException() {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setStatus("IN_PROGRESS");
        when(baseMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.getBackupFilePath(1L));
        assertEquals("仅可下载状态为成功的备份文件", ex.getMessage());
    }

    // ========== restoreFromBackup() tests ==========

    @Test
    void restoreFromBackup_wrongDatabaseName_throwsBusinessException() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.restoreFromBackup(1L, "wrong_db_name"));
        assertEquals("数据库名称确认不匹配，请确认后重新输入", ex.getMessage());
    }

    @Test
    void restoreFromBackup_concurrentOperation_throwsBusinessException() throws Exception {
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.restoreFromBackup(1L, "english_test"));
        assertTrue(ex.getMessage().contains("已有备份或恢复任务正在执行"));
    }

    @Test
    void restoreFromBackup_statusNotSuccess_throwsBusinessException() {
        BackupRecord record = new BackupRecord();
        record.setId(1L);
        record.setStatus("FAILED");
        when(baseMapper.selectById(1L)).thenReturn(record);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.restoreFromBackup(1L, "english_test"));
        assertEquals("仅可从成功的备份中恢复", ex.getMessage());
    }

    @Test
    void restoreFromBackup_recordNotFound_throwsBusinessException() {
        when(baseMapper.selectById(999L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> backupService.restoreFromBackup(999L, "english_test"));
        assertEquals("备份记录不存在", ex.getMessage());
    }

    // ========== getBackupStats() tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void getBackupStats_returnsCorrectStats() {
        // count() and count(wrapper) both call selectCount with non-null wrappers in MyBatis Plus.
        // First call = total count, second call = success count.
        lenient().when(baseMapper.selectCount(any())).thenReturn(10L, 8L);
        // total size (getMap -> selectMaps)
        Map<String, Object> sizeMap = new HashMap<>();
        sizeMap.put("file_size", 1024000L);
        lenient().when(baseMapper.selectMaps(any(QueryWrapper.class))).thenReturn(Collections.singletonList(sizeMap));
        // last backup (getOne -> selectOne)
        BackupRecord lastBackup = new BackupRecord();
        lastBackup.setCreatedTime(LocalDateTime.of(2026, 3, 15, 10, 0));
        lenient().when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(lastBackup);

        Map<String, Object> stats = backupService.getBackupStats();

        assertNotNull(stats);
        assertTrue(stats.containsKey("totalCount"));
        assertTrue(stats.containsKey("successCount"));
        assertTrue(stats.containsKey("totalSize"));
        assertTrue(stats.containsKey("lastBackupTime"));
        assertTrue(stats.containsKey("backupEnabled"));
        assertTrue(stats.containsKey("incrementalEnabled"));
        assertEquals(true, stats.get("backupEnabled"));
    }

    // ========== getBackupConfig() tests ==========

    @Test
    void getBackupConfig_returnsAllConfigFields() {
        Map<String, Object> config = backupService.getBackupConfig();

        assertNotNull(config);
        assertEquals(true, config.get("enabled"));
        assertEquals(30, config.get("retentionDays"));
        assertEquals(50, config.get("maxCount"));
        assertEquals(false, config.get("incrementalEnabled"));
        assertEquals(7, config.get("fullBackupIntervalDays"));
    }

    @Test
    void getBackupConfig_disabledBackup_returnsCorrectFlag() throws Exception {
        setField(backupService, "backupEnabled", false);
        setField(backupService, "incrementalEnabled", true);

        Map<String, Object> config = backupService.getBackupConfig();

        assertEquals(false, config.get("enabled"));
        assertEquals(true, config.get("incrementalEnabled"));
    }

    // ========== cleanupOldBackups() tests ==========

    @Test
    void cleanupOldBackups_backupInProgress_returnsZero() throws Exception {
        Field lockField = BackupServiceImpl.class.getDeclaredField("backupInProgress");
        lockField.setAccessible(true);
        ((AtomicBoolean) lockField.get(null)).set(true);

        int result = backupService.cleanupOldBackups();

        assertEquals(0, result);
        verify(baseMapper, never()).delete(any());
    }

    @Test
    void cleanupOldBackups_noOldBackups_returnsZero() {
        // getLastSuccessfulFullBackup returns null
        lenient().when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        // old backups query returns empty
        lenient().when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        // total count within limit - count() uses emptyWrapper (non-null), not null
        lenient().when(baseMapper.selectCount(any())).thenReturn(5L);

        int result = backupService.cleanupOldBackups();

        assertEquals(0, result);
    }

    // ========== Utility methods ==========

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        if (field != null) {
            field.setAccessible(true);
            field.set(target, value);
        }
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
