package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.Assignment;
import com.english.entity.ScoreRecord;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
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

/**
 * Enhanced ScoreRecordController unit tests.
 * Covers: record score, get student scores, get class scores (teacher),
 * IDOR protection on updateAssignmentScore, export, BusinessException catch.
 */
@DisplayName("ScoreRecordController Enhanced Tests")
class ScoreRecordControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ScoreRecordService scoreRecordService;
    @Mock private UserService userService;
    @Mock private AssignmentService assignmentService;
    @Mock private ClassService classService;
    @Mock private StudentClassesService studentClassService;
    @Mock private UnitService unitService;

    @InjectMocks
    private ScoreRecordController scoreRecordController;

    private User teacherUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(scoreRecordController)
                .addFilters(TestUtils.authenticationFilter()).build();

        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        studentUser = new User();
        studentUser.setId(2L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
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
    @DisplayName("POST /api/scores - record score success")
    void recordScore_success() throws Exception {
        doNothing().when(scoreRecordService).recordScore(any(ScoreRecord.class));

        Map<String, Object> req = new HashMap<>();
        req.put("studentId", 2);
        req.put("assignmentId", 100);
        req.put("score", 85);

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(scoreRecordService).recordScore(any());
    }

    @Test
    @DisplayName("GET /api/scores/student/{studentId}/assignment/{assignmentId} - get score")
    void getStudentScore_success() throws Exception {
        ScoreRecord record = new ScoreRecord();
        record.setScore(85);
        when(scoreRecordService.getScore(2L, 100L)).thenReturn(record);

        mockMvc.perform(get("/api/scores/student/2/assignment/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.score").value(85));
    }

    @Test
    @DisplayName("GET /api/scores/class/{classId} - get class scores as teacher")
    void getClassScores_teacher_success() throws Exception {
        when(classService.validateTeacherPermission(1L, 5L)).thenReturn(true);
        when(scoreRecordService.getClassScoresByPage(anyMap()))
                .thenReturn(Map.of("records", List.of(), "total", 0));

        mockMvc.perform(get("/api/scores/class/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /api/scores/{recordId} - IDOR: student cannot update others' scores")
    void updateScore_idorProtection_returnsError() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        ScoreRecord record = new ScoreRecord();
        record.setId(10L);
        record.setStudentId(99L); // Different student
        when(scoreRecordService.getScoreRecord(10L)).thenReturn(record);

        Map<String, Object> req = new HashMap<>();
        req.put("score", 100);

        mockMvc.perform(put("/api/scores/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        verify(scoreRecordService, never()).updateScore(any());
    }

    @Test
    @DisplayName("POST /api/scores - BusinessException returns error")
    void recordScore_businessException_returnsError() throws Exception {
        when(scoreRecordService.recordScore(any()))
                .thenThrow(new BusinessException("成绩验证失败"));

        Map<String, Object> req = new HashMap<>();
        req.put("studentId", 2);
        req.put("assignmentId", 100);
        req.put("score", 150); // Invalid score

        mockMvc.perform(post("/api/scores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("成绩验证失败"));
    }

    @Test
    @DisplayName("GET /api/scores/export/class/{classId} - export class scores")
    void exportClassScores_success() throws Exception {
        when(classService.validateTeacherPermission(1L, 5L)).thenReturn(true);
        when(scoreRecordService.getClassScores(5L, null))
                .thenReturn(Map.of("data", new ArrayList<>()));

        mockMvc.perform(get("/api/scores/export/class/5"))
                .andExpect(status().isOk());
    }
}
