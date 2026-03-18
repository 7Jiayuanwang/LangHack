package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.*;
import com.english.exception.BusinessException;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.UserService;
import com.english.mapper.TextMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.UnitWordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("AssignmentController Enhanced Tests")
class AssignmentControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AssignmentService assignmentService;
    @Mock private UserService userService;
    @Mock private ClassService classService;
    @Mock private TextMapper textMapper;
    @Mock private WordMapper wordMapper;
    @Mock private UnitWordMapper unitWordMapper;

    @InjectMocks
    private AssignmentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        User teacherUser = new User();
        teacherUser.setId(10L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setUpSecurityContext(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ========== GET /api/assignments ==========

    @Test
    @DisplayName("GET /api/assignments - paginated list success")
    void getAssignments_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(assignmentService.getAssignmentsByPage(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/assignments").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/assignments/{id} ==========

    @Test
    @DisplayName("GET /api/assignments/{id} - success with teacher permission")
    void getAssignmentById_success() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setTitle("Test Assignment");
        when(assignmentService.getAssignmentById(1L)).thenReturn(assignment);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/assignments/{id} - not found")
    void getAssignmentById_notFound() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 99L)).thenReturn(true);
        when(assignmentService.getAssignmentById(99L)).thenReturn(null);

        mockMvc.perform(get("/api/assignments/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    @DisplayName("GET /api/assignments/{id} - teacher no permission")
    void getAssignmentById_noPermission() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看此任务"));
    }

    // ========== POST /api/assignments ==========

    @Test
    @DisplayName("POST /api/assignments - create assignment success")
    void createAssignment_success() throws Exception {
        com.english.entity.Class classEntity = new com.english.entity.Class();
        classEntity.setId(1L);
        classEntity.setTeacherId(10L);
        when(classService.getClassById(1L)).thenReturn(classEntity);
        doNothing().when(assignmentService).createAssignment(any(Assignment.class));

        Assignment assignment = new Assignment();
        assignment.setTitle("New Assignment");
        assignment.setClassId(1L);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务创建成功"));
    }

    @Test
    @DisplayName("POST /api/assignments - teacher no permission for class")
    void createAssignment_noClassPermission() throws Exception {
        com.english.entity.Class classEntity = new com.english.entity.Class();
        classEntity.setId(2L);
        classEntity.setTeacherId(99L); // different teacher
        when(classService.getClassById(2L)).thenReturn(classEntity);

        Assignment assignment = new Assignment();
        assignment.setTitle("New Assignment");
        assignment.setClassId(2L);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限为该班级创建任务"));
    }

    // ========== PUT /api/assignments/{id} ==========

    @Test
    @DisplayName("PUT /api/assignments/{id} - update success")
    void updateAssignment_success() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        doNothing().when(assignmentService).updateAssignment(any(Assignment.class));

        Assignment assignment = new Assignment();
        assignment.setTitle("Updated Assignment");

        mockMvc.perform(put("/api/assignments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务更新成功"));
    }

    @Test
    @DisplayName("PUT /api/assignments/{id} - teacher no permission")
    void updateAssignment_noPermission() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        Assignment assignment = new Assignment();
        assignment.setTitle("Updated");

        mockMvc.perform(put("/api/assignments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限修改此任务"));
    }

    // ========== DELETE /api/assignments/{id} ==========

    @Test
    @DisplayName("DELETE /api/assignments/{id} - delete success")
    void deleteAssignment_success() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        doNothing().when(assignmentService).deleteAssignment(1L);

        mockMvc.perform(delete("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务删除成功"));
    }

    @Test
    @DisplayName("DELETE /api/assignments/{id} - teacher no permission")
    void deleteAssignment_noPermission() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限删除此任务"));
    }

    // ========== GET /api/assignments/teacher/stats ==========

    @Test
    @DisplayName("GET /api/assignments/teacher/stats - success")
    void getTeacherStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalAssignments", 5, "totalStudents", 20);
        when(assignmentService.getTeacherStats(10L)).thenReturn(stats);

        mockMvc.perform(get("/api/assignments/teacher/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalAssignments").value(5));
    }

    // ========== GET /api/assignments/teacher/classes ==========

    @Test
    @DisplayName("GET /api/assignments/teacher/classes - success")
    void getTeacherClasses_success() throws Exception {
        List<Map<String, Object>> classes = List.of(Map.of("classId", 1, "className", "Class A"));
        when(assignmentService.getTeacherClassesWithStats(10L)).thenReturn(classes);

        mockMvc.perform(get("/api/assignments/teacher/classes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].className").value("Class A"));
    }

    // ========== GET /api/assignments/teacher/recent ==========

    @Test
    @DisplayName("GET /api/assignments/teacher/recent - success")
    void getRecentAssignments_success() throws Exception {
        List<Map<String, Object>> recent = List.of(Map.of("title", "Recent Task"));
        when(assignmentService.getRecentAssignmentsWithStats(eq(10L), anyInt())).thenReturn(recent);

        mockMvc.perform(get("/api/assignments/teacher/recent").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/assignments/{assignmentId}/progress ==========

    @Test
    @DisplayName("GET /api/assignments/{id}/progress - success")
    void getAssignmentProgress_success() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(true);
        List<Map<String, Object>> progress = List.of(Map.of("studentId", 1, "progress", 80));
        when(assignmentService.getAssignmentProgress(1L)).thenReturn(progress);

        mockMvc.perform(get("/api/assignments/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].progress").value(80));
    }

    @Test
    @DisplayName("GET /api/assignments/{id}/progress - no permission")
    void getAssignmentProgress_noPermission() throws Exception {
        when(assignmentService.validateTeacherPermission(10L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/assignments/1/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看此任务的进度"));
    }

    // ========== Admin bypass ==========

    @Test
    @DisplayName("GET /api/assignments/{id} - admin can view any assignment")
    void getAssignmentById_adminBypass() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");
        User adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin1");
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("admin1")).thenReturn(adminUser);

        Assignment assignment = new Assignment();
        assignment.setId(1L);
        assignment.setTitle("Any Assignment");
        when(assignmentService.getAssignmentById(1L)).thenReturn(assignment);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assignmentService, never()).validateTeacherPermission(anyLong(), anyLong());
    }

    // ========== Service exception ==========

    @Test
    @DisplayName("GET /api/assignments/teacher/stats - service exception")
    void getTeacherStats_serviceException() throws Exception {
        when(assignmentService.getTeacherStats(10L)).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/assignments/teacher/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }
}
