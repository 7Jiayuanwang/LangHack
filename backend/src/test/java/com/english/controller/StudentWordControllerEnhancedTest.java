package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.User;
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

@DisplayName("StudentWordController Enhanced Tests")
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentWordControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private VocabularyBooksService vocabularyBooksService;
    @Mock private LearningRecordsService learningRecordsService;
    @Mock private WordService wordsService;
    @Mock private UserService userService;
    @Mock private WordNetService wordNetService;

    @InjectMocks
    private StudentWordController controller;

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

    // ========== GET /api/student/words/{wordId}/detail ==========

    @Test
    @DisplayName("GET /{wordId}/detail - success")
    void getWordDetail_success() throws Exception {
        Map<String, Object> detail = Map.of("word", "hello", "definition", "greeting");
        when(vocabularyBooksService.getWordDetailWithLearningState(5L, 1L)).thenReturn(detail);
        doNothing().when(learningRecordsService).recordWordView(5L, 1L);

        mockMvc.perform(get("/api/student/words/1/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /{wordId}/detail - word not found")
    void getWordDetail_notFound() throws Exception {
        when(vocabularyBooksService.getWordDetailWithLearningState(5L, 99L))
                .thenReturn(Collections.emptyMap());

        mockMvc.perform(get("/api/student/words/99/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    // ========== POST /api/student/words/vocabulary ==========

    @Test
    @DisplayName("POST /vocabulary - add new word success")
    void addToVocabulary_newWord() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(5L, 1L)).thenReturn(false);
        doNothing().when(vocabularyBooksService).addToVocabularyWithLevel(5L, 1L, "LEARNING");
        doNothing().when(learningRecordsService).recordVocabularyAdd(5L, 1L);

        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("wordId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("已添加到生词本"));
    }

    @Test
    @DisplayName("POST /vocabulary - word already exists updates status")
    void addToVocabulary_alreadyExists() throws Exception {
        when(vocabularyBooksService.existsByStudentAndWord(5L, 1L)).thenReturn(true);
        doNothing().when(vocabularyBooksService).updateMasteryLevel(eq(5L), eq(1L), eq("LEARNING"), isNull());
        doNothing().when(learningRecordsService).recordVocabularyAdd(5L, 1L);

        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("wordId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("单词学习状态已更新"));
    }

    @Test
    @DisplayName("POST /vocabulary - missing wordId")
    void addToVocabulary_missingWordId() throws Exception {
        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("wordId不能为空"));
    }

    @Test
    @DisplayName("POST /vocabulary - invalid wordId format")
    void addToVocabulary_invalidFormat() throws Exception {
        mockMvc.perform(post("/api/student/words/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("wordId", "abc"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("wordId格式无效"));
    }

    // ========== POST /api/student/words/vocabulary/by-word ==========

    @Test
    @DisplayName("POST /vocabulary/by-word - success with new word")
    void addToVocabularyByWord_success() throws Exception {
        Map<String, Object> wordResult = new HashMap<>();
        wordResult.put("found", true);
        wordResult.put("wordId", 10L);
        wordResult.put("word", "test");
        when(wordNetService.getOrCreateWord("test")).thenReturn(wordResult);
        when(vocabularyBooksService.existsByStudentAndWord(5L, 10L)).thenReturn(false);
        doNothing().when(vocabularyBooksService).addToVocabularyWithLevel(5L, 10L, "LEARNING");
        doNothing().when(learningRecordsService).recordVocabularyAdd(5L, 10L);

        mockMvc.perform(post("/api/student/words/vocabulary/by-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", "test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /vocabulary/by-word - empty word")
    void addToVocabularyByWord_emptyWord() throws Exception {
        mockMvc.perform(post("/api/student/words/vocabulary/by-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不能为空"));
    }

    // ========== GET /api/student/words/vocabulary ==========

    @Test
    @DisplayName("GET /vocabulary - paginated list success")
    void getVocabularyList_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(vocabularyBooksService.getVocabularyWithPagination(anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/student/words/vocabulary").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== PUT /api/student/words/vocabulary/{wordId}/mastery ==========

    @Test
    @DisplayName("PUT /vocabulary/{wordId}/mastery - success")
    void updateMasteryLevel_success() throws Exception {
        doNothing().when(vocabularyBooksService).updateMasteryLevel(eq(5L), eq(1L), eq("MASTERED"), anyMap());

        mockMvc.perform(put("/api/student/words/vocabulary/1/mastery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("masteryLevel", "MASTERED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("掌握程度已更新"));
    }

    @Test
    @DisplayName("PUT /vocabulary/{wordId}/mastery - invalid mastery level")
    void updateMasteryLevel_invalidLevel() throws Exception {
        mockMvc.perform(put("/api/student/words/vocabulary/1/mastery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("masteryLevel", "INVALID"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("无效的掌握程度"));
    }

    @Test
    @DisplayName("PUT /vocabulary/{wordId}/mastery - missing mastery level")
    void updateMasteryLevel_missing() throws Exception {
        mockMvc.perform(put("/api/student/words/vocabulary/1/mastery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("掌握程度不能为空"));
    }

    // ========== POST /api/student/words/study/session ==========

    @Test
    @DisplayName("POST /study/session - success")
    void startStudySession_success() throws Exception {
        Map<String, Object> session = Map.of("words", List.of(), "totalWords", 0);
        when(vocabularyBooksService.createStudySession(5L, 1L)).thenReturn(session);

        mockMvc.perform(post("/api/student/words/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("unitId", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /study/session - missing unitId")
    void startStudySession_missingUnitId() throws Exception {
        mockMvc.perform(post("/api/student/words/study/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("unitId不能为空"));
    }

    // ========== POST /api/student/words/study/submit ==========

    @Test
    @DisplayName("POST /study/submit - success")
    void submitStudyResult_success() throws Exception {
        doNothing().when(vocabularyBooksService).processStudyResults(eq(5L), anyMap());

        Map<String, Object> request = Map.of("results", List.of());

        mockMvc.perform(post("/api/student/words/study/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("学习结果已记录"));
    }

    // ========== GET /api/student/words/study/stats ==========

    @Test
    @DisplayName("GET /study/stats - success")
    void getWordStudyStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalWords", 100, "mastered", 30);
        when(vocabularyBooksService.getWordStudyStats(5L, null)).thenReturn(stats);

        mockMvc.perform(get("/api/student/words/study/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ========== POST /api/student/words/vocabulary/batch ==========

    @Test
    @DisplayName("POST /vocabulary/batch - delete success")
    void batchOperation_deleteSuccess() throws Exception {
        doNothing().when(vocabularyBooksService).batchDeleteVocabulary(eq(5L), anyList());

        Map<String, Object> request = new HashMap<>();
        request.put("operation", "delete");
        request.put("wordIds", List.of(1, 2, 3));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("批量删除成功"));
    }

    @Test
    @DisplayName("POST /vocabulary/batch - mark mastered success")
    void batchOperation_markMastered() throws Exception {
        doNothing().when(vocabularyBooksService).batchUpdateMasteryLevel(eq(5L), anyList(), eq("MASTERED"));

        Map<String, Object> request = new HashMap<>();
        request.put("operation", "mark_mastered");
        request.put("wordIds", List.of(1, 2));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("批量标记成功"));
    }

    @Test
    @DisplayName("POST /vocabulary/batch - missing operation")
    void batchOperation_missingOperation() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("wordIds", List.of(1, 2));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请指定操作类型"));
    }

    @Test
    @DisplayName("POST /vocabulary/batch - unsupported operation")
    void batchOperation_unsupported() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("operation", "unknown");
        request.put("wordIds", List.of(1));

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("不支持的操作类型"));
    }

    @Test
    @DisplayName("POST /vocabulary/batch - missing wordIds")
    void batchOperation_missingWordIds() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("operation", "delete");

        mockMvc.perform(post("/api/student/words/vocabulary/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请选择要操作的单词"));
    }
}
