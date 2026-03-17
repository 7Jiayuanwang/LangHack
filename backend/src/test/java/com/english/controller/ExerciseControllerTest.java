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

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExerciseController unit tests.
 *
 * Covers: single/batch create, student vs teacher view (isCorrect leak prevention),
 * update, delete, text ownership validation (getUploadedBy per R43),
 * invalid input, and error paths.
 */
class ExerciseControllerTest {

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
    private Text ownedText;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(exerciseController).addFilters(TestUtils.authenticationFilter()).build();

        // Default: TEACHER role
        setUpSecurityContext("ROLE_TEACHER");

        teacherUser = new User();
        teacherUser.setId(1L);
        teacherUser.setUsername("teacher1");
        teacherUser.setRole("TEACHER");
        when(userService.getUserByUsername("teacher1")).thenReturn(teacherUser);

        ownedText = new Text();
        ownedText.setId(10L);
        ownedText.setDeleted(0);
        ownedText.setUploadedBy(1L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- helpers ----

    private void setUpSecurityContext(String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "teacher1", null, Collections.singletonList(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setUpStudentContext() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "student1", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        User studentUser = new User();
        studentUser.setId(2L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);
    }

    private Map<String, Object> buildSingleExerciseRequest(Long textId) {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", textId);
        req.put("question", "What is the past tense of 'go'?");
        req.put("exerciseType", "MULTIPLE_CHOICE");
        req.put("orderNum", 1);
        return req;
    }

    private Map<String, Object> buildBatchExerciseRequest(Long textId) {
        Map<String, Object> option1 = new HashMap<>();
        option1.put("optionContent", "went");
        option1.put("optionKey", "A");
        option1.put("isCorrect", true);
        option1.put("orderNum", 1);

        Map<String, Object> option2 = new HashMap<>();
        option2.put("optionContent", "goed");
        option2.put("optionKey", "B");
        option2.put("isCorrect", false);
        option2.put("orderNum", 2);

        Map<String, Object> exercise = new HashMap<>();
        exercise.put("question", "Past tense of go?");
        exercise.put("exerciseType", "MULTIPLE_CHOICE");
        exercise.put("orderNum", 1);
        exercise.put("options", Arrays.asList(option1, option2));

        Map<String, Object> req = new HashMap<>();
        req.put("textId", textId);
        req.put("exercises", Collections.singletonList(exercise));
        return req;
    }

    // ---- 1. Create single exercise ----

    @Test
    void createExercise_success() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.createTextExercise(any(TextExercise.class))).thenReturn(true);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSingleExerciseRequest(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("习题创建成功"));

        verify(textExerciseService).createTextExercise(any(TextExercise.class));
    }

    // ---- 2. Batch create exercises ----

    @Test
    void batchCreateExercises_success() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.batchCreateExercises(anyList(), anyList())).thenReturn(true);

        mockMvc.perform(post("/api/exercises/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildBatchExerciseRequest(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.exerciseCount").value(1));

        verify(textExerciseService).batchCreateExercises(anyList(), anyList());
    }

    // ---- 3. Student view: isCorrect should NOT be leaked ----

    @Test
    void getExercisesByTextId_studentView_isCorrectStripped() throws Exception {
        setUpStudentContext();

        Text text = new Text();
        text.setId(10L);
        text.setDeleted(0);
        when(textService.getTextById(10L)).thenReturn(text);

        // Simulate a response that contains isCorrect in options
        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("optionKey", "A");
        optionMap.put("optionContent", "went");
        optionMap.put("isCorrect", 1);
        optionMap.put("orderNum", 1);

        Map<String, Object> exerciseMap = new HashMap<>();
        exerciseMap.put("id", 1L);
        exerciseMap.put("question", "Past tense of go?");
        exerciseMap.put("options", Collections.singletonList(optionMap));
        exerciseMap.put("isCorrect", 1); // should also be removed from exercise level

        when(textExerciseService.getExercisesWithOptions(10L))
                .thenReturn(Collections.singletonList(exerciseMap));

        mockMvc.perform(get("/api/exercises/text/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.data[0].options[0].isCorrect").doesNotExist());
    }

    // ---- 4. Teacher view: isCorrect visible ----

    @Test
    void getExercisesByTextId_teacherView_isCorrectVisible() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);

        Map<String, Object> optionMap = new HashMap<>();
        optionMap.put("optionKey", "A");
        optionMap.put("optionContent", "went");
        optionMap.put("isCorrect", 1);
        optionMap.put("orderNum", 1);

        Map<String, Object> exerciseMap = new HashMap<>();
        exerciseMap.put("id", 1L);
        exerciseMap.put("question", "Past tense?");
        exerciseMap.put("options", Collections.singletonList(optionMap));

        when(textExerciseService.getExercisesWithOptions(10L))
                .thenReturn(Collections.singletonList(exerciseMap));

        mockMvc.perform(get("/api/exercises/text/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].options[0].isCorrect").value(1));
    }

    // ---- 5. Update exercise ----

    @Test
    void updateExercise_success() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 1L); // same as teacherUser.getId()

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
    }

    // ---- 6. Delete exercise ----

    @Test
    void deleteExercise_success() throws Exception {
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

    // ---- 7. Teacher can only edit own exercises ----

    @Test
    void updateExercise_teacherCannotEditOtherTeacherExercise() throws Exception {
        Map<String, Object> existingExercise = new HashMap<>();
        existingExercise.put("id", 1L);
        existingExercise.put("createdBy", 999L); // different teacher

        when(textExerciseService.getExerciseDetail(1L)).thenReturn(existingExercise);

        Map<String, Object> updateData = new HashMap<>();
        updateData.put("question", "Stolen question?");
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

    // ---- 8. Text ownership validation (uses getUploadedBy, per R43) ----

    @Test
    void createExercise_teacherCannotCreateForOtherTeacherText() throws Exception {
        Text otherText = new Text();
        otherText.setId(20L);
        otherText.setDeleted(0);
        otherText.setUploadedBy(999L); // different teacher uploaded this text

        when(textService.getTextById(20L)).thenReturn(otherText);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSingleExerciseRequest(20L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无权为其他教师创建的课文添加习题"));

        verify(textExerciseService, never()).createTextExercise(any());
    }

    // ---- 9. Invalid exercise: missing required fields ----

    @Test
    void createExercise_missingRequiredFields() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("textId", 10L);
        // missing question, exerciseType, orderNum

        when(textService.getTextById(10L)).thenReturn(ownedText);

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("textId、题目、题型和序号不能为空"));
    }

    // ---- 10. Service throws exception -> controller returns error ----

    @Test
    void createExercise_serviceThrowsException_returnsError() throws Exception {
        when(textService.getTextById(10L)).thenReturn(ownedText);
        when(textExerciseService.createTextExercise(any(TextExercise.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        mockMvc.perform(post("/api/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSingleExerciseRequest(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("创建习题失败，请稍后重试"));
    }
}
