package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Assignment;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.TextMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.WordMapper;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AssignmentController unit tests.
 *
 * Covers: create, update with classId authorization (R43 fix), delete,
 * get detail, list, teacher stats, BusinessException catch,
 * and non-teacher permission checks.
 */
class AssignmentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private UserService userService;

    @Mock
    private ClassService classService;

    @Mock
    private TextMapper textMapper;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @InjectMocks
    private AssignmentController assignmentController;

    private User teacherUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(assignmentController).addFilters(TestUtils.authenticationFilter()).build();

        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
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

    private Assignment buildAssignment() {
        Assignment a = new Assignment();
        a.setTitle("Unit 1 Homework");
        a.setDescription("Read and practice");
        a.setClassId(10L);
        a.setUnitId(5L);
        return a;
    }

    // ---- 1. Create assignment (TEACHER) ----

    @Test
    void createAssignment_teacherSuccess() throws Exception {
        // Teacher owns the class
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        Assignment assignment = buildAssignment();

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assignment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务创建成功"));

        verify(assignmentService).createAssignment(any(Assignment.class));
    }

    @Test
    void createTextAssignment_withoutUnit_succeeds() throws Exception {
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        Map<String, Object> request = new HashMap<>();
        request.put("title", "Reading Assignment");
        request.put("classId", 10L);
        request.put("assignmentType", "TEXTS");
        request.put("textIds", List.of(101L));

        mockMvc.perform(post("/api/assignments/create-with-content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Assignment> assignmentCaptor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentService).createAssignment(assignmentCaptor.capture());
        assertNull(assignmentCaptor.getValue().getUnitId());
        assertEquals("TEXTS", assignmentCaptor.getValue().getAssignmentContent().getType());
    }

    // ---- 2. Update assignment: teacher classId authorization check (R43 fix) ----

    @Test
    void updateAssignment_teacherCannotAssignToOtherClass() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);

        // Target class belongs to another teacher
        com.english.entity.Class otherClass = new com.english.entity.Class();
        otherClass.setTeacherId(999L);
        when(classService.getClassById(20L)).thenReturn(otherClass);

        Assignment update = new Assignment();
        update.setTitle("Changed");
        update.setClassId(20L); // attempting to move to another teacher's class

        mockMvc.perform(put("/api/assignments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限将任务分配到该班级"));

        verify(assignmentService, never()).updateAssignment(any());
    }

    // ---- 3. Delete assignment (soft delete) ----

    @Test
    void deleteAssignment_teacherSuccess() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务删除成功"));

        verify(assignmentService).deleteAssignment(1L);
    }

    // ---- 4. Get assignment details ----

    @Test
    void getAssignmentById_success() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);

        Assignment assignment = buildAssignment();
        assignment.setId(1L);
        when(assignmentService.getAssignmentById(1L)).thenReturn(assignment);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Unit 1 Homework"));
    }

    @Test
    void getAssignmentById_notFound() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);
        when(assignmentService.getAssignmentById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务不存在"));
    }

    // ---- 5. List assignments for class ----

    @Test
    void getAssignments_withClassFilter() throws Exception {
        Map<String, Object> pageResult = new HashMap<>();
        pageResult.put("total", 1L);
        pageResult.put("records", Collections.emptyList());
        when(assignmentService.getAssignmentsByPage(anyMap())).thenReturn(pageResult);

        mockMvc.perform(get("/api/assignments")
                        .param("classId", "10")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    // ---- 6. Get teacher stats ----

    @Test
    void getTeacherStats_success() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAssignments", 5);
        stats.put("totalStudents", 30);
        when(assignmentService.getTeacherStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/assignments/teacher/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalAssignments").value(5));
    }

    // ---- 7. BusinessException catch ----

    @Test
    void createAssignment_businessExceptionReturnsErrorMessage() throws Exception {
        com.english.entity.Class classInfo = new com.english.entity.Class();
        classInfo.setTeacherId(1L);
        when(classService.getClassById(10L)).thenReturn(classInfo);

        doThrow(new BusinessException("任务标题已存在"))
                .when(assignmentService).createAssignment(any(Assignment.class));

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildAssignment())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务标题已存在"));
    }

    // ---- 8. Teacher cannot view other teacher's assignment ----

    @Test
    void getAssignmentById_teacherNoPermission() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(false);

        mockMvc.perform(get("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("没有权限查看此任务"));

        verify(assignmentService, never()).getAssignmentById(anyLong());
    }

    // ---- 9. Update assignment: teacher validates own permission ----

    @Test
    void updateAssignment_teacherSuccess() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);

        // No classId change, so classService is not checked for target class
        Assignment update = new Assignment();
        update.setTitle("Updated Homework");

        mockMvc.perform(put("/api/assignments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("任务更新成功"));

        verify(assignmentService).updateAssignment(any(Assignment.class));
    }

    // ---- 10. RuntimeException returns generic error message ----

    @Test
    void deleteAssignment_runtimeExceptionReturnsGenericError() throws Exception {
        when(assignmentService.validateTeacherPermission(1L, 1L)).thenReturn(true);
        doThrow(new RuntimeException("DB failure"))
                .when(assignmentService).deleteAssignment(1L);

        mockMvc.perform(delete("/api/assignments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("操作失败，请稍后重试"));
    }
}
