package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Assignment;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StudentAssignmentController unit tests.
 *
 * Covers: assignment list (by unitId, courseId, classIds), detail, start,
 * content, complete (success, deadline expired, assignment not found),
 * progress, records, IDOR protection, and BusinessException paths.
 */
class StudentAssignmentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AssignmentService assignmentsService;

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private UserService userService;

    @InjectMocks
    private StudentAssignmentController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).addFilters(TestUtils.authenticationFilter()).build();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "student1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        User mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("student1");
        mockUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(mockUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getStudentAssignments_byUnitId_success() throws Exception {
        List<Map<String, Object>> assignments = List.of(Map.of("id", 1L, "title", "Task 1"));
        when(assignmentsService.getAssignmentsByUnitId(eq(10L), eq(1L))).thenReturn(assignments);

        mockMvc.perform(get("/api/student/assignments").param("unitId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assignments").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void getStudentAssignments_byCourseId_success() throws Exception {
        List<Map<String, Object>> assignments = List.of(Map.of("id", 2L));
        when(assignmentsService.getAssignmentsByCourseId(eq(5L), eq(1L))).thenReturn(assignments);

        mockMvc.perform(get("/api/student/assignments").param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.assignments").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void getStudentAssignments_byClassIds_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        Map<String, Object> result = new HashMap<>();
        result.put("assignments", List.of());
        result.put("total", 0);
        when(assignmentsService.getStudentAssignmentsWithStatus(anyList(), eq(1L), isNull())).thenReturn(result);

        mockMvc.perform(get("/api/student/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getAssignmentDetail_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(10L), anyList())).thenReturn(true);
        Map<String, Object> detail = new HashMap<>();
        detail.put("id", 10L);
        detail.put("title", "Test Assignment");
        when(assignmentsService.getAssignmentDetailWithProgress(10L, 1L)).thenReturn(detail);

        mockMvc.perform(get("/api/student/assignments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Test Assignment"));
    }

    @Test
    void getAssignmentDetail_noAccess_returnsError() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(10L), anyList())).thenReturn(false);

        mockMvc.perform(get("/api/student/assignments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权访问此任务"));
    }

    @Test
    void startAssignment_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);
        Map<String, Object> startInfo = Map.of("started", true);
        when(assignmentsService.startAssignment(5L, 1L)).thenReturn(startInfo);

        mockMvc.perform(post("/api/student/assignments/5/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.started").value(true));
    }

    @Test
    void getAssignmentContent_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);
        Map<String, Object> content = Map.of("texts", List.of());
        when(assignmentsService.getAssignmentContent(5L, 1L)).thenReturn(content);

        mockMvc.perform(get("/api/student/assignments/5/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void completeAssignment_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);

        Assignment assignment = new Assignment();
        assignment.setId(5L);
        assignment.setDeadline(LocalDateTime.now().plusDays(1));
        when(assignmentsService.getAssignmentById(5L)).thenReturn(assignment);

        Map<String, Object> completionData = Map.of("score", 85);

        mockMvc.perform(post("/api/student/assignments/5/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completionData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务提交成功"));
    }

    @Test
    void completeAssignment_deadlineExpired_returnsError() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);

        Assignment assignment = new Assignment();
        assignment.setId(5L);
        assignment.setDeadline(LocalDateTime.now().minusDays(1));
        when(assignmentsService.getAssignmentById(5L)).thenReturn(assignment);

        Map<String, Object> completionData = Map.of("score", 85);

        mockMvc.perform(post("/api/student/assignments/5/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completionData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务已截止，无法再提交"));
    }

    @Test
    void completeAssignment_notFound_returnsError() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);
        when(assignmentsService.getAssignmentById(5L)).thenReturn(null);

        Map<String, Object> completionData = Map.of("score", 85);

        mockMvc.perform(post("/api/student/assignments/5/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completionData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    @Test
    void getAssignmentProgress_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);
        Map<String, Object> progress = Map.of("percentage", 50);
        when(assignmentsService.getAssignmentProgress(5L, 1L)).thenReturn(progress);

        mockMvc.perform(get("/api/student/assignments/5/progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.percentage").value(50));
    }

    @Test
    void getAssignmentRecords_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);
        Map<String, Object> records = Map.of("records", List.of(), "total", 0);
        when(learningRecordsService.getAssignmentLearningRecords(eq(5L), eq(1L), anyInt(), anyInt()))
                .thenReturn(records);

        mockMvc.perform(get("/api/student/assignments/5/records")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getStudentAssignments_businessException() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(get("/api/student/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }
}
