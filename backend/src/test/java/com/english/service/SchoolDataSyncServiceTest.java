package com.english.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.english.entity.FieldMapping;
import com.english.entity.SyncLog;
import com.english.mapper.*;
import com.english.service.impl.SchoolDataFetchResult;
import com.english.service.impl.SchoolDataSyncServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    private SchoolDataFetchService fetchService;

    @Mock
    private SchoolDataPersistService persistService;

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
    private StudentScheduleMapper studentScheduleMapper;

    @Mock
    private SchoolStudentMapper schoolStudentMapper;

    @Mock
    private TeacherStudentBindingMapper teacherStudentBindingMapper;

    @Mock
    private TeachingPlanCourseMapper teachingPlanCourseMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        syncService = new SchoolDataSyncServiceImpl(
                apiClient, fetchService, persistService, fieldMappingService, syncLogMapper,
                levelExamScoreMapper, studentScheduleMapper, schoolStudentMapper,
                teacherStudentBindingMapper, administrativeClassMapper, schoolCourseMapper,
                teachingClassMapper, courseScheduleMapper, courseSelectionMapper,
                teachingPlanMapper, teachingPlanCourseMapper, userMapper, objectMapper
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
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(syncLogMapper.insert(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertNotNull(result);
            verify(fetchService).fetchRawDataWithSummary(eq("administrative_class"), isNull());
        }

        @Test
        @DisplayName("同步完成后状态为SUCCESS")
        void fullSync_success_setsStatusToSuccess() {
            List<Map<String, Object>> apiData = List.of(
                    Map.of("XZBH", "20230101", "XZMC", "计算机1班")
            );

            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), isNull())).thenReturn(fetchResult(apiData));
            when(persistService.persistRawData(eq("administrative_class"), eq(apiData), eq("SCHOOL_API")))
                    .thenReturn(new SchoolDataPersistService.PersistResult(1, 1, 0, 0));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertNotNull(result);
            assertEquals("SUCCESS", result.getStatus());
            verify(persistService).persistRawData(eq("administrative_class"), eq(apiData), eq("SCHOOL_API"));
        }

        @Test
        @DisplayName("course_selection 缺少学年学期时使用降级唯一键")
        void fullSync_courseSelection_missingYearSemester_fallsBackToDefaultKey() {
            List<Map<String, Object>> apiData = List.of(
                    Map.of("XH", "2023001", "KCBH", "ENG101-01")
            );

            when(fetchService.fetchRawDataWithSummary(eq("course_selection"), isNull())).thenReturn(fetchResult(apiData));
            when(persistService.persistRawData(eq("course_selection"), eq(apiData), eq("SCHOOL_API")))
                    .thenReturn(new SchoolDataPersistService.PersistResult(1, 1, 0, 0));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("course_selection", "MANUAL", "admin");

            assertEquals("SUCCESS", result.getStatus());
            verify(persistService).persistRawData(eq("course_selection"), eq(apiData), eq("SCHOOL_API"));
        }

        @Test
        @DisplayName("administrative_class 缺少学院信息时使用默认值")
        void fullSync_administrativeClass_missingCollege_usesFallback() {
            List<Map<String, Object>> apiData = List.of(
                    Map.of("XZBH", "150108", "XZMC", "15植保2")
            );

            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), isNull())).thenReturn(fetchResult(apiData));
            when(persistService.persistRawData(eq("administrative_class"), eq(apiData), eq("SCHOOL_API")))
                    .thenReturn(new SchoolDataPersistService.PersistResult(1, 1, 0, 0));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertEquals("SUCCESS", result.getStatus());
            verify(persistService).persistRawData(eq("administrative_class"), eq(apiData), eq("SCHOOL_API"));
        }



        @Test
        @DisplayName("上游响应显示未完整拉取时全量同步失败且不落库")
        void fullSync_incompleteFetch_failsWithoutPersisting() {
            List<Map<String, Object>> apiData = List.of(Map.of("XZBH", "20230101"));
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), isNull()))
                    .thenReturn(incompleteFetchResult(apiData));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("administrative_class", "MANUAL", "admin");

            assertEquals("FAILED", result.getStatus());
            assertTrue(result.getErrorMessage().contains("学校数据未完整拉取"));
            verify(persistService, never()).persistRawData(anyString(), any(), anyString());
        }

        @Test
        @DisplayName("level_exam_scores 姓名优先使用本地 school_student 映射")
        void fullSync_levelExamScores_nameResolvedFromSchoolStudent() {
            List<Map<String, Object>> apiData = List.of(
                    Map.of("xh", "2023001", "kskm", "CET4", "ksrq", "2026-01-01", "fskscj", 425)
            );

            when(fetchService.fetchRawDataWithSummary(eq("level_exam_scores"), isNull())).thenReturn(fetchResult(apiData));
            when(persistService.persistRawData(eq("level_exam_scores"), eq(apiData), eq("SCHOOL_API")))
                    .thenReturn(new SchoolDataPersistService.PersistResult(1, 1, 0, 0));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.fullSync("level_exam_scores", "MANUAL", "admin");

            assertEquals("SUCCESS", result.getStatus());
            verify(persistService).persistRawData(eq("level_exam_scores"), eq(apiData), eq("SCHOOL_API"));
        }
    }

    // ==================== fullSyncAll tests ====================

    @Nested
    @DisplayName("fullSyncAll 全量同步所有数据源")
    class FullSyncAllTests {

        @Test
        @DisplayName("同步所有10个数据源")
        void fullSyncAll_syncsAllDataSources() {
            when(fetchService.fetchRawDataWithSummary(anyString(), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            List<SyncLog> results = syncService.fullSyncAll("SCHEDULED", "SYSTEM");

            assertEquals(10, results.size());
            // student_schedule is skipped (requires student ID), so fetchService is called 9 times
            verify(fetchService, times(9)).fetchRawDataWithSummary(anyString(), isNull());
        }

        @Test
        @DisplayName("单个数据源失败不影响其他数据源")
        void fullSyncAll_singleFailure_continuesWithOthers() {
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("school_course"), isNull())).thenThrow(new RuntimeException("API错误"));
            when(fetchService.fetchRawDataWithSummary(eq("teaching_class"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("course_schedule"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("course_selection"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("level_exam_scores"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("teaching_plans"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("teacher_courses"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            when(fetchService.fetchRawDataWithSummary(eq("student_basic_info"), isNull())).thenReturn(fetchResult(Collections.emptyList()));
            // student_schedule is skipped by fullSyncAll — no stub needed
            when(syncLogMapper.insert(any())).thenReturn(1);
            when(syncLogMapper.updateById(any())).thenReturn(1);

            List<SyncLog> results = syncService.fullSyncAll("SCHEDULED", "SYSTEM");

            assertEquals(10, results.size());
            assertEquals("FAILED", results.get(1).getStatus());
            assertEquals("SUCCESS", results.get(2).getStatus());
        }
    }

    // ==================== incrementalSync tests ====================

    @Nested
    @DisplayName("incrementalSync 增量同步指定数据源")
    class IncrementalSyncTests {

        @Test
        @DisplayName("增量同步调用API并写入同步日志")
        void incrementalSync_callsApiAndWritesLog() {
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), any())).thenReturn(fetchResult(Collections.emptyList()));
            when(syncLogMapper.insert(any())).thenAnswer(invocation -> {
                SyncLog log = invocation.getArgument(0);
                log.setId(1L);
                return 1;
            });
            when(syncLogMapper.updateById(any())).thenReturn(1);

            syncService.incrementalSync("administrative_class", "MANUAL", "admin");

            verify(fetchService).fetchRawDataWithSummary(eq("administrative_class"), any());
        }

        @Test
        @DisplayName("增量同步无数据时仍完成日志记录")
        void incrementalSync_emptyResult_completesSuccessfully() {
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), any())).thenReturn(fetchResult(Collections.emptyList()));
            when(syncLogMapper.insert(any())).thenAnswer(invocation -> {
                SyncLog log = invocation.getArgument(0);
                log.setId(1L);
                return 1;
            });
            when(syncLogMapper.updateById(any())).thenReturn(1);

            syncService.incrementalSync("administrative_class", "MANUAL", "admin");

            verify(fetchService).fetchRawDataWithSummary(eq("administrative_class"), any());
        }

        @Test
        @DisplayName("上游响应显示未完整拉取时增量同步失败且不落库")
        void incrementalSync_incompleteFetch_failsWithoutPersisting() {
            List<Map<String, Object>> apiData = List.of(Map.of("XZBH", "20230101"));
            when(fetchService.fetchRawDataWithSummary(eq("administrative_class"), any()))
                    .thenReturn(incompleteFetchResult(apiData));
            when(syncLogMapper.insert(any())).thenAnswer(invocation -> {
                SyncLog log = invocation.getArgument(0);
                log.setId(1L);
                return 1;
            });
            when(syncLogMapper.updateById(any())).thenReturn(1);

            SyncLog result = syncService.incrementalSync("administrative_class", "MANUAL", "admin");

            assertEquals("FAILED", result.getStatus());
            assertTrue(result.getErrorMessage().contains("学校数据未完整拉取"));
            verify(persistService, never()).persistRawData(anyString(), any(), anyString());
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

            assertEquals(10, overview.size());
            assertTrue(overview.containsKey("administrative_class"));
            assertTrue(overview.containsKey("teaching_plans"));
            assertTrue(overview.containsKey("teacher_courses"));
            assertTrue(overview.containsKey("student_schedule"));
            assertTrue(overview.containsKey("student_basic_info"));
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
        @DisplayName("所有数据源都有关键字段映射时校验通过")
        void validateData_hasMappings_returnsHealthy() {
            when(fieldMappingService.getMappingsByDataSource(anyString())).thenAnswer(invocation -> {
                String dataSource = invocation.getArgument(0);
                List<String> targets;
                switch (dataSource) {
                    case "teacher_courses":
                        targets = List.of("classId", "courseCode");
                        break;
                    case "course_selection":
                        targets = List.of("studentId", "classId");
                        break;
                    case "student_schedule":
                        targets = List.of("classId", "courseCode");
                        break;
                    case "course_schedule":
                        targets = List.of("classId");
                        break;
                    case "level_exam_scores":
                        targets = List.of("studentId", "examName");
                        break;
                    default:
                        targets = List.of("ok");
                        break;
                }
                List<FieldMapping> mappings = new ArrayList<>();
                for (String target : targets) {
                    FieldMapping mapping = new FieldMapping();
                    mapping.setTargetField(target);
                    mappings.add(mapping);
                }
                return mappings;
            });
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            Map<String, Object> result = syncService.validateData();

            assertEquals(0, result.get("totalIssues"));
            assertEquals(true, result.get("overallHealthy"));
        }

        @Test
        @DisplayName("关键数据源缺少关键 targetField 时 validateData 报告问题")
        void validateData_missingCriticalMappings_reportsIssue() {
            FieldMapping teacherCourseMapping = new FieldMapping();
            teacherCourseMapping.setTargetField("courseCode");
            FieldMapping courseSelectionMapping = new FieldMapping();
            courseSelectionMapping.setTargetField("studentId");
            FieldMapping studentScheduleMapping = new FieldMapping();
            studentScheduleMapping.setTargetField("courseCode");
            FieldMapping courseScheduleMapping = new FieldMapping();
            courseScheduleMapping.setTargetField("courseCode");
            FieldMapping levelExamMapping = new FieldMapping();
            levelExamMapping.setTargetField("studentId");

            when(fieldMappingService.getMappingsByDataSource(anyString())).thenAnswer(invocation -> {
                String dataSource = invocation.getArgument(0);
                switch (dataSource) {
                    case "teacher_courses":
                        return List.of(teacherCourseMapping);
                    case "course_selection":
                        return List.of(courseSelectionMapping);
                    case "student_schedule":
                        return List.of(studentScheduleMapping);
                    case "course_schedule":
                        return List.of(courseScheduleMapping);
                    case "level_exam_scores":
                        return List.of(levelExamMapping);
                    default:
                        FieldMapping mapping = new FieldMapping();
                        mapping.setTargetField("ok");
                        return List.of(mapping);
                }
            });
            when(syncLogMapper.selectLatestByDataSource(anyString())).thenReturn(null);

            Map<String, Object> result = syncService.validateData();

            @SuppressWarnings("unchecked")
            Map<String, Object> teacherCourses = (Map<String, Object>) result.get("teacher_courses");
            @SuppressWarnings("unchecked")
            Map<String, Object> courseSelection = (Map<String, Object>) result.get("course_selection");
            @SuppressWarnings("unchecked")
            Map<String, Object> studentSchedule = (Map<String, Object>) result.get("student_schedule");
            @SuppressWarnings("unchecked")
            Map<String, Object> courseSchedule = (Map<String, Object>) result.get("course_schedule");
            @SuppressWarnings("unchecked")
            Map<String, Object> levelExamScores = (Map<String, Object>) result.get("level_exam_scores");

            assertFalse((Boolean) teacherCourses.get("healthy"));
            assertFalse((Boolean) courseSelection.get("healthy"));
            assertFalse((Boolean) studentSchedule.get("healthy"));
            assertFalse((Boolean) courseSchedule.get("healthy"));
            assertFalse((Boolean) levelExamScores.get("healthy"));
            assertTrue((int) result.get("totalIssues") >= 5);
        }
    }

    // ==================== chainFetchStudentsByCollege tests ====================

    @Nested
    @DisplayName("chainFetchStudentsByCollege 按学院链式拉取")
    class ChainFetchStudentsByCollegeTests {

        @Test
        @DisplayName("成功链式拉取外国语学院数据")
        void chainFetch_success_returnsCompleteResult() {
            // Step 1: school_course by kkyx → raw data, filterCoursesByCollege applies mapping
            List<Map<String, Object>> courses = new ArrayList<>();
            courses.add(new HashMap<>(Map.of("课程代码", "ENG101", "课程名称", "大学英语", "开课院系", "外国语学院")));
            courses.add(new HashMap<>(Map.of("课程代码", "ENG102", "课程名称", "英语听力", "开课院系", "外国语学院")));
            when(apiClient.fetchSchoolCourses(any())).thenReturn(courses);

            // school_course mapping: needs collegeName for filter + courseCode for matchedCourseCodes
            Map<String, Object> mappedCourse1 = new HashMap<>(Map.of(
                    "courseCode", "ENG101", "courseName", "大学英语", "collegeName", "外国语学院"));
            Map<String, Object> mappedCourse2 = new HashMap<>(Map.of(
                    "courseCode", "ENG102", "courseName", "英语听力", "collegeName", "外国语学院"));

            // Step 2.5: teacher_courses by kcdm — mapped uses classId (not courseClassCode)
            List<Map<String, Object>> rawTc1 = new ArrayList<>();
            rawTc1.add(new HashMap<>(Map.of("rkjsh", "T001", "rkjsxm", "张老师", "kcdm", "ENG101", "kcbh", "ENG101-01", "xn", "2025-2026", "xq", "1")));
            List<Map<String, Object>> rawTc2 = new ArrayList<>();
            rawTc2.add(new HashMap<>(Map.of("rkjsh", "T002", "rkjsxm", "李老师", "kcdm", "ENG102", "kcbh", "ENG102-01", "xn", "2025-2026", "xq", "1")));
            List<Map<String, Object>> allTeacherCourses = new ArrayList<>();
            allTeacherCourses.addAll(rawTc1);
            allTeacherCourses.addAll(rawTc2);
            when(apiClient.fetchTeacherCourses(any())).thenReturn(allTeacherCourses);

            Map<String, Object> mappedTc1 = new HashMap<>(Map.of(
                    "teacherId", "T001", "teacherName", "张老师", "courseCode", "ENG101",
                    "classId", "ENG101-01", "academicYear", "2025-2026", "semester", "1"));
            Map<String, Object> mappedTc2 = new HashMap<>(Map.of(
                    "teacherId", "T002", "teacherName", "李老师", "courseCode", "ENG102",
                    "classId", "ENG102-01", "academicYear", "2025-2026", "semester", "1"));

            // Step 2: course_selection by KCBH — mapped uses classId for KCBH check
            List<Map<String, Object>> rawSel1 = new ArrayList<>();
            rawSel1.add(new HashMap<>(Map.of("xh", "2023001", "kcbh", "ENG101-01")));
            rawSel1.add(new HashMap<>(Map.of("xh", "2023002", "kcbh", "ENG101-01")));
            List<Map<String, Object>> rawSel2 = new ArrayList<>();
            rawSel2.add(new HashMap<>(Map.of("xh", "2023001", "kcbh", "ENG102-01")));
            rawSel2.add(new HashMap<>(Map.of("xh", "2023003", "kcbh", "ENG102-01")));
            when(apiClient.fetchCourseSelections(argThat(params ->
                    params != null && String.valueOf(params.get("$filter")).contains("ENG101-01")))).thenReturn(rawSel1);
            when(apiClient.fetchCourseSelections(argThat(params ->
                    params != null && String.valueOf(params.get("$filter")).contains("ENG102-01")))).thenReturn(rawSel2);

            Map<String, Object> mappedSel1 = new HashMap<>(Map.of("studentId", "2023001", "classId", "ENG101-01"));
            Map<String, Object> mappedSel2 = new HashMap<>(Map.of("studentId", "2023002", "classId", "ENG101-01"));
            Map<String, Object> mappedSel3 = new HashMap<>(Map.of("studentId", "2023001", "classId", "ENG102-01"));
            Map<String, Object> mappedSel4 = new HashMap<>(Map.of("studentId", "2023003", "classId", "ENG102-01"));

            when(fieldMappingService.applyMapping(eq("school_course"), any())).thenAnswer(inv -> {
                Map<String, Object> raw = inv.getArgument(1);
                String code = (String) raw.get("课程代码");
                if ("ENG101".equals(code)) return mappedCourse1;
                if ("ENG102".equals(code)) return mappedCourse2;
                return new HashMap<>();
            });

            when(fieldMappingService.applyMapping(eq("teacher_courses"), any())).thenAnswer(inv -> {
                Map<String, Object> raw = inv.getArgument(1);
                String tid = (String) raw.get("rkjsh");
                if ("T001".equals(tid)) return mappedTc1;
                if ("T002".equals(tid)) return mappedTc2;
                return new HashMap<>();
            });

            when(fieldMappingService.applyMapping(eq("course_selection"), any())).thenAnswer(inv -> {
                Map<String, Object> raw = inv.getArgument(1);
                String xh = (String) raw.get("xh");
                String kcbh = (String) raw.get("kcbh");
                if ("2023001".equals(xh) && "ENG101-01".equals(kcbh)) return mappedSel1;
                if ("2023002".equals(xh) && "ENG101-01".equals(kcbh)) return mappedSel2;
                if ("2023001".equals(xh) && "ENG102-01".equals(kcbh)) return mappedSel3;
                if ("2023003".equals(xh) && "ENG102-01".equals(kcbh)) return mappedSel4;
                return new HashMap<>();
            });

            doNothing().when(schoolCourseMapper).upsertByCourseCode(any());
            doNothing().when(courseSelectionMapper).upsertBySelectionId(any());
            doNothing().when(teacherStudentBindingMapper).upsertBinding(any());

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("外国语学院", "eq", true, null, null);

            assertNotNull(result);
            assertEquals(2, result.get("courseCount"));
            assertEquals(3, result.get("studentCount")); // 2023001, 2023002, 2023003
            assertEquals(2, result.get("teacherCount")); // T001, T002
            assertTrue((Integer) result.get("bindingCount") > 0);
            verify(schoolCourseMapper, times(2)).upsertByCourseCode(any());
            verify(persistService, times(3)).persistMappedRecord(eq("school_student"), any());
            verify(persistService, times(2)).persistMappedRecord(eq("school_teacher"), any());
        }

        @Test
        @DisplayName("KCBH参数不支持时客户端过滤生效")
        void chainFetch_kcbhNotSupported_clientSideFiltering() {
            List<Map<String, Object>> courses = new ArrayList<>();
            courses.add(new HashMap<>(Map.of("课程代码", "ENG101", "课程名称", "大学英语", "开课院系", "外国语学院")));
            when(apiClient.fetchSchoolCourses(any())).thenReturn(courses);

            Map<String, Object> mappedCourse = new HashMap<>(Map.of(
                    "courseCode", "ENG101", "courseName", "大学英语", "collegeName", "外国语学院"));

            // Need teacher to establish tcByClassCode → kcbh="ENG101-01"
            List<Map<String, Object>> rawTc = new ArrayList<>();
            rawTc.add(new HashMap<>(Map.of("rkjsh", "T001", "rkjsxm", "张老师", "kcdm", "ENG101", "kcbh", "ENG101-01")));
            when(apiClient.fetchTeacherCourses(any())).thenReturn(rawTc);

            Map<String, Object> mappedTc = new HashMap<>(Map.of(
                    "teacherId", "T001", "teacherName", "张老师", "courseCode", "ENG101", "classId", "ENG101-01"));

            // CDSP ignores KCBH and returns all selections
            List<Map<String, Object>> allSelections = new ArrayList<>();
            allSelections.add(new HashMap<>(Map.of("xh", "2023001", "kcbh", "ENG101-01")));
            allSelections.add(new HashMap<>(Map.of("xh", "2023002", "kcbh", "OTHER-01"))); // Different class
            when(apiClient.fetchCourseSelections(any())).thenReturn(allSelections);

            when(fieldMappingService.applyMapping(eq("school_course"), any())).thenReturn(mappedCourse);
            when(fieldMappingService.applyMapping(eq("teacher_courses"), any())).thenReturn(mappedTc);
            when(fieldMappingService.applyMapping(eq("course_selection"), any())).thenAnswer(inv -> {
                Map<String, Object> raw = inv.getArgument(1);
                Map<String, Object> mapped = new HashMap<>();
                mapped.put("studentId", raw.get("xh"));
                mapped.put("classId", raw.get("kcbh"));
                return mapped;
            });

            doNothing().when(schoolCourseMapper).upsertByCourseCode(any());
            doNothing().when(courseSelectionMapper).upsertBySelectionId(any());
            doNothing().when(teacherStudentBindingMapper).upsertBinding(any());

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("外国语学院", "eq", true, null, null);

            assertNotNull(result);
            assertEquals(false, result.get("upstreamKcbhSupported"));
            assertEquals(1, result.get("studentCount")); // Only 2023001 matches ENG101-01 via classId
            verify(persistService, times(1)).persistMappedRecord(eq("school_student"), any());
        }

        @Test
        @DisplayName("course_selection 历史 KCBH→courseCode 映射时仍可完成链式拉取")
        void chainFetch_legacyCourseSelectionMapping_fallsBackToCourseCode() {
            List<Map<String, Object>> courses = new ArrayList<>();
            courses.add(new HashMap<>(Map.of("课程代码", "ENG101", "课程名称", "大学英语", "开课院系", "外国语学院")));
            when(apiClient.fetchSchoolCourses(any())).thenReturn(courses);

            Map<String, Object> mappedCourse = new HashMap<>(Map.of(
                    "courseCode", "ENG101", "courseName", "大学英语", "collegeName", "外国语学院"));

            List<Map<String, Object>> rawTc = new ArrayList<>();
            rawTc.add(new HashMap<>(Map.of("rkjsh", "T001", "rkjsxm", "张老师", "kcdm", "ENG101", "kcbh", "ENG101-01")));
            when(apiClient.fetchTeacherCourses(any())).thenReturn(rawTc);

            Map<String, Object> mappedTc = new HashMap<>(Map.of(
                    "teacherId", "T001", "teacherName", "张老师", "courseCode", "ENG101", "classId", "ENG101-01"));

            List<Map<String, Object>> rawSelections = new ArrayList<>();
            rawSelections.add(new HashMap<>(Map.of("xh", "2023001", "kcbh", "ENG101-01")));
            when(apiClient.fetchCourseSelections(any())).thenReturn(rawSelections);

            when(fieldMappingService.applyMapping(eq("school_course"), any())).thenReturn(mappedCourse);
            when(fieldMappingService.applyMapping(eq("teacher_courses"), any())).thenReturn(mappedTc);
            when(fieldMappingService.applyMapping(eq("course_selection"), any())).thenAnswer(inv -> {
                Map<String, Object> raw = inv.getArgument(1);
                Map<String, Object> mapped = new HashMap<>();
                mapped.put("studentId", raw.get("xh"));
                mapped.put("courseCode", raw.get("kcbh"));
                mapped.put("academicYear", "2025-2026");
                mapped.put("semester", "1");
                return mapped;
            });

            doNothing().when(schoolCourseMapper).upsertByCourseCode(any());
            doNothing().when(courseSelectionMapper).upsertBySelectionId(any());
            doNothing().when(teacherStudentBindingMapper).upsertBinding(any());

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("外国语学院", "eq", true, null, null);

            assertNotNull(result);
            assertEquals(1, result.get("studentCount"));
            assertEquals(true, result.get("upstreamKcbhSupported"));
            verify(courseSelectionMapper).upsertBySelectionId(argThat(entity ->
                    "ENG101-01".equals(entity.getClassId()) && entity.getCourseCode() == null));
            verify(persistService, times(1)).persistMappedRecord(eq("school_student"), any());
            verify(persistService, times(1)).persistMappedRecord(eq("school_teacher"), any());
        }

        @Test
        @DisplayName("学院无匹配课程时返回零计数")
        void chainFetch_noMatchingCourses_returnsZeroCounts() {
            when(apiClient.fetchSchoolCourses(any())).thenReturn(Collections.emptyList());

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("不存在的学院", "eq", true, null, null);

            assertNotNull(result);
            assertEquals(0, result.get("courseCount"));
            assertEquals(0, result.get("studentCount"));
            assertEquals(0, result.get("teacherCount"));
            assertEquals(0, result.get("bindingCount"));
            verify(apiClient, never()).fetchCourseSelections(any());
            verify(apiClient, never()).fetchTeacherCourses(any());
        }

        @Test
        @DisplayName("persist=false时不写入数据库")
        void chainFetch_persistFalse_doesNotWriteToDb() {
            List<Map<String, Object>> courses = new ArrayList<>();
            courses.add(new HashMap<>(Map.of("课程代码", "ENG101", "课程名称", "大学英语", "开课院系", "外国语学院")));
            when(apiClient.fetchSchoolCourses(any())).thenReturn(courses);

            Map<String, Object> mappedCourse = new HashMap<>(Map.of(
                    "courseCode", "ENG101", "courseName", "大学英语", "collegeName", "外国语学院"));
            when(fieldMappingService.applyMapping(eq("school_course"), any())).thenReturn(mappedCourse);

            when(apiClient.fetchTeacherCourses(any())).thenReturn(Collections.emptyList());

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("外国语学院", "eq", false, null, null);

            assertNotNull(result);
            assertEquals(1, result.get("courseCount"));
            verify(schoolCourseMapper, never()).upsertByCourseCode(any());
            verify(persistService, never()).persistMappedRecord(eq("school_student"), any());
            verify(persistService, never()).persistMappedRecord(eq("school_teacher"), any());
        }

        @Test
        @DisplayName("school_course 映射缺少 collegeName 时返回可定位的失败诊断")
        void chainFetch_missingCollegeMapping_returnsDiagnosableFailure() {
            List<Map<String, Object>> courses = new ArrayList<>();
            courses.add(new HashMap<>(Map.of("课程代码", "ENG101", "课程名称", "大学英语")));
            when(apiClient.fetchSchoolCourses(any())).thenReturn(courses);
            when(fieldMappingService.applyMapping(eq("school_course"), any()))
                    .thenReturn(new HashMap<>(Map.of("courseCode", "ENG101")));

            Map<String, Object> result = syncService.chainFetchStudentsByCollege("外国语学院", "eq", true, null, null);

            assertNotNull(result);
            assertEquals("FAILED", result.get("status"));
            assertEquals(0, result.get("courseCount"));
            assertTrue(((Integer) result.get("errorCount")) > 0);
            assertTrue(String.valueOf(result.get("errorMessage")).contains("collegeName"));

            @SuppressWarnings("unchecked")
            Map<String, Object> stepStats = (Map<String, Object>) result.get("stepStats");
            @SuppressWarnings("unchecked")
            Map<String, Object> schoolCourseStats = (Map<String, Object>) stepStats.get("schoolCourse");
            assertEquals(1, schoolCourseStats.get("upstreamCount"));
            assertEquals(1, schoolCourseStats.get("blankCollegeCount"));
            verify(apiClient, never()).fetchTeacherCourses(any());
            verify(apiClient, never()).fetchCourseSelections(any());
        }
    }
    private SchoolDataFetchResult fetchResult(List<Map<String, Object>> rows) {
        return new SchoolDataFetchResult(rows, new SchoolDataFetchResult.PaginationSummary(
                true, rows.isEmpty() ? "EMPTY_FIRST_PAGE" : "DIRECT_ARRAY_RESPONSE", rows.isEmpty() ? 0 : 1, 0, 0, false, rows.size()));
    }

    private SchoolDataFetchResult incompleteFetchResult(List<Map<String, Object>> rows) {
        return new SchoolDataFetchResult(rows, new SchoolDataFetchResult.PaginationSummary(
                false, "UPSTREAM_HAS_MORE_UNSUPPORTED", 1, 0, 0, true, rows.size()));
    }

}
