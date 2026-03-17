package com.english.controller;
import com.english.test.TestUtils;

import com.english.dto.Result;
import com.english.entity.Course;
import com.english.service.CourseService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CourseControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CourseService courseService;

    @InjectMocks
    private CourseController courseController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(courseController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/courses ==========

    @Test
    @DisplayName("GET /api/courses - returns paginated course list")
    void getCourseList_returnsPaginatedList() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of(createMockCourse(1L, "English 101")));
        result.put("total", 1L);
        result.put("current", 1);
        result.put("size", 10);

        when(courseService.getCoursesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/courses")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("GET /api/courses - uses default pagination when no params")
    void getCourseList_defaultPagination() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(courseService.getCoursesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(courseService).getCoursesByPage(paramsCaptor.capture());
        Assertions.assertEquals(1, paramsCaptor.getValue().get("page"));
        Assertions.assertEquals(10, paramsCaptor.getValue().get("size"));
    }

    @Test
    @DisplayName("GET /api/courses - with keyword search parameter")
    void getCourseList_withKeyword() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of(createMockCourse(1L, "Advanced English")));
        result.put("total", 1L);

        when(courseService.getCoursesByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/courses")
                        .param("keyword", "Advanced"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(courseService).getCoursesByPage(paramsCaptor.capture());
        Assertions.assertEquals("Advanced", paramsCaptor.getValue().get("keyword"));
    }

    @Test
    @DisplayName("GET /api/courses - exception returns error")
    void getCourseList_exception_returnsError() throws Exception {
        when(courseService.getCoursesByPage(any(Map.class)))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== GET /api/courses/active ==========

    @Test
    @DisplayName("GET /api/courses/active - returns active courses")
    void getActiveCourses_returnsList() throws Exception {
        List<Course> courses = List.of(
                createMockCourse(1L, "English 101"),
                createMockCourse(2L, "English 201")
        );

        when(courseService.getActiveCourses()).thenReturn(courses);

        mockMvc.perform(get("/api/courses/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ========== GET /api/courses/{id} ==========

    @Test
    @DisplayName("GET /api/courses/{id} - existing course returns details")
    void getCourseById_existingCourse_returnsDetails() throws Exception {
        Course course = createMockCourse(1L, "English 101");
        course.setDescription("Beginner English course");

        when(courseService.getCourseById(1L)).thenReturn(course);

        mockMvc.perform(get("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("English 101"))
                .andExpect(jsonPath("$.data.description").value("Beginner English course"));
    }

    @Test
    @DisplayName("GET /api/courses/{id} - non-existent course returns error")
    void getCourseById_nonExistent_returnsError() throws Exception {
        when(courseService.getCourseById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/courses/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课程不存在"));
    }

    // ========== POST /api/courses ==========

    @Test
    @DisplayName("POST /api/courses - ADMIN creates course successfully")
    void createCourse_admin_success() throws Exception {
        Course course = new Course();
        course.setName("New Course");
        course.setDescription("A new course");

        doNothing().when(courseService).createCourse(any(Course.class));

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(courseService).createCourse(any(Course.class));
    }

    @Test
    @DisplayName("POST /api/courses - exception returns error")
    void createCourse_exception_returnsError() throws Exception {
        Course course = new Course();
        course.setName("Duplicate Course");

        doThrow(new RuntimeException("Course name already exists"))
                .when(courseService).createCourse(any(Course.class));

        mockMvc.perform(post("/api/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== PUT /api/courses/{id} ==========

    @Test
    @DisplayName("PUT /api/courses/{id} - update sets id from path variable")
    void updateCourse_setsIdFromPath() throws Exception {
        Course course = new Course();
        course.setName("Updated Course");
        course.setDescription("Updated description");

        doNothing().when(courseService).updateCourse(any(Course.class));

        mockMvc.perform(put("/api/courses/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<Course> courseCaptor = ArgumentCaptor.forClass(Course.class);
        verify(courseService).updateCourse(courseCaptor.capture());
        Assertions.assertEquals(5L, courseCaptor.getValue().getId());
    }

    // ========== DELETE /api/courses/{id} ==========

    @Test
    @DisplayName("DELETE /api/courses/{id} - soft delete success")
    void deleteCourse_success() throws Exception {
        doNothing().when(courseService).deleteCourse(1L);

        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(courseService).deleteCourse(1L);
    }

    @Test
    @DisplayName("DELETE /api/courses/{id} - exception returns error")
    void deleteCourse_exception_returnsError() throws Exception {
        doThrow(new RuntimeException("Course has active classes"))
                .when(courseService).deleteCourse(1L);

        mockMvc.perform(delete("/api/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== GET /api/courses/stats ==========

    @Test
    @DisplayName("GET /api/courses/stats - returns course statistics")
    void getCourseStats_returnsStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCourses", 10);
        stats.put("activeCourses", 8);

        when(courseService.getCourseStats()).thenReturn(stats);

        mockMvc.perform(get("/api/courses/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalCourses").value(10))
                .andExpect(jsonPath("$.data.activeCourses").value(8));
    }

    // ========== Helper Methods ==========

    private Course createMockCourse(Long id, String name) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        return course;
    }
}
