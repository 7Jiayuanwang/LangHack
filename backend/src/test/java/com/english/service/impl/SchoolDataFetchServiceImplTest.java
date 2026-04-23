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
import static org.mockito.Mockito.doReturn;
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
        SchoolDataSyncFilterRequest request = new SchoolDataSyncFilterRequest();
        SchoolDataSyncFilterRequest.FilterCondition condition = new SchoolDataSyncFilterRequest.FilterCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValue(value);

        SchoolDataSyncFilterRequest.FilterGroup group = new SchoolDataSyncFilterRequest.FilterGroup();
        group.setConditions(List.of(condition));
        request.setFilterGroups(List.of(group));
        return request;
    }
}
