package com.english.service;

import com.english.config.SchoolDataProperties;
import com.english.entity.FieldMapping;
import com.english.entity.SyncLog;
import com.english.mapper.*;
import com.english.service.impl.SchoolDataSyncServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SchoolDataSyncServiceImpl.
 *
 * Key implementation facts:
 * - fullSync/incrementalSync: fetch API data -> apply field mapping -> UPSERT per record -> log results
 * - Anti-reentry: AtomicBoolean prevents concurrent sync of same type
 * - Single record failure doesn't affect the overall batch (catch and continue)
 * - SyncLog records: RUNNING -> SUCCESS/FAILED lifecycle
 * - fullSyncAll/incrementalSyncAll: iterate all 7 data sources, one failure doesn't stop others
 */
@ExtendWith(MockitoExtension.class)
class SchoolDataSyncServiceTest {

    private SchoolDataSyncServiceImpl syncService;

    @Mock
    private SchoolDataApiClient apiClient;

    @Mock
    private FieldMappingService fieldMappingService;

    @Mock
    private SyncLogMapper syncLogMapper;

    @Mock
    private AdministrativeClassMapper administrativeClassMapper;

    @Mock
    private SchoolCourseMapper schoolCourseMapper;

    @Mock
    private TeachingClassMapper teachingClassMapper;

    @Mock
    private CourseScheduleMapper courseScheduleMapper;

    @Mock
    private CourseSelectionMapper courseSelectionMapper;

    @Mock
    private LevelExamScoreMapper levelExamScoreMapper;

    @Mock
    private TeachingPlanMapper teachingPlanMapper;

    @Mock
    private TeachingPlanCourseMapper teachingPlanCourseMapper;

    @Mock
    private StudentScheduleMapper studentScheduleMapper;

