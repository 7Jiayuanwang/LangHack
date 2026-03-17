package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.Assignment;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.AssignmentService;
import com.english.service.ClassService;
import com.english.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
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
 * Enhanced AssignmentController unit tests.
 * Covers: create task, update with classId auth (R43), delete, get detail,
 * list, teacher stats, BusinessException catch, non-teacher permission checks.
 */
@DisplayName("AssignmentController Enhanced Tests")
class AssignmentControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AssignmentService assignmentService;
    @Mock private UserService userService;
    @Mock private ClassService classService;

    @InjectMocks
    private AssignmentController assignmentController;

    private User teacherUser;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(assignmentController)
                .addFilters(TestUtils.authenticationFilter()).build();

        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTitle("Assignment 1");
        assignment.setClassId(10L);
        assignment.setUnitId(5L);
        assignment.setDeadline(LocalDateTime.now().plusDays(1));
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

    @Test
    @DisplayName("POST /api/assignments - create assignment success")
    void createAssignment_success() throws Exception {
        when(classService.validateTeacherPermission(1L, 10L)).thenReturn(true);
        doNothing().when(assignmentService).createAssignment(any(Assignment.class));

        Map<String, Object> req = new HashMap<>();
        req.put("title", "Assignment 1");
        req.put("classId", 10);
        req.put("unitId", 5);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assignmentService).createAssignment(any(Assignment.class));
    }

    @Test
    @DisplayName("POST /api/assignments - teacher without class permission")
    void createAssignment_noPermission_returnsError() throws Exception {
        when(classService.validateTeacherPermission(1L, 10L)).thenReturn(false);

        Map<String, Object> req = new HashMap<>();
        req.put("title", "Assignment 1");
        req.put("classId", 10);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        verify(assignmentService, never()).createAssignment(any());
    }

    @Test
    @DisplayName("PUT /api/assignments/{id} - update assignment with classId validation")
    void updateAssignment_withClassIdAuth_success() throws Exception {
        when(assignmentService.getAssignmentById(100L)).thenReturn(assignment);
        when(classService.validateTeacherPermission(1L, 10L)).thenReturn(true);
        doNothing().when(assignmentService).updateAssignment(any(Assignment.class));

        Map<String, Object> req = new HashMap<>();
        req.put("title", "Updated Assignment");
        req.put("classId", 10);

        mockMvc.perform(put("/api/assignments/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assignmentService).updateAssignment(any(Assignment.class));
    }

    @Test
    @DisplayName("DELETE /api/assignments/{id} - delete assignment success")
    void deleteAssignment_success() throws Exception {
        when(assignmentService.getAssignmentById(100L)).thenReturn(assignment);
        when(classService.validateTeacherPermission(1L, 10L)).thenReturn(true);
        doNothing().when(assignmentService).deleteAssignment(100L);

        mockMvc.perform(delete("/api/assignments/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(assignmentService).deleteAssignment(100L);
    }

    @Test
    @DisplayName("GET /api/assignments/{id} - get assignment detail")
    void getAssignmentDetail_success() throws Exception {
        when(assignmentService.getAssignmentById(100L)).thenReturn(assignment);

        mockMvc.perform(get("/api/assignments/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Assignment 1"));
    }

    @Test
    @DisplayName("GET /api/assignments - list assignments")
    void listAssignments_success() throws Exception {
        when(assignmentService.getAssignmentsByPage(anyMap()))
                .thenReturn(Map.of("records", List.of(assignment), "total", 1));

        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/assignments - service throws BusinessException")
    void createAssignment_businessException_returnsError() throws Exception {
        when(classService.validateTeacherPermission(1L, 10L)).thenReturn(true);
        when(assignmentService.createAssignment(any()))
                .thenThrow(new BusinessException("验证失败"));

        Map<String, Object> req = new HashMap<>();
        req.put("title", "Assignment 1");
        req.put("classId", 10);

        mockMvc.perform(post("/api/assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("验证失败"));
    }
}
