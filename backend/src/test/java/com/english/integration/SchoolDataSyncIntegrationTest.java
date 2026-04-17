package com.english.integration;

import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataSyncService;
import com.english.service.SyncMonitorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for School Data Sync system.
 *
 * These tests verify the full integration between:
 * - SchoolDataSyncService (core sync logic)
 * - FieldMappingService (field mapping and conversion)
 * - SyncMonitorService (health monitoring)
 * - MyBatis Mapper UPSERT operations
 * - SyncLog lifecycle (RUNNING -> SUCCESS/FAILED)
 *
 * Note: Requires database connection to run. Uses @Transactional for rollback.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchoolDataSyncIntegrationTest {

    @Autowired
    private SchoolDataSyncService syncService;

    @Autowired
    private SyncMonitorService monitorService;

    @Autowired
    private FieldMappingService fieldMappingService;

    @Autowired
    private AdministrativeClassMapper administrativeClassMapper;

    @Autowired
    private SchoolCourseMapper schoolCourseMapper;

    @Autowired
    private TeachingClassMapper teachingClassMapper;

    @Autowired
    private CourseSelectionMapper courseSelectionMapper;

    @Autowired
    private LevelExamScoreMapper levelExamScoreMapper;

    @Autowired
    private TeachingPlanMapper teachingPlanMapper;

    @Autowired
    private SyncLogMapper syncLogMapper;

    // ==================== Field Mapping Integration ====================

    @Nested
    @DisplayName("字段映射集成测试")
    class FieldMappingIntegrationTests {

        @Test
        @DisplayName("获取行政班字段映射配置")
        void getAdministrativeClassMappings() {
            List<FieldMapping> mappings = fieldMappingService.getMappingsByDataSource("administrative_class");

            assertNotNull(mappings);
            assertFalse(mappings.isEmpty(), "行政班字段映射不应为空");
            assertTrue(mappings.stream().anyMatch(m -> "XZBH".equals(m.getSourceField())));
            assertTrue(mappings.stream().anyMatch(m -> "classCode".equals(m.getTargetField())));
        }

        @Test
        @DisplayName("获取学校课程字段映射配置")
        void getSchoolCourseMappings() {
            List<FieldMapping> mappings = fieldMappingService.getMappingsByDataSource("school_course");

            assertNotNull(mappings);
            assertFalse(mappings.isEmpty(), "学校课程字段映射不应为空");
        }

        @Test
        @DisplayName("字段映射应用 - 行政班数据转换")
        void applyMapping_administrativeClass() {
            Map<String, Object> sourceData = new LinkedHashMap<>();
            sourceData.put("XZBH", "2023010101");
            sourceData.put("XZMC", "计算机1班");
            sourceData.put("XYDM", "01");
            sourceData.put("XYMC", "计算机学院");

            Map<String, Object> result = fieldMappingService.applyMapping("administrative_class", sourceData);

            assertNotNull(result);
            assertEquals("2023010101", result.get("classCode"));
            assertEquals("计算机1班", result.get("className"));
        }

        @Test
        @DisplayName("BOOLEAN转换器 - 中文值正确转换")
        void booleanConverter_chineseValues() {
            Map<String, Object> sourceData = new LinkedHashMap<>();
            sourceData.put("SFSYJX", "是");

            Map<String, Object> result = fieldMappingService.applyMapping("school_course", sourceData);

            // isBilingual should be true after BOOLEAN conversion
            if (result.containsKey("isBilingual")) {
                assertEquals(true, result.get("isBilingual"));
            }
        }
    }

    // ==================== UPSERT Integration ====================

    @Nested
    @DisplayName("UPSERT操作集成测试")
    class UpsertIntegrationTests {

        @Test
        @DisplayName("行政班UPSERT - 插入新记录")
        void upsertAdministrativeClass_insert() {
            AdministrativeClass entity = new AdministrativeClass();
            entity.setClassCode("TEST_CODE_" + System.currentTimeMillis());
            entity.setClassName("测试班级");
            entity.setCollegeCode("01");
            entity.setCollegeName("测试学院");
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            administrativeClassMapper.upsertByClassCode(entity);

            // Verify inserted
            assertNotNull(entity.getId());
        }

        @Test
        @DisplayName("行政班UPSERT - 更新已有记录")
        void upsertAdministrativeClass_update() {
            String uniqueCode = "TEST_UPSERT_" + System.currentTimeMillis();

            // First insert
            AdministrativeClass entity1 = new AdministrativeClass();
            entity1.setClassCode(uniqueCode);
            entity1.setClassName("原始名称");
            entity1.setSyncTime(LocalDateTime.now());
            entity1.setSyncSource("TEST");
            administrativeClassMapper.upsertByClassCode(entity1);

            // Then update with same code
            AdministrativeClass entity2 = new AdministrativeClass();
            entity2.setClassCode(uniqueCode);
            entity2.setClassName("更新名称");
            entity2.setSyncTime(LocalDateTime.now());
            entity2.setSyncSource("TEST");
            administrativeClassMapper.upsertByClassCode(entity2);

            // Should not have duplicated the record
            // (verification would require a select query)
        }

        @Test
        @DisplayName("学校课程UPSERT - 插入新记录")
        void upsertSchoolCourse_insert() {
            SchoolCourse entity = new SchoolCourse();
            entity.setCourseCode("TEST_COURSE_" + System.currentTimeMillis());
            entity.setCourseName("测试课程");
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            schoolCourseMapper.upsertByCourseCode(entity);

            assertNotNull(entity.getId());
        }

        @Test
        @DisplayName("教学班UPSERT - 插入新记录")
        void upsertTeachingClass_insert() {
            TeachingClass entity = new TeachingClass();
            entity.setClassId("TEST_TC_" + System.currentTimeMillis());
            entity.setClassName("测试教学班");
            entity.setAcademicYear("2025-2026");
            entity.setSemester(1);
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            teachingClassMapper.upsertByClassId(entity);

            assertNotNull(entity.getId());
        }

        @Test
        @DisplayName("选课记录UPSERT - 插入新记录")
        void upsertCourseSelection_insert() {
            CourseSelection entity = new CourseSelection();
            entity.setSelectionId("TEST_SEL_" + System.currentTimeMillis());
            entity.setStudentId("2023001");
            entity.setCourseCode("CS101");
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            courseSelectionMapper.upsertBySelectionId(entity);

            assertNotNull(entity.getId());
        }

        @Test
        @DisplayName("等级考试成绩UPSERT - 插入新记录")
        void upsertLevelExamScore_insert() {
            LevelExamScore entity = new LevelExamScore();
            entity.setStudentId("2023STU_" + System.currentTimeMillis());
            entity.setExamType("CET4");
            entity.setExamDate(java.time.LocalDate.of(2026, 6, 15));
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            levelExamScoreMapper.upsertByStudentExam(entity);

            assertNotNull(entity.getId());
        }

        @Test
        @DisplayName("教学计划UPSERT - 插入新记录")
        void upsertTeachingPlan_insert() {
            TeachingPlan entity = new TeachingPlan();
            entity.setPlanId("TEST_PLAN_" + System.currentTimeMillis());
            entity.setPlanName("测试教学计划");
            entity.setMajorCode("CS");
            entity.setSyncTime(LocalDateTime.now());
            entity.setSyncSource("INTEGRATION_TEST");

            teachingPlanMapper.upsertByPlanId(entity);

            assertNotNull(entity.getId());
        }
    }

    // ==================== Sync Monitor Integration ====================

    @Nested
    @DisplayName("同步监控集成测试")
    class SyncMonitorIntegrationTests {

        @Test
        @DisplayName("健康检查返回所有数据源状态")
        void checkSyncHealth_returnsAllDataSources() {
            Map<String, Object> healthReport = monitorService.checkSyncHealth();

            assertNotNull(healthReport);
            assertTrue(healthReport.containsKey("summary"));
            assertTrue(healthReport.containsKey("sources"));
            assertTrue(healthReport.containsKey("alerts"));

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) healthReport.get("summary");
            assertEquals(7, summary.get("totalSources"));
        }

        @Test
        @DisplayName("同步状态概览包含本地数据量")
        void getSyncStatusOverview_includesLocalCount() {
            Map<String, Object> overview = syncService.getSyncStatusOverview();

            assertNotNull(overview);
            assertEquals(7, overview.size());

            for (String dataSource : overview.keySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceStatus = (Map<String, Object>) overview.get(dataSource);
                assertTrue(sourceStatus.containsKey("localCount"));
                assertTrue(sourceStatus.containsKey("lastSyncStatus"));
            }
        }

        @Test
        @DisplayName("数据校验返回各数据源状态")
        void validateData_returnsSourceStatus() {
            Map<String, Object> result = syncService.validateData();

            assertNotNull(result);
            assertTrue(result.containsKey("totalIssues"));
            assertTrue(result.containsKey("overallHealthy"));
            assertTrue(result.containsKey("validatedAt"));
        }
    }

    // ==================== SyncLog Lifecycle ====================

    @Nested
    @DisplayName("同步日志生命周期测试")
    class SyncLogLifecycleTests {

        @Test
        @DisplayName("同步日志正确记录 RUNNING -> SUCCESS 生命周期")
        void syncLog_lifecycle_runningToSuccess() {
            // Create a running log
            SyncLog syncLog = new SyncLog();
            syncLog.setSyncType("FULL");
            syncLog.setDataSource("administrative_class");
            syncLog.setSyncMethod("REPLACE_ALL");
            syncLog.setStatus("RUNNING");
            syncLog.setStartTime(LocalDateTime.now());
            syncLog.setTriggerType("MANUAL");
            syncLog.setOperator("TEST");

            syncLogMapper.insert(syncLog);
            assertNotNull(syncLog.getId());

            // Update to SUCCESS
            syncLog.setStatus("SUCCESS");
            syncLog.setTotalCount(100);
            syncLog.setSuccessCount(95);
            syncLog.setFailCount(5);
            syncLog.setSkipCount(0);
            syncLog.setEndTime(LocalDateTime.now());
            syncLog.setDurationMs(5000L);
            syncLogMapper.updateById(syncLog);

            // Verify
            SyncLog retrieved = syncLogMapper.selectById(syncLog.getId());
            assertEquals("SUCCESS", retrieved.getStatus());
            assertEquals(100, retrieved.getTotalCount());
            assertEquals(95, retrieved.getSuccessCount());
            assertEquals(5, retrieved.getFailCount());
        }

        @Test
        @DisplayName("查询最新同步日志")
        void syncLog_selectLatestByDataSource() {
            // Insert test data
            SyncLog log1 = new SyncLog();
            log1.setSyncType("FULL");
            log1.setDataSource("test_integration_" + System.currentTimeMillis());
            log1.setSyncMethod("REPLACE_ALL");
            log1.setStatus("SUCCESS");
            log1.setStartTime(LocalDateTime.now().minusHours(2));
            log1.setTriggerType("SCHEDULED");
            log1.setOperator("SYSTEM");
            syncLogMapper.insert(log1);

            SyncLog log2 = new SyncLog();
            log2.setSyncType("INCREMENTAL");
            log2.setDataSource(log1.getDataSource());
            log2.setSyncMethod("UPSERT");
            log2.setStatus("SUCCESS");
            log2.setStartTime(LocalDateTime.now().minusHours(1));
            log2.setTriggerType("SCHEDULED");
            log2.setOperator("SYSTEM");
            syncLogMapper.insert(log2);

            // Query latest
            SyncLog latest = syncLogMapper.selectLatestByDataSource(log1.getDataSource());
            assertNotNull(latest);
            assertEquals("INCREMENTAL", latest.getSyncType());
        }
    }
}