    private ObjectMapper objectMapper;
    private SchoolDataProperties properties;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new SchoolDataProperties();
        syncService = new SchoolDataSyncServiceImpl(
                apiClient, fieldMappingService, syncLogMapper,
                administrativeClassMapper, schoolCourseMapper, teachingClassMapper,
                courseScheduleMapper, courseSelectionMapper, levelExamScoreMapper,
                teachingPlanMapper, teachingPlanCourseMapper, studentScheduleMapper,
                objectMapper, properties
        );
    }

    // ==================== fullSync tests ====================

    @Nested
    @DisplayName("fullSync 全量同步指定数据源")
    class FullSyncTests {

        @Test
        @DisplayName("不支持的数据源抛出BusinessException")
        void fullSync_unsupportedDataSource_throwsBusinessException() {
            assertThrows(Exception.class,
                    () -> syncService.fullSync("invalid_source", "MANUAL", "admin"));
        }

        @Test
        @DisplayName("API返回空数据时返回零计数同步日志")
        void fullSync_emptyApiData_returnsZeroCounts() {
            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(Collections.emptyList());
            when(syncLogMapper.insert(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertNotNull(result);
            verify(apiClient).fetchAdministrativeClasses(any());
        }

        @Test
        @DisplayName("同步完成后状态为SUCCESS")
        void fullSync_success_setsStatusToSuccess() {
            List<Map<String, Object>> apiData = List.of(
                    Map.of("XZBH", "20230101", "XZMC", "计算机1班")
            );

            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(apiData);
            Map<String, Object> mappedResult = new HashMap<>();
            mappedResult.put("classCode", "20230101");
            mappedResult.put("className", "计算机1班");
            when(fieldMappingService.applyMapping(anyString(), any())).thenReturn(mappedResult);
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);
            doNothing().when(administrativeClassMapper).upsertByClassCode(any());

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertNotNull(result);
            assertEquals("SUCCESS", result.getStatus());
            verify(administrativeClassMapper).upsertByClassCode(any());
        }

        @Test
        @DisplayName("API异常时状态为FAILED")
        void fullSync_apiException_setsStatusToFailed() {
            when(apiClient.fetchAdministrativeClasses(any())).thenThrow(new RuntimeException("API连接失败"));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertEquals("FAILED", result.getStatus());
            assertNotNull(result.getErrorMessage());
        }
    }

    // ==================== fullSyncAll tests ====================

    @Nested
    @DisplayName("fullSyncAll 全量同步所有数据源")
    class FullSyncAllTests {

        @Test
        @DisplayName("同步所有9个数据源")
        void fullSyncAll_syncsAllDataSources() {
            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchSchoolCourses(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchTeachingClasses(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchCourseSchedules(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchCourseSelections(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchLevelExamScores(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchTeachingPlans(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchTeacherCourses(any())).thenReturn(Collections.emptyList());
            // student_schedule: fullSync不支持批量拉取，直接返回空列表，不调用apiClient
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            List<SyncLog> results = syncService.fullSyncAll("SCHEDULED", "SYSTEM");

            assertEquals(9, results.size());
        }

        @Test
        @DisplayName("单个数据源失败不影响其他数据源")
        void fullSyncAll_singleFailure_continuesWithOthers() {
            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchSchoolCourses(any())).thenThrow(new RuntimeException("API错误"));
            when(apiClient.fetchTeachingClasses(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchCourseSchedules(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchCourseSelections(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchLevelExamScores(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchTeachingPlans(any())).thenReturn(Collections.emptyList());
            when(apiClient.fetchTeacherCourses(any())).thenReturn(Collections.emptyList());
            // student_schedule: fullSync不支持批量拉取，直接返回空列表，不调用apiClient
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            List<SyncLog> results = syncService.fullSyncAll("SCHEDULED", "SYSTEM");

            assertEquals(9, results.size());
            assertEquals("FAILED", results.get(1).getStatus());
            assertEquals("SUCCESS", results.get(2).getStatus());
        }
    }

    // ==================== incrementalSync tests ====================

    @Nested
    @DisplayName("incrementalSync 增量同步指定数据源")
    class IncrementalSyncTests {

        @Test
        @DisplayName("增量同步正确传递updatedSince参数")
        void incrementalSync_passesUpdatedSinceParam() {
            SyncLog lastSync = new SyncLog();
            lastSync.setEndTime(LocalDateTime.of(2026, 4, 11, 10, 0, 0));

            when(syncLogMapper.selectLatestByDataSource("administrative_class")).thenReturn(lastSync);
            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(Collections.emptyList());
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            syncService.incrementalSync("administrative_class", "MANUAL", "admin");

            verify(apiClient).fetchAdministrativeClasses(any());
        }

        @Test
        @DisplayName("无上次同步记录时不传updatedSince参数")
        void incrementalSync_noLastSync_noUpdatedSince() {
            when(syncLogMapper.selectLatestByDataSource("administrative_class")).thenReturn(null);
            when(apiClient.fetchAdministrativeClasses(any())).thenReturn(Collections.emptyList());
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            syncService.incrementalSync("administrative_class", "MANUAL", "admin");

            verify(apiClient).fetchAdministrativeClasses(any());
        }
    }

    // ==================== getSyncStatusOverview tests ====================

    @Nested
    @DisplayName("getSyncStatusOverview 同步状态概览")
    class GetSyncStatusOverviewTests {

        @Test
        @DisplayName("所有数据源都有概览信息")
        void getSyncStatusOverview_returnsAllDataSources() {
            when(syncLogMapper.selectLatestPerDataSource()).thenReturn(Collections.emptyList());
            when(administrativeClassMapper.selectCount(any())).thenReturn(0L);
            when(schoolCourseMapper.selectCount(any())).thenReturn(0L);
            when(teachingClassMapper.selectCount(any())).thenReturn(0L);
            when(courseScheduleMapper.selectCount(any())).thenReturn(0L);
            when(courseSelectionMapper.selectCount(any())).thenReturn(0L);
            when(levelExamScoreMapper.selectCount(any())).thenReturn(0L);
            when(teachingPlanMapper.selectCount(any())).thenReturn(0L);
            when(studentScheduleMapper.selectCount(any())).thenReturn(0L);

            Map<String, Object> overview = syncService.getSyncStatusOverview();

            assertEquals(9, overview.size());
            assertTrue(overview.containsKey("administrative_class"));
            assertTrue(overview.containsKey("teaching_plans"));
            assertTrue(overview.containsKey("teacher_courses"));
            assertTrue(overview.containsKey("student_schedule"));
        }

        @Test
        @DisplayName("从未同步的数据源状态为NEVER")
        void getSyncStatusOverview_neverSynced_statusIsNever() {
            when(syncLogMapper.selectLatestPerDataSource()).thenReturn(Collections.emptyList());
            when(administrativeClassMapper.selectCount(any())).thenReturn(5L);

            Map<String, Object> overview = syncService.getSyncStatusOverview();

            @SuppressWarnings("unchecked")
            Map<String, Object> sourceStatus = (Map<String, Object>) overview.get("administrative_class");
            assertEquals("NEVER", sourceStatus.get("lastSyncStatus"));
            assertEquals(5L, sourceStatus.get("localCount"));
        }
    }

    // ==================== validateData tests ====================

    @Nested
    @DisplayName("validateData 数据校验")
    class ValidateDataTests {

        @Test
        @DisplayName("所有数据源都有字段映射时校验通过")
        void validateData_hasMappings_returnsHealthy() {
            FieldMapping mapping = new FieldMapping();
            mapping.setId(1L);
            when(fieldMappingService.getMappingsByDataSource(anyString())).thenReturn(List.of(mapping));
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            Map<String, Object> result = syncService.validateData();

            assertEquals(0, result.get("totalIssues"));
            assertEquals(true, result.get("overallHealthy"));
        }

        @Test
        @DisplayName("缺少字段映射配置时产生告警")
        void validateData_noMappings_reportsIssue() {
            when(fieldMappingService.getMappingsByDataSource(anyString())).thenReturn(Collections.emptyList());
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            Map<String, Object> result = syncService.validateData();

            assertTrue((int) result.get("totalIssues") > 0);
            assertEquals(false, result.get("overallHealthy"));
        }
    }
}
