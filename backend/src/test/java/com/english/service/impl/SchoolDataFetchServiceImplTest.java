package com.english.service.impl;

import com.english.dto.SchoolDataSyncFilterRequest;
import com.english.exception.BusinessException;
import com.english.service.FieldMappingService;
import com.english.service.SchoolDataApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolDataFetchServiceImplTest {

    @Mock
    private SchoolDataApiClient apiClient;

    @Mock
    private FieldMappingService fieldMappingService;

    private SchoolDataFetchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolDataFetchServiceImpl(apiClient, fieldMappingService);
    }

    @Test
    @DisplayName("同步本地过滤应匹配映射后的 targetField")
    void fetchRawData_filtersByMappedTargetField() {
        SchoolDataSyncFilterRequest request = request("courseCode", "eq", "ENG101");
        Map<String, Object> rawRow = Map.of("KCDM", "ENG101", "KCMC", "大学英语");
        Map<String, Object> mappedRow = Map.of("courseCode", "ENG101", "courseName", "大学英语");

        when(apiClient.fetchSchoolCourses(Map.of("kcdm", "ENG101"))).thenReturn(List.of(rawRow));
        when(fieldMappingService.applyMapping("school_course", rawRow)).thenReturn(mappedRow);

        List<Map<String, Object>> rows = service.fetchRawData("school_course", request);

        assertEquals(1, rows.size());
        assertEquals("ENG101", rows.get(0).get("KCDM"));
    }

    @Test
    @DisplayName("同步过滤应支持数据源 sourceField 并构造上游原生参数")
    void fetchRawData_filtersBySourceField() {
        SchoolDataSyncFilterRequest request = request("kcdm", "eq", "ENG101");
        Map<String, Object> rawRow = Map.of("课程代码", "ENG101", "课程名称", "大学英语");
        Map<String, Object> mappedRow = Map.of("courseCode", "ENG101", "courseName", "大学英语");

        when(apiClient.fetchSchoolCourses(Map.of("kcdm", "ENG101"))).thenReturn(List.of(rawRow));
        when(fieldMappingService.applyMapping("school_course", rawRow)).thenReturn(mappedRow);

        List<Map<String, Object>> rows = service.fetchRawData("school_course", request);

        assertEquals(1, rows.size());
        assertEquals("ENG101", rows.get(0).get("课程代码"));
    }

    @Test
    @DisplayName("同步过滤应大小写不敏感匹配上游字段")
    void fetchRawData_filtersBySourceFieldIgnoreCase() {
        SchoolDataSyncFilterRequest request = request("KCBH", "eq", "CLS001");
        Map<String, Object> rawRow = Map.of("kcbh", "CLS001");

        when(apiClient.fetchCourseSelections(Map.of("KCBH", "CLS001"))).thenReturn(List.of(rawRow));

        List<Map<String, Object>> rows = service.fetchRawData("course_selection", request);

        assertEquals(1, rows.size());
        assertEquals("CLS001", rows.get(0).get("kcbh"));
    }

    @Test
    @DisplayName("student_schedule 手动同步缺少学号时抛出异常")
    void fetchRawData_studentScheduleWithoutStudentId_throwsException() {
        SchoolDataSyncFilterRequest request = request("academicYear", "eq", "2025-2026");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.fetchRawData("student_schedule", request));

        assertTrue(ex.getMessage().contains("必须提供学号"));
    }

    @Test
    @DisplayName("student_schedule 手动同步应支持学校原字段筛选并二次过滤生效")
    void fetchRawData_studentScheduleSourceFieldFilters_success() {
        SchoolDataSyncFilterRequest request = request(
                condition("xh", "eq", "2023001"),
                condition("xn", "eq", "2025"),
                condition("xq", "eq", "秋"),
                condition("kcmc", "eq", "大学英语")
        );
        Map<String, Object> rawMatched = Map.of("学号", "2023001", "学年", "2025", "学期", "秋", "课程名称", "大学英语");
        Map<String, Object> rawOtherCourse = Map.of("学号", "2023001", "学年", "2025", "学期", "秋", "课程名称", "高等数学");
        Map<String, Object> mappedMatched = Map.of("studentId", "2023001", "academicYear", "2025", "semester", "秋", "courseName", "大学英语");
        Map<String, Object> mappedOtherCourse = Map.of("studentId", "2023001", "academicYear", "2025", "semester", "秋", "courseName", "高等数学");

        when(apiClient.fetchStudentSchedules("2023001", "2025", 1)).thenReturn(List.of(rawMatched, rawOtherCourse));
        when(fieldMappingService.applyMapping("student_schedule", rawMatched)).thenReturn(mappedMatched);
        when(fieldMappingService.applyMapping("student_schedule", rawOtherCourse)).thenReturn(mappedOtherCourse);

        List<Map<String, Object>> rows = service.fetchRawData("student_schedule", request);

        assertEquals(1, rows.size());
        assertEquals("大学英语", rows.get(0).get("课程名称"));
        verify(apiClient).fetchStudentSchedules("2023001", "2025", 1);
    }

    @Test
    @DisplayName("student_schedule 手动同步摘要应记录真实拉取行数")
    void fetchRawDataWithSummary_studentScheduleFetchedCount_success() {
        SchoolDataSyncFilterRequest request = request("xh", "eq", "2023001");
        Map<String, Object> raw1 = Map.of("xh", "2023001", "课程名称", "大学英语");
        Map<String, Object> raw2 = Map.of("xh", "2023001", "课程名称", "高等数学");

        when(apiClient.fetchStudentSchedules("2023001", null, null)).thenReturn(List.of(raw1, raw2));

        SchoolDataFetchResult result = service.fetchRawDataWithSummary("student_schedule", request);

        assertEquals(2, result.getRows().size());
        assertEquals(2, result.getPaginationSummary().getFetchedRowCount());
        verify(apiClient).fetchStudentSchedules("2023001", null, null);
    }

    @Test
    @DisplayName("student_basic_info 路由到 fetchStudentBasicInfos")
    void fetchRawData_studentBasicInfo_routesToFetchStudentBasicInfos() {
        Map<String, Object> raw = Map.of("xh", "2023001", "xm", "张三");
        when(apiClient.fetchStudentBasicInfos(any())).thenReturn(List.of(raw));

        List<Map<String, Object>> result = service.fetchRawData("student_basic_info", null);

        assertEquals(1, result.size());
        verify(apiClient).fetchStudentBasicInfos(any());
    }

    @Test
    @DisplayName("旧 fetchRawData 接口应拒绝不完整的上游结果")
    void fetchRawData_incompleteSummary_throwsException() {
        SchoolDataFetchServiceImpl spyService = spy(service);
        SchoolDataFetchResult incomplete = new SchoolDataFetchResult(
                List.of(Map.of("KCDM", "ENG101")),
                new SchoolDataFetchResult.PaginationSummary(
                        false, "UPSTREAM_HAS_MORE_UNSUPPORTED", 1, 0, 0, true, 1));
        doReturn(incomplete).when(spyService).fetchRawDataWithSummary("school_course", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> spyService.fetchRawData("school_course", null));

        assertTrue(ex.getMessage().contains("学校数据未完整拉取"));
    }

    private SchoolDataSyncFilterRequest request(String field, String operator, String value) {
        return request(condition(field, operator, value));
    }

    private SchoolDataSyncFilterRequest request(SchoolDataSyncFilterRequest.FilterCondition... conditions) {
        SchoolDataSyncFilterRequest request = new SchoolDataSyncFilterRequest();

        SchoolDataSyncFilterRequest.FilterGroup group = new SchoolDataSyncFilterRequest.FilterGroup();
        group.setConditions(List.of(conditions));
        request.setFilterGroups(List.of(group));
        return request;
    }

    private SchoolDataSyncFilterRequest.FilterCondition condition(String field, String operator, String value) {
        SchoolDataSyncFilterRequest.FilterCondition condition = new SchoolDataSyncFilterRequest.FilterCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }
}
