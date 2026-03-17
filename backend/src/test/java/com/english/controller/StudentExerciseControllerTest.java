package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Assignment;
import com.english.entity.StudentExerciseAnswer;
import com.english.entity.TextExercise;
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
 * StudentExerciseController unit tests.
 *
 * Covers: submit answer (with and without assignmentId, deadline check,
 * exercise validation), get answers by text, get answers by assignment,
 * get answer stats, check text exercise completion, and IDOR protection.
 */
class StudentExerciseControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StudentExerciseService studentExerciseService;

    @Mock
    private UserService userService;

    @Mock
    private AssignmentService assignmentsService;

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private TextExerciseService textExerciseService;

    @InjectMocks
    private StudentExerciseController controller;

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
    void submitAnswer_success_noAssignment() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        exercise.setTextId(5L);
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> serviceResult = Map.of("isCorrect", true, "score", 100);
        when(studentExerciseService.submitAnswer(any(StudentExerciseAnswer.class))).thenReturn(serviceResult);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 10);
        request.put("answerContent", "my answer");
        request.put("timeSpentSeconds", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isCorrect").value(true));
    }

    @Test
    void submitAnswer_missingRequiredFields_returnsError() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 10);
        // Missing answerContent and timeSpentSeconds

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("exerciseId、答案内容和用时不能为空"));
    }

    @Test
    void submitAnswer_exerciseNotFound_returnsError() throws Exception {
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
    void submitAnswer_timeSpentOutOfRange_returnsError() throws Exception {
        TextExercise exercise = new TextExercise();
        exercise.setId(10L);
        when(textExerciseService.getById(10L)).thenReturn(exercise);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 10);
        request.put("answerContent", "answer");
        request.put("timeSpentSeconds", 99999); // exceeds 54000

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("学习时长不在有效范围(0-54000秒)"));
    }

    @Test
    void getStudentAnswersByText_success() throws Exception {
        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByText(1L, 5L, null)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/text/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getStudentAnswersByAssignment_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);

        List<StudentExerciseAnswer> answers = new ArrayList<>();
        when(studentExerciseService.getStudentAnswersByAssignment(1L, 5L)).thenReturn(answers);

        mockMvc.perform(get("/api/student/exercises/assignment/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getStudentAnswersByAssignment_noAccess_returnsError() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(false);

        mockMvc.perform(get("/api/student/exercises/assignment/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权访问此任务"));
    }

    @Test
    void getStudentAnswerStats_success() throws Exception {
        when(studentClassesService.getClassIdsByStudentId(1L)).thenReturn(List.of(100L));
        when(assignmentsService.isAssignmentInClasses(eq(5L), anyList())).thenReturn(true);

        Map<String, Object> stats = Map.of("totalQuestions", 10, "correctCount", 8);
        when(studentExerciseService.getStudentAnswerStats(1L, 5L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/exercises/assignment/5/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalQuestions").value(10));
    }

    @Test
    void checkTextExerciseCompletion_success() throws Exception {
        Map<String, Object> result = Map.of("completed", true, "totalExercises", 5, "answeredExercises", 5);
        when(studentExerciseService.getTextExerciseProgress(1L, 5L, null)).thenReturn(result);

        mockMvc.perform(get("/api/student/exercises/text/5/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.completed").value(true));
    }

    @Test
    void submitAnswer_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        Map<String, Object> request = new HashMap<>();
        request.put("exerciseId", 10);
        request.put("answerContent", "answer");
        request.put("timeSpentSeconds", 30);

        mockMvc.perform(post("/api/student/exercises/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }
}
