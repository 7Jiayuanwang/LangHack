package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.BackupOperationLog;
import com.english.mapper.BackupOperationLogMapper;
import com.english.service.impl.BackupOperationLogServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BackupOperationLogServiceImpl.
 *
 * Key observations from source code:
 * - BackupOperationLogServiceImpl extends ServiceImpl<BackupOperationLogMapper, BackupOperationLog>
 * - log() is fire-and-forget: catches all exceptions, never throws
 * - log() truncates detail to 1000 chars (997 + "...")
 * - log() defaults operator to "UNKNOWN" if null
 * - log() defaults status to "SUCCESS" if null
 * - getLogsByPage() clamps page min to 1, size min to 10, size max to 100
 * - getLogsByPage() optionally filters by operationType
 */
@ExtendWith(MockitoExtension.class)
class BackupOperationLogServiceTest {

    @InjectMocks
    private BackupOperationLogServiceImpl logService;

    @Mock
    private BackupOperationLogMapper baseMapper;

    @Captor
    private ArgumentCaptor<BackupOperationLog> logCaptor;

    @BeforeEach
    void setUp() {
        // Reset state before each test
    }

    // ========== log() tests ==========

    @Test
    void log_normalParameters_savesCorrectly() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup_001.sql",
                "Manual backup created", "SUCCESS", "192.168.1.1");

        verify(baseMapper).insert(logCaptor.capture());
        BackupOperationLog saved = logCaptor.getValue();
        assertEquals("admin", saved.getOperator());
        assertEquals("BACKUP_CREATE", saved.getOperationType());
        assertEquals(1L, saved.getTargetId());
        assertEquals("backup_001.sql", saved.getTargetName());
        assertEquals("Manual backup created", saved.getDetail());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("192.168.1.1", saved.getIpAddress());
        assertNotNull(saved.getCreatedTime());
    }

    @Test
    void log_nullOperator_defaultsToUnknown() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log(null, "BACKUP_CREATE", 1L, "backup.sql", "detail", "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertEquals("UNKNOWN", logCaptor.getValue().getOperator());
    }

    @Test
    void log_nullStatus_defaultsToSuccess() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", "detail", null, null);

        verify(baseMapper).insert(logCaptor.capture());
        assertEquals("SUCCESS", logCaptor.getValue().getStatus());
    }

    @Test
    void log_detailExceeds1000Chars_truncatedTo997PlusEllipsis() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        String longDetail = "x".repeat(1500);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", longDetail, "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        String savedDetail = logCaptor.getValue().getDetail();
        assertEquals(1000, savedDetail.length());
        assertTrue(savedDetail.endsWith("..."));
        assertEquals("x".repeat(997) + "...", savedDetail);
    }

    @Test
    void log_detailExactly1000Chars_notTruncated() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        String exactDetail = "x".repeat(1000);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", exactDetail, "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertEquals(1000, logCaptor.getValue().getDetail().length());
        assertFalse(logCaptor.getValue().getDetail().endsWith("..."));
    }

    @Test
    void log_detailExactly1001Chars_truncated() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        String detail1001 = "x".repeat(1001);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", detail1001, "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertEquals(1000, logCaptor.getValue().getDetail().length());
        assertTrue(logCaptor.getValue().getDetail().endsWith("..."));
    }

    @Test
    void log_nullDetail_savedAsNull() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", null, "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertNull(logCaptor.getValue().getDetail());
    }

    @Test
    void log_saveThrowsException_swallowedNeverThrows() {
        when(baseMapper.insert(any(BackupOperationLog.class)))
                .thenThrow(new RuntimeException("DB connection failed"));

        // Should not throw - fire-and-forget
        assertDoesNotThrow(() ->
                logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", "detail", "SUCCESS", null)
        );

        verify(baseMapper).insert(any(BackupOperationLog.class));
    }

    @Test
    void log_nullTargetIdAndName_savedAsNull() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log("SYSTEM", "BACKUP_CLEANUP", null, null, "Cleanup completed", "SUCCESS", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertNull(logCaptor.getValue().getTargetId());
        assertNull(logCaptor.getValue().getTargetName());
    }

    @Test
    void log_createdTimeSetToNow() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        logService.log("admin", "BACKUP_CREATE", 1L, "backup.sql", "detail", "SUCCESS", null);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        verify(baseMapper).insert(logCaptor.capture());
        LocalDateTime createdTime = logCaptor.getValue().getCreatedTime();
        assertNotNull(createdTime);
        assertTrue(createdTime.isAfter(before) || createdTime.isEqual(before));
        assertTrue(createdTime.isBefore(after) || createdTime.isEqual(after));
    }

    @Test
    void log_failedStatus_savedCorrectly() {
        when(baseMapper.insert(any(BackupOperationLog.class))).thenReturn(1);

        logService.log("SYSTEM", "BACKUP_SCHEDULED", 5L, "backup_005.sql",
                "Scheduled backup failed", "FAILED", null);

        verify(baseMapper).insert(logCaptor.capture());
        assertEquals("FAILED", logCaptor.getValue().getStatus());
    }

    // ========== getLogsByPage() tests ==========

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_normalParameters_returnsPage() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupOperationLog> result = logService.getLogsByPage(1, 20, null);

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_withOperationTypeFilter_appliesFilter() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupOperationLog> result = logService.getLogsByPage(1, 20, "BACKUP_CREATE");

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_nullOperationType_noFilter() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupOperationLog> result = logService.getLogsByPage(1, 10, null);

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_emptyOperationType_noFilter() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        IPage<BackupOperationLog> result = logService.getLogsByPage(1, 10, "");

        assertNotNull(result);
        verify(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_pageLessThan1_clampsTo1() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        logService.getLogsByPage(0, 10, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupOperationLog> p = (Page<BackupOperationLog>) page;
            return p.getCurrent() == 1;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_negativePage_clampsTo1() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        logService.getLogsByPage(-5, 10, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupOperationLog> p = (Page<BackupOperationLog>) page;
            return p.getCurrent() == 1;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_sizeLessThan1_clampsTo10() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        logService.getLogsByPage(1, 0, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupOperationLog> p = (Page<BackupOperationLog>) page;
            return p.getSize() == 10;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_sizeGreaterThan100_clampsTo100() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        logService.getLogsByPage(1, 500, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupOperationLog> p = (Page<BackupOperationLog>) page;
            return p.getSize() == 100;
        }), any(QueryWrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLogsByPage_sizeExactly100_notClamped() {
        IPage<BackupOperationLog> mockPage = mock(IPage.class);
        doReturn(mockPage).when(baseMapper).selectPage(any(Page.class), any(QueryWrapper.class));

        logService.getLogsByPage(1, 100, null);

        verify(baseMapper).selectPage(argThat(page -> {
            Page<BackupOperationLog> p = (Page<BackupOperationLog>) page;
            return p.getSize() == 100;
        }), any(QueryWrapper.class));
    }
}
