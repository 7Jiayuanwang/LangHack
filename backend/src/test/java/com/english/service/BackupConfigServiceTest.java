package com.english.service;

import com.english.entity.BackupConfig;
import com.english.exception.BusinessException;
import com.english.mapper.BackupConfigMapper;
import com.english.service.impl.BackupConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BackupConfigServiceImpl.
 *
 * Dependencies:
 * - BackupConfigMapper backupConfigMapper (constructor-injected)
 * - @Value fields: defaultEnabled, defaultRetentionDays, defaultMaxCount,
 *                  defaultIncrementalEnabled, defaultFullBackupIntervalDays
 *
 * Key implementation facts:
 * - getConfig() queries selectById(1); falls back to @Value defaults if null or exception
 * - updateConfig() validates hour/minute/retentionDays/maxCount/fullBackupIntervalDays ranges
 * - updateConfig() auto-generates cron from hour+minute
 * - updateConfig() uses insert-or-update pattern (selectById(1) to decide)
 */
@ExtendWith(MockitoExtension.class)
class BackupConfigServiceTest {

    @InjectMocks
    private BackupConfigServiceImpl backupConfigService;

    @Mock
    private BackupConfigMapper backupConfigMapper;

    @BeforeEach
    void setUp() {
        // Inject @Value defaults via reflection
        ReflectionTestUtils.setField(backupConfigService, "defaultEnabled", true);
        ReflectionTestUtils.setField(backupConfigService, "defaultRetentionDays", 30);
        ReflectionTestUtils.setField(backupConfigService, "defaultMaxCount", 50);
        ReflectionTestUtils.setField(backupConfigService, "defaultIncrementalEnabled", false);
        ReflectionTestUtils.setField(backupConfigService, "defaultFullBackupIntervalDays", 7);
    }

    // ==================== getConfig tests ====================

    @Nested
    @DisplayName("getConfig 获取备份配置")
    class GetConfigTests {

        @Test
        @DisplayName("数据库存在配置时返回数据库中的配置")
        void getConfig_dbHasConfig_returnsDbConfig() {
            // Arrange
            BackupConfig dbConfig = new BackupConfig();
            dbConfig.setId(1);
            dbConfig.setAutoBackupEnabled(false);
            dbConfig.setBackupCron("0 30 3 * * ?");
            dbConfig.setBackupHour(3);
            dbConfig.setBackupMinute(30);
            dbConfig.setRetentionDays(15);
            dbConfig.setMaxCount(20);
            dbConfig.setIncrementalEnabled(true);
            dbConfig.setFullBackupIntervalDays(5);
            when(backupConfigMapper.selectById(1)).thenReturn(dbConfig);

            // Act
            BackupConfig result = backupConfigService.getConfig();

            // Assert
            assertSame(dbConfig, result);
            assertEquals(1, result.getId());
            assertFalse(result.getAutoBackupEnabled());
            assertEquals("0 30 3 * * ?", result.getBackupCron());
            assertEquals(3, result.getBackupHour());
            assertEquals(30, result.getBackupMinute());
            assertEquals(15, result.getRetentionDays());
            assertEquals(20, result.getMaxCount());
            assertTrue(result.getIncrementalEnabled());
            assertEquals(5, result.getFullBackupIntervalDays());
            verify(backupConfigMapper).selectById(1);
        }

        @Test
        @DisplayName("数据库为空时返回默认配置（来自@Value）")
        void getConfig_dbEmpty_returnsDefaultConfig() {
            // Arrange
            when(backupConfigMapper.selectById(1)).thenReturn(null);

            // Act
            BackupConfig result = backupConfigService.getConfig();

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getId());
            assertTrue(result.getAutoBackupEnabled());
            assertEquals("0 0 2 * * ?", result.getBackupCron());
            assertEquals(2, result.getBackupHour());
            assertEquals(0, result.getBackupMinute());
            assertEquals(30, result.getRetentionDays());
            assertEquals(50, result.getMaxCount());
            assertFalse(result.getIncrementalEnabled());
            assertEquals(7, result.getFullBackupIntervalDays());
        }

