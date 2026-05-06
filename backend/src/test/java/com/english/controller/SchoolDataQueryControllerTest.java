package com.english.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.english.dto.SchoolDataFilterFieldDto;
import com.english.exception.GlobalExceptionHandler;
import com.english.mapper.*;
import com.english.service.SchoolDataFieldMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SchoolDataQueryControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private SchoolDataQueryController controller;

    @Mock private AdministrativeClassMapper administrativeClassMapper;
    @Mock private SchoolCourseMapper schoolCourseMapper;
    @Mock private TeachingClassMapper teachingClassMapper;
    @Mock private CourseScheduleMapper courseScheduleMapper;
    @Mock private CourseSelectionMapper courseSelectionMapper;
    @Mock private LevelExamScoreMapper levelExamScoreMapper;
    @Mock private TeachingPlanMapper teachingPlanMapper;
    @Mock private TeachingPlanCourseMapper teachingPlanCourseMapper;
    @Mock private StudentScheduleMapper studentScheduleMapper;
    @Mock private SchoolStudentMapper schoolStudentMapper;
    @Mock private SchoolTeacherMapper schoolTeacherMapper;
    @Mock private TeacherStudentBindingMapper teacherStudentBindingMapper;
    @Mock private SchoolDataFieldMetadataService fieldMetadataService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("概览应使用 teacher_courses 键对齐前端官方数据源 Tab")
    void getOverview_usesTeacherCoursesKey_success() throws Exception {
        when(teachingClassMapper.selectCount(null)).thenReturn(5L);
        when(schoolStudentMapper.selectCount(null)).thenReturn(12L);
        when(schoolTeacherMapper.selectCount(null)).thenReturn(3L);
        when(teacherStudentBindingMapper.selectCount(null)).thenReturn(20L);

        mockMvc.perform(get("/api/admin/school-data/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.teacher_courses").value(5))
                .andExpect(jsonPath("$.data.teaching_class").doesNotExist())
                .andExpect(jsonPath("$.data.school_student").value(12))
                .andExpect(jsonPath("$.data.school_teacher").value(3))
                .andExpect(jsonPath("$.data.teacher_student_binding").value(20));
    }

    @Test
    @DisplayName("获取字段元数据成功")
    void getFilterFields_success() throws Exception {
        SchoolDataFilterFieldDto field = new SchoolDataFilterFieldDto();
        field.setField("studentId");
        field.setLabel("学号");
        field.setType("STRING");
        field.setOperators(List.of("eq", "contains", "in"));
        when(fieldMetadataService.getFilterFields("course_selection")).thenReturn(List.of(field));

        mockMvc.perform(get("/api/admin/school-data/course_selection/filter-fields"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].field").value("studentId"))
                .andExpect(jsonPath("$.data[0].operators[1]").value("contains"));
    }

    @Test
    @DisplayName("通用查询支持 contains 和分页")
    void querySchoolData_containsFilter_success() throws Exception {
        SchoolDataFilterFieldDto field = stringField("className", "class_name", true);
        when(fieldMetadataService.getFilterFieldMap("administrative_class")).thenReturn(Map.of("className", field));
        when(administrativeClassMapper.selectPage(any(), any())).thenReturn(new Page<>(2, 5));

        mockMvc.perform(post("/api/admin/school-data/administrative_class/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "page": 2,
                                  "size": 5,
                                  "filterGroups": [
                                    {
                                      "relation": "AND",
                                      "conditions": [
                                        {"field": "className", "operator": "contains", "value": "英语"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Page> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(administrativeClassMapper).selectPage(pageCaptor.capture(), any());
        assertTrue(pageCaptor.getValue().getCurrent() == 2L);
        assertTrue(pageCaptor.getValue().getSize() == 5L);
    }

    @Test
    @DisplayName("teacher_courses 通用查询应落到教学班表")
    void querySchoolData_teacherCoursesAlias_success() throws Exception {
        SchoolDataFilterFieldDto field = stringField("teacherId", "teacher_id", true);
        when(fieldMetadataService.getFilterFieldMap("teacher_courses")).thenReturn(Map.of("teacherId", field));
        when(teachingClassMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        mockMvc.perform(post("/api/admin/school-data/teacher_courses/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "filterGroups": [
                                    {
                                      "conditions": [
                                        {"field": "teacherId", "operator": "eq", "value": "T001"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(teachingClassMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("学期筛选应支持中文春并归一化")
    void querySchoolData_semesterChineseValue_success() throws Exception {
        SchoolDataFilterFieldDto field = numberField("semester", "semester", true);
        when(fieldMetadataService.getFilterFieldMap("student_schedule")).thenReturn(Map.of("semester", field));
        when(studentScheduleMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        mockMvc.perform(post("/api/admin/school-data/student_schedule/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "filterGroups": [
                                    {
                                      "conditions": [
                                        {"field": "semester", "operator": "eq", "value": "春"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("school_student 通用查询应命中链式拉取本地表")
    void querySchoolData_schoolStudent_success() throws Exception {
        SchoolDataFilterFieldDto field = stringField("collegeName", "college_name", true);
        when(fieldMetadataService.getFilterFieldMap("school_student")).thenReturn(Map.of("collegeName", field));
        when(schoolStudentMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        mockMvc.perform(post("/api/admin/school-data/school_student/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "filterGroups": [
                                    {
                                      "conditions": [
                                        {"field": "collegeName", "operator": "eq", "value": "外国语学院"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(schoolStudentMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("非法字段应返回服务错误")
    void querySchoolData_invalidField_returnsError() throws Exception {
        when(fieldMetadataService.getFilterFieldMap("administrative_class")).thenReturn(Map.of());

        mockMvc.perform(post("/api/admin/school-data/administrative_class/query")
                        .contentType("application/json")
                        .content("""
                                {
                                  "filterGroups": [
                                    {
                                      "conditions": [
                                        {"field": "unknownField", "operator": "eq", "value": "x"}
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("非法操作符应返回服务错误")
    void querySchoolData_invalidOperator_returnsError() throws Exception {
        SchoolDataFilterFieldDto field = stringField("className", "class_name", true);
        when(fieldMetadataService.getFilterFieldMap("administrative_class")).thenReturn(Map.of("className", field));

        mockMvc.perform(post("/api/admin/school-data/administrative_class/query")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "filterGroups", List.of(Map.of(
                                        "conditions", List.of(Map.of(
                                                "field", "className",
                                                "operator", "gte",
                                                "value", "英语"
                                        ))
                                ))
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private SchoolDataFilterFieldDto stringField(String name, String column, boolean listQuery) {
        SchoolDataFilterFieldDto field = new SchoolDataFilterFieldDto();
        field.setField(name);
        field.setColumn(column);
        field.setType("STRING");
        field.setOperators(List.of("eq", "contains", "in"));
        field.getCapabilities().setListQuery(listQuery);
        return field;
    }

    private SchoolDataFilterFieldDto numberField(String name, String column, boolean listQuery) {
        SchoolDataFilterFieldDto field = new SchoolDataFilterFieldDto();
        field.setField(name);
        field.setColumn(column);
        field.setType("NUMBER");
        field.setOperators(List.of("eq", "gte", "lte", "in"));
        field.getCapabilities().setListQuery(listQuery);
        return field;
    }
}
