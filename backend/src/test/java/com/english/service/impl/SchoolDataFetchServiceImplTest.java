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
    @DisplayName("student_schedule 手动同步缺少学号时抛出异常")
    void fetchRawData_studentScheduleWithoutStudentId_throwsException() {
        SchoolDataSyncFilterRequest request = request("academicYear", "eq", "2025-2026");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.fetchRawData("student_schedule", request));

        assertTrue(ex.getMessage().contains("必须提供学号"));
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