        @Test
        @DisplayName("数据库异常时返回默认配置且不抛出异常")
        void getConfig_dbThrowsException_returnsDefaultConfig() {
            // Arrange
            when(backupConfigMapper.selectById(1)).thenThrow(new RuntimeException("DB connection failed"));

            // Act
            BackupConfig result = backupConfigService.getConfig();

            // Assert
            assertNotNull(result);
            assertEquals(1, result.getId());
            assertTrue(result.getAutoBackupEnabled());
            assertEquals(30, result.getRetentionDays());
            assertEquals(50, result.getMaxCount());
        }
    }

    // ==================== updateConfig tests ====================

    @Nested
    @DisplayName("updateConfig 更新备份配置")
    class UpdateConfigTests {

        @Test
        @DisplayName("有效配置且数据库已有记录时调用updateById")
        void updateConfig_validValues_existingRecord_callsUpdateById() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(3);
            config.setBackupMinute(30);
            config.setRetentionDays(15);
            config.setMaxCount(20);
            config.setAutoBackupEnabled(true);

            BackupConfig existingConfig = new BackupConfig();
            existingConfig.setId(1);
            when(backupConfigMapper.selectById(1))
                    .thenReturn(existingConfig)   // first call: check existing
                    .thenReturn(config);           // second call: return updated

            // Act
            BackupConfig result = backupConfigService.updateConfig(config, "admin");

            // Assert
            verify(backupConfigMapper).updateById(config);
            verify(backupConfigMapper, never()).insert(any());
            verify(backupConfigMapper, times(2)).selectById(1);
        }

        @Test
        @DisplayName("有效配置且数据库无记录时调用insert")
        void updateConfig_validValues_noExistingRecord_callsInsert() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(5);
            config.setBackupMinute(0);
            config.setRetentionDays(10);
            config.setMaxCount(30);

            when(backupConfigMapper.selectById(1))
                    .thenReturn(null)    // first call: no existing record
                    .thenReturn(config); // second call: return after insert

            // Act
            BackupConfig result = backupConfigService.updateConfig(config, "admin");

            // Assert
            verify(backupConfigMapper).insert(config);
            verify(backupConfigMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("小时为负数时抛出BusinessException")
        void updateConfig_negativeHour_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(-1);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("0-23"));
        }

        @Test
        @DisplayName("小时超过23时抛出BusinessException")
        void updateConfig_hourOver23_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(24);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("0-23"));
        }

        @Test
        @DisplayName("分钟为负数时抛出BusinessException")
        void updateConfig_negativeMinute_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupMinute(-1);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("0-59"));
        }

        @Test
        @DisplayName("分钟超过59时抛出BusinessException")
        void updateConfig_minuteOver59_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupMinute(60);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("0-59"));
        }

        @Test
        @DisplayName("保留天数为0时抛出BusinessException")
        void updateConfig_retentionDaysZero_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setRetentionDays(0);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-365"));
        }

        @Test
        @DisplayName("保留天数超过365时抛出BusinessException")
        void updateConfig_retentionDaysOver365_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setRetentionDays(366);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-365"));
        }

        @Test
        @DisplayName("最大备份数为0时抛出BusinessException")
        void updateConfig_maxCountZero_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setMaxCount(0);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-200"));
        }

        @Test
        @DisplayName("最大备份数超过200时抛出BusinessException")
        void updateConfig_maxCountOver200_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setMaxCount(201);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-200"));
        }

        @Test
        @DisplayName("根据hour和minute自动生成正确的cron表达式")
        void updateConfig_generatesCorrectCronExpression() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(14);
            config.setBackupMinute(45);

            BackupConfig existing = new BackupConfig();
            existing.setId(1);
            when(backupConfigMapper.selectById(1))
                    .thenReturn(existing)
                    .thenReturn(config);

            // Act
            backupConfigService.updateConfig(config, "admin");

            // Assert
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(backupConfigMapper).updateById(captor.capture());
            assertEquals("0 45 14 * * ?", captor.getValue().getBackupCron());
        }

        @Test
        @DisplayName("hour为0、minute为0时生成午夜cron表达式")
        void updateConfig_midnightCron_generatesCorrectExpression() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(0);
            config.setBackupMinute(0);

            when(backupConfigMapper.selectById(1))
                    .thenReturn(new BackupConfig())
                    .thenReturn(config);

            // Act
            backupConfigService.updateConfig(config, "admin");

            // Assert
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(backupConfigMapper).updateById(captor.capture());
            assertEquals("0 0 0 * * ?", captor.getValue().getBackupCron());
        }

        @Test
        @DisplayName("只设置hour不设置minute时不生成cron表达式")
        void updateConfig_onlyHourSet_doesNotGenerateCron() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(3);
            // minute is null

            when(backupConfigMapper.selectById(1))
                    .thenReturn(new BackupConfig())
                    .thenReturn(config);

            // Act
            backupConfigService.updateConfig(config, "admin");

            // Assert
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(backupConfigMapper).updateById(captor.capture());
            assertNull(captor.getValue().getBackupCron());
        }

        @Test
        @DisplayName("正确设置updatedBy和updatedTime")
        void updateConfig_setsUpdatedByAndUpdatedTime() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setBackupHour(2);
            config.setBackupMinute(0);
            LocalDateTime before = LocalDateTime.now();

            when(backupConfigMapper.selectById(1))
                    .thenReturn(new BackupConfig())
                    .thenReturn(config);

            // Act
            backupConfigService.updateConfig(config, "admin_user");

            // Assert
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(backupConfigMapper).updateById(captor.capture());
            BackupConfig captured = captor.getValue();

            assertEquals("admin_user", captured.getUpdatedBy());
            assertNotNull(captured.getUpdatedTime());
            assertFalse(captured.getUpdatedTime().isBefore(before));
            assertTrue(captured.getUpdatedTime().isBefore(before.plusSeconds(5)));
        }

        @Test
        @DisplayName("id固定为1，不受传入值影响")
        void updateConfig_alwaysSetsIdTo1() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setId(999); // try to set a different id
            config.setBackupHour(2);
            config.setBackupMinute(0);

            when(backupConfigMapper.selectById(1))
                    .thenReturn(new BackupConfig())
                    .thenReturn(config);

            // Act
            backupConfigService.updateConfig(config, "admin");

            // Assert
            ArgumentCaptor<BackupConfig> captor = ArgumentCaptor.forClass(BackupConfig.class);
            verify(backupConfigMapper).updateById(captor.capture());
            assertEquals(1, captor.getValue().getId());
        }

        @Test
        @DisplayName("fullBackupIntervalDays超出范围时抛出BusinessException")
        void updateConfig_fullBackupIntervalDaysOutOfRange_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setFullBackupIntervalDays(31);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-30"));
        }

        @Test
        @DisplayName("fullBackupIntervalDays为0时抛出BusinessException")
        void updateConfig_fullBackupIntervalDaysZero_throwsBusinessException() {
            // Arrange
            BackupConfig config = new BackupConfig();
            config.setFullBackupIntervalDays(0);

            // Act & Assert
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> backupConfigService.updateConfig(config, "admin"));
            assertTrue(ex.getMessage().contains("1-30"));
        }
    }
}
