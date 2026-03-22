package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.*;
import com.english.entity.Class;
import com.english.exception.BusinessException;
import com.english.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

@DisplayName("StudentExerciseController Enhanced Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentExerciseControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private StudentExerciseService studentExerciseService;
    @Mock private UserService userService;
    @Mock private AssignmentService assignmentsService;
    @Mock private StudentClassesService studentClassesService;
    @Mock private TextExerciseService textExerciseService;
    @Mock private ClassService classService;
    @Mock private TextService textService;
    @Mock private UnitService unitService;

    @InjectMocks
    private StudentExerciseController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("student1", "ROLE_USER");

        User student = new User();
        student.setId(5L);
        student.setUsername("student1");
        student.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(student);
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

    // ========== POST /api/student/exercises/submit ==========

    @Test
    @DisplayName("POST /submit - success without assignment")
    void submitAnswer_success() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(1L);
        exercise.setTextId(10L);
        when(textExerciseService.getById(1L)).thenReturn(exercise);

        // Mock assertStudentCanAccessText: text exists, no unitId → access check passes
        Text text = new Text();
        text.setId(10L);
        when(textService.getTextById(10L)).thenReturn(text);

        Map<String, Object> result = Map.of("isCorrect", true, "score", 100);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 1);
        request.put("answerContent", "my answer");
        request.put("timeSpentSeconds", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /submit - missing required fields")
    void submitAnswer_missingFields() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 1);
        // missing answerContent and timeSpentSeconds

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("exerciseId、答案内容和用时不能为空"));
    }

    @Test
    @DisplayName("POST /submit - exercise not found")
    void submitAnswer_exerciseNotFound() throws Exception {
        when(textExerciseService.getById(99L)).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 99);
        request.put("answerContent", "answer");
        request.put("timeSpentSeconds", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("习题不存在"));
    }

    @Test
    @DisplayName("POST /submit - invalid time format")
    void submitAnswer_invalidTimeFormat() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(1L);
        when(textExerciseService.getById(1L)).thenReturn(exercise);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 1);
        request.put("answerContent", "answer");
        request.put("timeSpentSeconds", "abc");

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用时参数格式错误"));
    }

    @Test
    @DisplayName("POST /submit - time out of range")
    void submitAnswer_timeOutOfRange() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(1L);
        when(textExerciseService.getById(1L)).thenReturn(exercise);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 1);
        request.put("answerContent", "answer");
        request.put("timeSpentSeconds", 99999);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长不在有效范围(0-54000秒)"));
    }

    // ========== GET /api/student/exercises/text/{textId} ==========

    @Test
    @DisplayName("GET /text/{textId} - success")
    void getStudentAnswersByText_success() throws Exception {
        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByText(5L, 10L, null)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/text/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/student/exercises/assignment/{assignmentId} ==========

    @Test
    @DisplayName("GET /assignment/{assignmentId} - success with access")
    void getStudentAnswersByAssignment_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(5L)).thenReturn(List.of(1L));
        when(assignmentsService.isAssignmentInClasses(1L, List.of(1L))).thenReturn(true);

        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByAssignment(5L, 1L)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/assignment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /assignment/{assignmentId} - no access")
    void getStudentAnswersByAssignment_noAccess() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(5L)).thenReturn(List.of(1L));
        when(assignmentsService.isAssignmentInClasses(99L, List.of(1L))).thenReturn(false);

        mockMvc.perform(get("/api/student/exercises/assignment/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权访问此任务"));
    }

    // ========== GET /api/student/exercises/assignment/{id}/stats ==========

    @Test
    @DisplayName("GET /assignment/{id}/stats - success")
    void getStudentAnswerStats_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(5L)).thenReturn(List.of(1L));
        when(assignmentsService.isAssignmentInClasses(1L, List.of(1L))).thenReturn(true);

        Map<String, Object> stats = Map.of("totalAnswered", 10, "correctCount", 8);
        when(studentExerciseService.getStudentAnswerStats(5L, 1L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/exercises/assignment/1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== GET /api/student/exercises/text/{textId}/completed ==========

    @Test
    @DisplayName("GET /text/{textId}/completed - success")
    void checkTextExerciseCompletion_success() throws Exception {
        Map<String, Object> progress = Map.of("completed", true, "progress", 100);
        when(studentExerciseService.getTextExerciseProgress(5L, 10L, null)).thenReturn(progress);

        mockMvc.perform(get("/api/student/exercises/text/10/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
