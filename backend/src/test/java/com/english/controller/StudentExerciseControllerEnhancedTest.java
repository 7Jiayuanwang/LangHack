package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.Assignment;
import com.english.entity.StudentExerciseAnswer;
import com.english.entity.TextExercise;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enhanced StudentExerciseController unit tests.
 *
 * Covers:
 * - Submit answer without assignment (basic flow)
 * - Submit answer with assignment (with deadline check, textId validation)
 * - Exercise validation (exists, belongs to text)
 * - IDOR protection (student cannot access other students' assignments)
 * - Deadline check (past deadline returns error)
 * - Time spent validation (0-54000 seconds range)
 * - Answer content length validation (max 50000 chars)
 * - Get answers by text (retrieves student's answers for a text)
 * - Get answers by assignment (with IDOR protection)
 * - Get answer stats (correct count, total questions)
 * - Check text exercise completion (progress tracking)
 * - User not found error
 * - Service exceptions and error paths
 */
@DisplayName("StudentExerciseController Enhanced Tests")
class StudentExerciseControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StudentExerciseService studentExerciseService;

    @Mock
    private UserService userService;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private TextExerciseService textExerciseService;

    @InjectMocks
    private StudentExerciseController controller;

    private User studentUser;
    private TextExercise exercise;
    private Assignment assignment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("student1", "ROLE_USER");

        studentUser = new User();
        studentUser.setId(1L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTextId(5L);

        assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTitle("Assignment 1");
        assignment.setTextIds(List.of(5L));
        assignment.setDeadline(LocalDateTime.now().plusDays(1)); // Not expired
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ========== Helpers ==========

    private void setUpSecurityContext(String username, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                username, null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Map<String, Object> buildSubmitAnswerRequest(Long exerciseId, String answer, int timeSpent) {
        Map<String, Object> req = new HashMap<>();
        req.put("exerciseId", exerciseId);
        req.put("answerContent", answer);
        req.put("timeSpentSeconds", timeSpent);
        return req;
    }

    private Map<String, Object> buildSubmitAnswerWithAssignmentRequest(Long exerciseId, String answer,
                                                                       int timeSpent, Long assignmentId) {
        Map<String, Object> req = buildSubmitAnswerRequest(exerciseId, answer, timeSpent);
        req.put("assignmentId", assignmentId);
        return req;
    }

    // ========== 1. Submit Answer Without Assignment ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - submit answer without assignment success")
    void submitAnswer_noAssignment_success() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> serviceResult = Map.of("isCorrect", true, "score", 100);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(serviceResult);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "Paris", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isCorrect").value(true))
                .andExpect(jsonPath("$.data.score").value(100));

        verify(studentExerciseService).submitAnswer(any(StudentExerciseAnswer.class));
    }

    @Test
    @DisplayName("POST /api/student/exercises/submit - submit with different correct answer")
    void submitAnswer_differentCorrectAnswer_success() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> serviceResult = Map.of("isCorrect", false, "score", 0);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(serviceResult);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "London", 25);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isCorrect").value(false))
                .andExpect(jsonPath("$.data.score").value(0));
    }

    // ========== 2. Submit Answer With Assignment ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - submit with assignment before deadline success")
    void submitAnswer_withAssignment_success() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);
        when(assignmentService.getAssignmentById(100L)).thenReturn(assignment);

        Map<String, Object> serviceResult = Map.of("isCorrect", true, "score", 100);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(serviceResult);

        Map<String, Object> req = buildSubmitAnswerWithAssignmentRequest(10L, "Paris", 40, 100L);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isCorrect").value(true));

        verify(studentExerciseService).submitAnswer(any(StudentExerciseAnswer.class));
    }

    // ========== 3. IDOR Protection ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - student cannot access other student's assignment")
    void submitAnswer_idorProtection_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(false);

        Map<String, Object> req = buildSubmitAnswerWithAssignmentRequest(10L, "Paris", 40, 100L);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权访问此任务"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 4. Deadline Check ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - cannot submit after deadline")
    void submitAnswer_afterDeadline_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);

        Assignment expiredAssignment = new Assignment();
        expiredAssignment.setId(100L);
        expiredAssignment.setTitle("Assignment 1");
        expiredAssignment.setTextIds(List.of(5L));
        expiredAssignment.setDeadline(LocalDateTime.now().minusDays(1)); // Expired
        when(assignmentService.getAssignmentById(100L)).thenReturn(expiredAssignment);

        Map<String, Object> req = buildSubmitAnswerWithAssignmentRequest(10L, "Paris", 40, 100L);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务已截止，无法再提交答案"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 5. Exercise Text Validation ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - exercise not in assignment texts returns error")
    void submitAnswer_exerciseNotInAssignmentTexts_returnsError() throws Exception {
        TextExercise wrongTextExercise = new TextExercise();
        wrongTextExercise.setId(10L);
        wrongTextExercise.setTextId(999L); // Different text

        when(textExerciseService.getById(10L)).thenReturn(wrongTextExercise);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);
        when(assignmentService.getAssignmentById(100L)).thenReturn(assignment);

        Map<String, Object> req = buildSubmitAnswerWithAssignmentRequest(10L, "Paris", 40, 100L);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("习题不属于此任务"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 6. Missing Required Fields ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - missing exerciseId returns error")
    void submitAnswer_missingExerciseId_returnsError() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("answerContent", "Paris");
        req.put("timeSpentSeconds", 30);
        // Missing exerciseId

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("exerciseId、答案内容和用时不能为空"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    @Test
    @DisplayName("POST /api/student/exercises/submit - missing answerContent returns error")
    void submitAnswer_missingAnswer_returnsError() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("exerciseId", 10);
        req.put("timeSpentSeconds", 30);
        // Missing answerContent

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("exerciseId、答案内容和用时不能为空"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    @Test
    @DisplayName("POST /api/student/exercises/submit - missing timeSpentSeconds returns error")
    void submitAnswer_missingTimeSpent_returnsError() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("exerciseId", 10);
        req.put("answerContent", "Paris");
        // Missing timeSpentSeconds

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("exerciseId、答案内容和用时不能为空"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 7. Time Spent Validation ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - time spent too long (>54000 seconds) returns error")
    void submitAnswer_timeTooLong_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "Paris", 99999);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长不在有效范围(0-54000秒)"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    @Test
    @DisplayName("POST /api/student/exercises/submit - negative time spent returns error")
    void submitAnswer_negativeTime_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "Paris", -1);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长不在有效范围(0-54000秒)"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    @Test
    @DisplayName("POST /api/student/exercises/submit - time at boundary 54000 seconds success")
    void submitAnswer_timeBoundary_success() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> serviceResult = Map.of("isCorrect", true, "score", 100);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(serviceResult);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "Paris", 54000); // 15 hours exactly

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(studentExerciseService).submitAnswer(any(StudentExerciseAnswer.class));
    }

    // ========== 8. Answer Content Length ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - answer content exceeds 50000 chars returns error")
    void submitAnswer_answerTooLong_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        String longAnswer = "a".repeat(50001);
        Map<String, Object> req = buildSubmitAnswerRequest(10L, longAnswer, 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("答案内容过长，不能超过50000字符"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 9. Exercise Not Found ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - exercise not found returns error")
    void submitAnswer_exerciseNotFound_returnsError() throws Exception {
        when(textExerciseService.getById(999L)).thenReturn(null);

        Map<String, Object> req = buildSubmitAnswerRequest(999L, "Paris", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("习题不存在"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 10. Get Student Answers By Text ==========

    @Test
    @DisplayName("GET /api/student/exercises/text/{textId} - get student's answers by text success")
    void getStudentAnswersByText_success() throws Exception {
        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByText(1L, 5L, null)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/text/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(studentExerciseService).getStudentAnswersByText(1L, 5L, null);
    }

    // ========== 11. Get Student Answers By Assignment ==========

    @Test
    @DisplayName("GET /api/student/exercises/assignment/{assignmentId} - get answers by assignment success")
    void getStudentAnswersByAssignment_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);

        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByAssignment(1L, 100L)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/assignment/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());

        verify(studentExerciseService).getStudentAnswersByAssignment(1L, 100L);
    }

    @Test
    @DisplayName("GET /api/student/exercises/assignment/{assignmentId} - IDOR protection")
    void getStudentAnswersByAssignment_idorProtection_returnsError() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(false);

        mockMvc.perform(get("/api/student/exercises/assignment/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权访问此任务"));

        verify(studentExerciseService, never()).getStudentAnswersByAssignment(anyLong(), anyLong());
    }

    // ========== 12. Get Answer Stats ==========

    @Test
    @DisplayName("GET /api/student/exercises/assignment/{assignmentId}/stats - get stats success")
    void getStudentAnswerStats_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);

        Map<String, Object> stats = Map.of("totalQuestions", 10, "correctCount", 8);
        when(studentExerciseService.getStudentAnswerStats(1L, 100L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/exercises/assignment/100/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalQuestions").value(10))
                .andExpect(jsonPath("$.data.correctCount").value(8));
    }

    // ========== 13. Check Text Exercise Completion ==========

    @Test
    @DisplayName("GET /api/student/exercises/text/{textId}/completed - check completion success")
    void checkTextExerciseCompletion_success() throws Exception {
        Map<String, Object> result = Map.of("completed", true, "totalExercises", 5, "answeredExercises", 5);
        when(studentExerciseService.getTextExerciseProgress(1L, 5L, null)).thenReturn(result);

        mockMvc.perform(get("/api/student/exercises/text/5/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.answeredExercises").value(5));
    }

    // ========== 14. User Not Found ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - user not found returns error")
    void submitAnswer_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        Map<String, Object> req = buildSubmitAnswerRequest(10L, "Paris", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }

    // ========== 15. Assignment Not Found ==========

    @Test
    @DisplayName("POST /api/student/exercises/submit - assignment not found returns error")
    void submitAnswer_assignmentNotFound_returnsError() throws Exception {
        when(textExerciseService.getById(10L)).thenReturn(exercise);
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(5L));
        when(assignmentService.isAssignmentInClasses(100L, List.of(5L))).thenReturn(true);
        when(assignmentService.getAssignmentById(100L)).thenReturn(null);

        Map<String, Object> req = buildSubmitAnswerWithAssignmentRequest(10L, "Paris", 40, 100L);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("任务不存在"));

        verify(studentExerciseService, never()).submitAnswer(any());
    }
}
