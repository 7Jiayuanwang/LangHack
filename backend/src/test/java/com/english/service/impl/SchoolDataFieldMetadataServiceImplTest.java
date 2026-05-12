package com.english.service.impl;

import com.english.dto.SchoolDataFilterFieldDto;
import com.english.entity.FieldMapping;
import com.english.service.FieldMappingService;
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
class SchoolDataFieldMetadataServiceImplTest {

    @Mock
    private FieldMappingService fieldMappingService;

    private SchoolDataFieldMetadataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolDataFieldMetadataServiceImpl(fieldMappingService);
    }

    @Test
    @DisplayName("实体字段应生成列表查询字段元数据")
    void getFilterFieldMap_includesEntityFields() {
        when(fieldMappingService.getMappingsByDataSource("course_selection")).thenReturn(List.of());

        Map<String, SchoolDataFilterFieldDto> fields = service.getFilterFieldMap("course_selection");

        assertTrue(fields.containsKey("studentId"));
        SchoolDataFilterFieldDto studentId = fields.get("studentId");
        assertEquals("student_id", studentId.getColumn());
        assertEquals("STRING", studentId.getType());
        assertEquals(List.of("eq", "contains", "in"), studentId.getOperators());
        assertTrue(studentId.getCapabilities().isListQuery());
        assertTrue(studentId.getCapabilities().isLocalSyncFilter());
        assertTrue(studentId.getCapabilities().isUpstreamSyncFilter());

        assertFalse(fields.containsKey("id"));
        assertFalse(fields.containsKey("createdAt"));
        assertFalse(fields.containsKey("updatedAt"));
        assertFalse(fields.containsKey("syncTime"));
        assertFalse(fields.containsKey("syncSource"));
    }

    @Test
    @DisplayName("映射字段应合并进可过滤字段集合")
    void getFilterFieldMap_mergesMappedFields() {
        FieldMapping mapping = new FieldMapping();
        mapping.setSourceField("KCMC");
        mapping.setTargetField("customCourseName");
        mapping.setTargetType("string");
        when(fieldMappingService.getMappingsByDataSource("school_course")).thenReturn(List.of(mapping));

        Map<String, SchoolDataFilterFieldDto> fields = service.getFilterFieldMap("school_course");

        assertTrue(fields.containsKey("customCourseName"));
        SchoolDataFilterFieldDto field = fields.get("customCourseName");
        assertEquals("KCMC", field.getSourceField());
        assertEquals("STRING", field.getType());
        assertEquals(List.of("eq", "contains", "in"), field.getOperators());
        assertFalse(field.getCapabilities().isListQuery());
        assertTrue(field.getCapabilities().isLocalSyncFilter());
        assertFalse(field.getCapabilities().isUpstreamSyncFilter());
    }

    @Test
    @DisplayName("数字和布尔字段应生成对应操作符")
    void getFilterFieldMap_resolvesOperatorsByType() {
        when(fieldMappingService.getMappingsByDataSource("level_exam_scores")).thenReturn(List.of());

        Map<String, SchoolDataFilterFieldDto> fields = service.getFilterFieldMap("level_exam_scores");

        assertEquals("NUMBER", fields.get("score").getType());
        assertEquals(List.of("eq", "gte", "lte", "in"), fields.get("score").getOperators());

        assertEquals("BOOLEAN", fields.get("isPassed").getType());
        assertEquals(List.of("eq"), fields.get("isPassed").getOperators());
    }

    @Test
    @DisplayName("获取字段列表时应按字段名排序")
    void getFilterFields_sortsByFieldName() {
        when(fieldMappingService.getMappingsByDataSource("student_schedule")).thenReturn(List.of());

        List<SchoolDataFilterFieldDto> fields = service.getFilterFields("student_schedule");

        assertFalse(fields.isEmpty());
        for (int i = 1; i < fields.size(); i++) {
            assertTrue(fields.get(i - 1).getField().compareTo(fields.get(i).getField()) <= 0);
        }
    }

    @Test
    @DisplayName("teacher_courses 应作为 teaching_class 表的可查询别名")
    void getFilterFieldMap_teacherCoursesAlias_success() {
        when(fieldMappingService.getMappingsByDataSource("teacher_courses")).thenReturn(List.of());

        Map<String, SchoolDataFilterFieldDto> fields = service.getFilterFieldMap("teacher_courses");

        assertTrue(fields.containsKey("teacherId"));
        assertTrue(fields.containsKey("classId"));
        assertEquals("teacher_id", fields.get("teacherId").getColumn());
        assertEquals("教师工号", fields.get("teacherId").getLabel());
        assertTrue(fields.get("teacherId").getCapabilities().isListQuery());
        assertTrue(fields.get("teacherId").getCapabilities().isUpstreamSyncFilter());
    }
}
