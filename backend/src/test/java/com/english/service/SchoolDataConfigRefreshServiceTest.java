package com.english.service;

import com.english.config.SchoolDataProperties;
import com.english.config.SchoolDataSyncScheduler;
import com.english.entity.SystemConfig;
import com.english.service.impl.SchoolDataApiClientDelegate;
import com.english.service.impl.SchoolDataEtlClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SchoolDataConfigRefreshService.
 *
 * Key behaviors:
 * - refreshFromDb: reads school_data group from DB → applies to Properties
 * - updateAndRefresh: filters **** masked values → batch update → refresh → trigger side effects
 * - testConnection: delegates to delegate.testConnection()
 * - Side effects: cron change → rescheduleAll, etl datasource change → recreateDataSource
 */
@ExtendWith(MockitoExtension.class)
class SchoolDataConfigRefreshServiceTest {

    private SchoolDataConfigRefreshService refreshService;

    @Mock
    private SystemConfigService configService;

    @Mock
    private SchoolDataSyncScheduler scheduler;

    @Mock
    private SchoolDataApiClientDelegate delegate;

    @Mock
    private SchoolDataEtlClientImpl etlClient;

    private SchoolDataProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SchoolDataProperties();
        refreshService = new SchoolDataConfigRefreshService(
                configService, properties, scheduler, delegate, etlClient
        );
    }

    @Nested
    @DisplayName("refreshFromDb")
    class RefreshFromDb {

        @Test
        @DisplayName("should apply DB config to Properties")
        void shouldApplyDbConfigToProperties() {
            Map<String, String> dbConfig = new HashMap<>();
            dbConfig.put("source", "etl");
            dbConfig.put("api_base_url", "https://custom.api.cn");
            dbConfig.put("etl_jdbc_url", "jdbc:mysql://school-db:3306/data");
            dbConfig.put("sync_enabled", "true");
            dbConfig.put("sync_batch_size", "200");
            when(configService.getByGroup("school_data")).thenReturn(dbConfig);

            refreshService.refreshFromDb();

            assertEquals("etl", properties.getSource());
            assertEquals("https://custom.api.cn", properties.getApi().getBaseUrl());
            assertEquals("jdbc:mysql://school-db:3306/data", properties.getEtl().getJdbcUrl());
            assertTrue(properties.getSync().isEnabled());
            assertEquals(200, properties.getSync().getBatchSize());
        }

        @Test
        @DisplayName("should do nothing when DB config is empty")
        void shouldDoNothingWhenEmpty() {
            when(configService.getByGroup("school_data")).thenReturn(Collections.emptyMap());

            refreshService.refreshFromDb();

            // Properties should remain at defaults
            assertEquals("api", properties.getSource());
        }

        @Test
        @DisplayName("should parse boolean values correctly")
        void shouldParseBooleanValues() {
            Map<String, String> dbConfig = Map.of("sync_enabled", "1");
            when(configService.getByGroup("school_data")).thenReturn(dbConfig);

            refreshService.refreshFromDb();

            assertTrue(properties.getSync().isEnabled());
        }

        @Test
        @DisplayName("should handle invalid integer gracefully")
        void shouldHandleInvalidInteger() {
            Map<String, String> dbConfig = Map.of("sync_batch_size", "not-a-number");
            when(configService.getByGroup("school_data")).thenReturn(dbConfig);

            refreshService.refreshFromDb();

            assertEquals(0, properties.getSync().getBatchSize());
        }
    }

    @Nested
    @DisplayName("updateAndRefresh")
    class UpdateAndRefresh {

        @Test
        @DisplayName("should filter masked values containing ****")
        void shouldFilterMaskedValues() {
            SystemConfig maskedConfig = new SystemConfig();
            maskedConfig.setConfigKey("api_token");
            maskedConfig.setConfigValue("****6789");

            SystemConfig normalConfig = new SystemConfig();
            normalConfig.setConfigKey("api_base_url");
            normalConfig.setConfigValue("https://new.api.cn");

            when(configService.getByGroup("school_data")).thenReturn(Map.of(
                    "api_base_url", "https://new.api.cn"
            ));

            refreshService.updateAndRefresh(List.of(maskedConfig, normalConfig), "admin");

            // Only normalConfig should be updated (masked is filtered)
            verify(configService).batchUpdate(List.of(normalConfig), "admin");
        }

        @Test
        @DisplayName("should skip update entirely when all values are masked")
        void shouldSkipUpdateWhenAllMasked() {
            SystemConfig maskedConfig = new SystemConfig();
            maskedConfig.setConfigKey("api_token");
            maskedConfig.setConfigValue("****6789");

            refreshService.updateAndRefresh(List.of(maskedConfig), "admin");

            verify(configService, never()).batchUpdate(any(), anyString());
        }

        @Test
        @DisplayName("should trigger reschedule when cron config changes")
        void shouldTriggerRescheduleOnCronChange() {
            SystemConfig cronConfig = new SystemConfig();
            cronConfig.setConfigKey("sync_full_cron");
            cronConfig.setConfigValue("0 0 3 * * ?");

            when(configService.getByGroup("school_data")).thenReturn(Collections.emptyMap());

            refreshService.updateAndRefresh(List.of(cronConfig), "admin");

            verify(scheduler).rescheduleAll();
        }

        @Test
        @DisplayName("should trigger recreateDataSource when ETL config changes")
        void shouldTriggerRecreateDataSourceOnEtlChange() {
            SystemConfig etlConfig = new SystemConfig();
            etlConfig.setConfigKey("etl_jdbc_url");
            etlConfig.setConfigValue("jdbc:mysql://new-host:3306/db");

            when(configService.getByGroup("school_data")).thenReturn(Collections.emptyMap());

            refreshService.updateAndRefresh(List.of(etlConfig), "admin");

            verify(etlClient).recreateDataSource();
        }

        @Test
        @DisplayName("should not trigger side effects when non-special config changes")
        void shouldNotTriggerSideEffectsForNormalConfig() {
            SystemConfig normalConfig = new SystemConfig();
            normalConfig.setConfigKey("api_base_url");
            normalConfig.setConfigValue("https://api.example.com");

            when(configService.getByGroup("school_data")).thenReturn(Collections.emptyMap());

            refreshService.updateAndRefresh(List.of(normalConfig), "admin");

            verify(scheduler, never()).rescheduleAll();
            verify(etlClient, never()).recreateDataSource();
        }

        @Test
        @DisplayName("should trigger reschedule when sync_enabled changes")
        void shouldTriggerRescheduleOnSyncEnabledChange() {
            SystemConfig enabledConfig = new SystemConfig();
            enabledConfig.setConfigKey("sync_enabled");
            enabledConfig.setConfigValue("true");

            when(configService.getByGroup("school_data")).thenReturn(Collections.emptyMap());

            refreshService.updateAndRefresh(List.of(enabledConfig), "admin");

            verify(scheduler).rescheduleAll();
        }
    }

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("should delegate to delegate.testConnection()")
        void shouldDelegateTestConnection() {
            when(delegate.testConnection()).thenReturn(true);

            boolean result = refreshService.testConnection();

            assertTrue(result);
            verify(delegate).testConnection();
        }

        @Test
        @DisplayName("should return false when connection fails")
        void shouldReturnFalseOnFailure() {
            when(delegate.testConnection()).thenReturn(false);

            boolean result = refreshService.testConnection();

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("getCurrentSource")
    class GetCurrentSource {

        @Test
        @DisplayName("should return current source from properties")
        void shouldReturnCurrentSource() {
            properties.setSource("etl");

            assertEquals("etl", refreshService.getCurrentSource());
        }
    }
}
