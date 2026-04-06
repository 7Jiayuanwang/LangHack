package com.english.controller;
import com.english.test.TestUtils;

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

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StudentWordController unit tests.
 *
 * Covers: word detail, add to vocabulary (by wordId and by word string),
 * vocabulary list, update mastery level, start study session, submit study
 * result, word study stats, and batch operations.
 */
class StudentWordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private VocabularyBooksService vocabularyBooksService;

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private WordService wordsService;

    @Mock
    private UserService userService;

    @Mock
    private WordNetService wordNetService;

    @InjectMocks
    private StudentWordController controller;

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
    void getWordDetail_success() throws Exception {
        Map<String, Object> detail = new HashMap<>();
        detail.put("word", "hello");
        detail.put("definition", "a greeting");
        when(vocabularyBooksService.getWordDetailWithLearningState(1L, 10L)).thenReturn(detail);

        mockMvc.perform(get("/api/student/words/10/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("hello"));

        verify(learningRecordsService).recordWordView(1L, 10L);
    }

    @Test
    void getWordDetail_notFound_returnsError() throws Exception {
        when(vocabularyBooksService.getWordDetailWithLearningState(1L, 99L)).thenReturn(new HashMap<>());

        mockMvc.perform(get("/api/student/words/99/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    @Test
    void addToVocabulary_newWord_success() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(1L, 10L)).thenReturn(false);

        Map<String, Object> request = Map.of("wordId", 10);

        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("已添加到生词本"));

        verify(vocabularyBooksService).addToVocabularyWithLevel(1L, 10L, "NEW");
        verify(learningRecordsService).recordVocabularyAdd(1L, 10L);
    }

    @Test
    void addToVocabulary_existingWord_updatesStatus() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(1L, 10L)).thenReturn(true);

        Map<String, Object> request = Map.of("wordId", 10);

        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词已在生词本中"));

        verify(learningRecordsService).recordVocabularyAdd(1L, 10L);
    }

    @Test
    void addToVocabulary_missingWordId_returnsError() throws Exception {
        Map<String, Object> request = new HashMap<>();

        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("wordId不能为空"));
    }

    @Test
    void addToVocabularyByWord_success() throws Exception {
        Map<String, Object> wordResult = new HashMap<>();
        wordResult.put("found", true);
        wordResult.put("wordId", 10L);
        wordResult.put("created", false);
        when(wordNetService.getOrCreateWord("hello")).thenReturn(wordResult);
        when(vocabularyBooksService.existsByStudentAndWord(1L, 10L)).thenReturn(false);

        Map<String, Object> request = Map.of("word", "hello");

        mockMvc.perform(post("/api/student/words/vocabulary/by-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.vocabularyAction").value("added"));

        verify(vocabularyBooksService).addToVocabularyWithLevel(1L, 10L, "NEW");
    }

    @Test
    void getVocabularyList_success() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of());
        result.put("total", 0);
        when(vocabularyBooksService.getVocabularyWithPagination(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/student/words/vocabulary")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void updateMasteryLevel_success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("masteryLevel", "MASTERED");
        request.put("isCorrect", true);

        mockMvc.perform(put("/api/student/words/vocabulary/10/mastery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("掌握程度已更新"));

        verify(vocabularyBooksService).updateMasteryLevel(eq(1L), eq(10L), eq("MASTERED"), anyMap());
        verify(learningRecordsService).recordWordReview(1L, 10L, true);
    }

    @Test
    void updateMasteryLevel_invalidLevel_returnsError() throws Exception {
        Map<String, Object> request = Map.of("masteryLevel", "INVALID");

        mockMvc.perform(put("/api/student/words/vocabulary/10/mastery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无效的掌握程度"));
    }

    @Test
    void startStudySession_success() throws Exception {
        Map<String, Object> session = Map.of("sessionId", "abc", "words", List.of());
        when(vocabularyBooksService.createStudySession(1L, 10L)).thenReturn(session);

        Map<String, Object> request = Map.of("unitId", 10);

        mockMvc.perform(post("/api/student/words/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.sessionId").value("abc"));
    }

    @Test
    void startStudySession_missingUnitId_returnsError() throws Exception {
        Map<String, Object> request = new HashMap<>();

        mockMvc.perform(post("/api/student/words/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("unitId不能为空"));
    }

    @Test
    void submitStudyResult_success() throws Exception {
        Map<String, Object> request = Map.of("results", List.of());

        mockMvc.perform(post("/api/student/words/study/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("学习结果已记录"));

        verify(vocabularyBooksService).processStudyResults(eq(1L), anyMap());
    }

    @Test
    void getWordStudyStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalWords", 50, "mastered", 20);
        when(vocabularyBooksService.getWordStudyStats(eq(1L), isNull())).thenReturn(stats);

        mockMvc.perform(get("/api/student/words/study/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(50));
    }

    @Test
    void batchOperateVocabulary_delete_success() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("operation", "delete");
        request.put("wordIds", List.of(1, 2, 3));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("批量删除成功"));

        verify(vocabularyBooksService).batchDeleteVocabulary(eq(1L), anyList());
    }

    @Test
    void batchOperateVocabulary_unsupportedOperation_returnsError() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("operation", "unknown");
        request.put("wordIds", List.of(1, 2, 3));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("不支持的操作类型"));
    }

    @Test
    void batchOperateVocabulary_missingWordIds_returnsError() throws Exception {
        Map<String, Object> request = Map.of("operation", "delete");

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请选择要操作的单词"));
    }

    @Test
    void anyEndpoint_userNotFound_returnsBusinessException() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(get("/api/student/words/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }
}
