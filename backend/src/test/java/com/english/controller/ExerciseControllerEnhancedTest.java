package com.english.controller;

import com.english.test.TestUtils;
import com.english.dto.Result;
import com.english.entity.ExerciseOption;
import com.english.entity.Text;
import com.english.entity.TextExercise;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.TextExerciseService;
import com.english.service.TextService;
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

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Enhanced ExerciseController unit tests.
 *
 * Covers:
 * - Single exercise creation (all types: MULTIPLE_CHOICE, FILL_IN_BLANK, etc.)
 * - Batch creation with validation (max 100 exercises, max 10 options per exercise)
 * - Student view: isCorrect field stripping (answer leak prevention)
 * - Teacher view: isCorrect field visible
 * - Update exercise with ownership validation (createdBy check)
 * - Delete exercise with ownership validation
 * - Teacher cannot edit/delete other teachers' exercises
 * - Teacher cannot create exercises for other teachers' texts (getUploadedBy validation)
 * - Invalid input handling (missing fields, wrong types)
 * - Service exceptions and error paths
 * - Text deletion validation (deleted=1 or null)
 * - Admin bypass (can create for any teacher's text)
 */
@DisplayName("ExerciseController Enhanced Tests")
class ExerciseControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private TextExerciseService textExerciseService;

    @Mock
    private UserService userService;

    @Mock
    private TextService textService;

    @InjectMocks
    private ExerciseController exerciseController;

    private User teacherUser;
    private User adminUser;
    private User otherTeacherUser;
    private Text ownedText;
    private Text otherTeacherText;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(exerciseController)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        // Setup default TEACHER context
        setUpSecurityContext("teacher1", "ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        otherTeacherUser = new User();
        otherTeacherUser.setId(999L);
        otherTeacherUser.setUsername("teacher2");
        otherTeacherUser.setRole("TEACHER");

        adminUser = new User();
        adminUser.setId(100L);
        adminUser.setUsername("admin1");
        adminUser.setRole("ADMIN");
        when(userService.getUserByUsername("admin1")).thenReturn(adminUser);

        // Text owned by teacher1
        ownedText = new Text();
        ownedText.setId(10L);
        ownedText.setDeleted(0);
        ownedText.setUploadedBy(1L);

        // Text owned by teacher2
        otherTeacherText = new Text();
        otherTeacherText.setId(20L);
        otherTeacherText.setDeleted(0);
        otherTeacherText.setUploadedBy(999L);
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

    private Map<String, Object> buildSingleExerciseRequest(Long textId, String question,
                                                           String type, int orderNum) {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", textId);
        req.put("question", question);
        req.put("exerciseType", type);
        req.put("orderNum", orderNum);
        return req;
    }

    private Map<String, Object> buildBatchExerciseRequest(Long textId, int numExercises) {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", textId);

        List<Map<String, Object>> exercises = new ArrayList<>();
        for (int i = 1; i <= numExercises; i++) {
            Map<String, Object> exercise = new HashMap<>();
            exercise.put("question", "Question " + i);
            exercise.put("exerciseType", "MULTIPLE_CHOICE");
            exercise.put("orderNum", i);

            List<Map<String, Object>> options = new ArrayList<>();
            Map<String, Object> opt1 = new HashMap<>();
            opt1.put("optionContent", "Option A");
            opt1.put("optionKey", "A");
            opt1.put("isCorrect", true);
            opt1.put("orderNum", 1);
            options.add(opt1);

            Map<String, Object> opt2 = new HashMap<>();
            opt2.put("optionContent", "Option B");
            opt2.put("optionKey", "B");
            opt2.put("isCorrect", false);
            opt2.put("orderNum", 2);
            options.add(opt2);

            exercise.put("options", options);
            exercises.add(exercise);
        }
        req.put("exercises", exercises);
        return req;
    }

    // ========== 1. Single Exercise Creation Tests ==========

    @Test
    @DisplayName("POST /api/exercises - create MULTIPLE_CHOICE exercise success")
    void createExercise_multipleChoice_success() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.createTextExercise(any(TextExercise.class))).thenReturn(true);

        Map<String, Object> req = buildSingleExerciseRequest(10L, "What is the capital of France?",
                                                             "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("习题创建成功"));

        verify(textExerciseService).createTextExercise(any(TextExercise.class));
    }

    @Test
    @DisplayName("POST /api/exercises - create FILL_IN_BLANK exercise success")
    void createExercise_fillInBlank_success() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.createTextExercise(any(TextExercise.class))).thenReturn(true);

        Map<String, Object> req = buildSingleExerciseRequest(10L, "The weather is _____ today",
                                                             "FILL_IN_BLANK", 2);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(textExerciseService).createTextExercise(any(TextExercise.class));
    }

    @Test
    @DisplayName("POST /api/exercises - missing question returns error")
    void createExercise_missingQuestion_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> req = new HashMap<>();
        req.put("textId", 10L);
        req.put("exerciseType", "MULTIPLE_CHOICE");
        req.put("orderNum", 1);
        // Missing question

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("textId、题目、题型和序号不能为空"));

        verify(textExerciseService, never()).createTextExercise(any());
    }

    @Test
    @DisplayName("POST /api/exercises - question exceeds 5000 chars returns error")
    void createExercise_questionTooLong_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        String longQuestion = "a".repeat(5001);
        Map<String, Object> req = buildSingleExerciseRequest(10L, longQuestion, "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("题目过长，不能超过5000字符"));
    }

    @Test
    @DisplayName("POST /api/exercises - invalid orderNum range returns error")
    void createExercise_invalidOrderNum_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> req = buildSingleExerciseRequest(10L, "Question?", "MULTIPLE_CHOICE", 1001);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("题目序号必须在1-1000之间"));
    }

    // ========== 2. Batch Exercise Creation Tests ==========

    @Test
    @DisplayName("POST /api/exercises/batch - batch create 5 exercises success")
    void batchCreateExercises_multiple_success() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.batchCreateExercises(anyList(), anyList())).thenReturn(true);

        Map<String, Object> req = buildBatchExerciseRequest(10L, 5);

        mockMvc.perform(post("/api/exercises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.exerciseCount").value(5));

        verify(textExerciseService).batchCreateExercises(anyList(), anyList());
    }

    @Test
    @DisplayName("POST /api/exercises/batch - exceeds 100 exercises limit returns error")
    void batchCreateExercises_exceeds100Limit_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> req = buildBatchExerciseRequest(10L, 101);

        mockMvc.perform(post("/api/exercises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单次批量创建习题不能超过100个"));

        verify(textExerciseService, never()).batchCreateExercises(anyList(), anyList());
    }

    @Test
    @DisplayName("POST /api/exercises/batch - exceeds 10 options per exercise returns error")
    void batchCreateExercises_exceedsOptionsLimit_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> req = new HashMap<>();
        req.put("textId", 10L);

        List<Map<String, Object>> exercises = new ArrayList<>();
        Map<String, Object> exercise = new HashMap<>();
        exercise.put("question", "Question 1");
        exercise.put("exerciseType", "MULTIPLE_CHOICE");
        exercise.put("orderNum", 1);

        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Map<String, Object> opt = new HashMap<>();
            opt.put("optionContent", "Option " + (char)('A' + i));
            opt.put("optionKey", String.valueOf((char)('A' + i)));
            opt.put("isCorrect", i == 0);
            opt.put("orderNum", i + 1);
            options.add(opt);
        }
        exercise.put("options", options);
        exercises.add(exercise);
        req.put("exercises", exercises);

        mockMvc.perform(post("/api/exercises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("每道习题的选项不能超过10个"));
    }

    // ========== 3. Student View: isCorrect Stripping ==========

    @Test
    @DisplayName("GET /api/exercises/text/{textId} - student view strips isCorrect from options")
    void getExercisesByTextId_studentView_stripsIsCorrect() throws Exception {
        setUpSecurityContext("student1", "ROLE_USER");
        User studentUser = new User();
        studentUser.setId(2L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);

        Text text = new Text();
        text.setId(10L);
        text.setDeleted(0);
        when(textService.getTextById(10L)).thenReturn(text);

        // Simulate service response with isCorrect
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("optionKey", "A");
        optionMap.put("optionContent", "Correct answer");
        optionMap.put("isCorrect", 1);
        optionMap.put("orderNum", 1);

        Map<String, Object> exerciseMap = new HashMap<>();
        exerciseMap.put("id", 1L);
        exerciseMap.put("question", "What is X?");
        exerciseMap.put("isCorrect", 1);
        exerciseMap.put("options", Collections.singletonList(optionMap));

        when(textExerciseService.getExercisesWithOptions(10L))
                .thenReturn(Collections.singletonList(exerciseMap));

        mockMvc.perform(get("/api/exercises/text/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.data[0].options[0].isCorrect").doesNotExist());
    }

    // ========== 4. Teacher View: isCorrect Visible ==========

    @Test
    @DisplayName("GET /api/exercises/text/{textId} - teacher view shows isCorrect field")
    void getExercisesByTextId_teacherView_showsIsCorrect() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("optionKey", "A");
        optionMap.put("optionContent", "Correct answer");
        optionMap.put("isCorrect", 1);
        optionMap.put("orderNum", 1);

        Map<String, Object> exerciseMap = new HashMap<>();
        exerciseMap.put("id", 1L);
        exerciseMap.put("question", "What is X?");
        exerciseMap.put("options", Collections.singletonList(optionMap));

        when(textExerciseService.getExercisesWithOptions(10L))
                .thenReturn(Collections.singletonList(exerciseMap));

        mockMvc.perform(get("/api/exercises/text/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].options[0].isCorrect").value(1));
    }

    // ========== 5. Update Exercise ==========

    @Test
    @DisplayName("PUT /api/exercises/{id} - teacher updates own exercise success")
    void updateExercise_ownExercise_success() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 1L); // Same as teacherUser.getId()
        when(textExerciseService.getExerciseDetail(1L)).thenReturn(existingExercise);
        when(textExerciseService.updateExercise(any(TextExercise.class))).thenReturn(true);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("question", "Updated question?");
        updateData.put("exerciseType", "FILL_IN_BLANK");
        updateData.put("orderNum", 2);

        mockMvc.perform(put("/api/exercises/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("习题更新成功"));

        verify(textExerciseService).updateExercise(any(TextExercise.class));
    }

    @Test
    @DisplayName("PUT /api/exercises/{id} - teacher cannot update other teacher's exercise")
    void updateExercise_otherTeacherExercise_returnsError() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 999L); // Different teacher
        when(textExerciseService.getExerciseDetail(1L)).thenReturn(existingExercise);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("question", "Hacked question?");
        updateData.put("exerciseType", "MULTIPLE_CHOICE");
        updateData.put("orderNum", 1);

        mockMvc.perform(put("/api/exercises/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权修改其他教师创建的习题"));

        verify(textExerciseService, never()).updateExercise(any());
    }

    // ========== 6. Delete Exercise ==========

    @Test
    @DisplayName("DELETE /api/exercises/{id} - teacher deletes own exercise success")
    void deleteExercise_ownExercise_success() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 1L);
        when(textExerciseService.getExerciseDetail(1L)).thenReturn(existingExercise);
        when(textExerciseService.deleteExercise(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/exercises/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("习题删除成功"));

        verify(textExerciseService).deleteExercise(1L);
    }

    @Test
    @DisplayName("DELETE /api/exercises/{id} - teacher cannot delete other teacher's exercise")
    void deleteExercise_otherTeacherExercise_returnsError() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 999L);
        when(textExerciseService.getExerciseDetail(1L)).thenReturn(existingExercise);

        mockMvc.perform(delete("/api/exercises/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权删除其他教师创建的习题"));

        verify(textExerciseService, never()).deleteExercise(anyLong());
    }

    // ========== 7. Text Ownership Validation ==========

    @Test
    @DisplayName("POST /api/exercises - teacher cannot create exercise for other teacher's text")
    void createExercise_textNotOwned_returnsError() throws Exception {
        when(textService.getTextById(20L)).thenReturn(otherTeacherText);

        Map<String, Object> req = buildSingleExerciseRequest(20L, "Question?", "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权为其他教师创建的课文添加习题"));

        verify(textExerciseService, never()).createTextExercise(any());
    }

    @Test
    @DisplayName("POST /api/exercises - text is deleted returns error")
    void createExercise_textIsDeleted_returnsError() throws Exception {
        Text deletedText = new Text();
        deletedText.setId(30L);
        deletedText.setDeleted(1);
        deletedText.setUploadedBy(1L);
        when(textService.getTextById(30L)).thenReturn(deletedText);

        Map<String, Object> req = buildSingleExerciseRequest(30L, "Question?", "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在或已被删除"));
    }

    @Test
    @DisplayName("POST /api/exercises/batch - admin can create exercise for any teacher's text")
    void batchCreateExercises_adminBypass_success() throws Exception {
        setUpSecurityContext("admin1", "ROLE_ADMIN");

        // Admin creating for teacher2's text should work
        when(textService.getTextById(20L)).thenReturn(otherTeacherText);
        when(textExerciseService.batchCreateExercises(anyList(), anyList())).thenReturn(true);

        Map<String, Object> req = buildBatchExerciseRequest(20L, 2);

        mockMvc.perform(post("/api/exercises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.exerciseCount").value(2));

        verify(textExerciseService).batchCreateExercises(anyList(), anyList());
    }

    // ========== 8. User and Text Not Found ==========

    @Test
    @DisplayName("POST /api/exercises - user not found returns error")
    void createExercise_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("teacher1")).thenReturn(null);

        Map<String, Object> req = buildSingleExerciseRequest(10L, "Question?", "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("POST /api/exercises - text not found returns error")
    void createExercise_textNotFound_returnsError() throws Exception {
        when(textService.getTextById(999L)).thenReturn(null);

        Map<String, Object> req = buildSingleExerciseRequest(999L, "Question?", "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("课文不存在或已被删除"));
    }

    // ========== 9. Service Exception Handling ==========

    @Test
    @DisplayName("POST /api/exercises - service throws exception returns error")
    void createExercise_serviceThrowsException_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.createTextExercise(any(TextExercise.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        Map<String, Object> req = buildSingleExerciseRequest(10L, "Question?", "MULTIPLE_CHOICE", 1);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建习题失败，请稍后重试"));
    }
}
