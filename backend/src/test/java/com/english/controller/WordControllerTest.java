package com.english.controller;
import com.english.test.TestUtils;

import com.english.entity.Word;
import com.english.service.WordService;
import com.english.util.TestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WordService wordService;

    @InjectMocks
    private WordController wordController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(wordController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/words ==========

    @Test
    @DisplayName("GET /api/words - returns paginated word list")
    void getWords_returnsPaginatedList() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", List.of(createMockWord(1L, "hello")));
        result.put("total", 1L);

        when(wordService.getWordsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/words")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /api/words - with keyword filter")
    void getWords_withKeyword() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(wordService.getWordsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/words")
                        .param("keyword", "hel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wordService).getWordsByPage(paramsCaptor.capture());
        Assertions.assertEquals("hel", paramsCaptor.getValue().get("keyword"));
    }

    @Test
    @DisplayName("GET /api/words - with difficulty filter")
    void getWords_withDifficultyFilter() throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0L);

        when(wordService.getWordsByPage(any(Map.class))).thenReturn(result);

        mockMvc.perform(get("/api/words")
                        .param("difficulty", "HARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wordService).getWordsByPage(paramsCaptor.capture());
        Assertions.assertEquals("HARD", paramsCaptor.getValue().get("difficulty"));
    }

    // ========== GET /api/words/search ==========

    @Test
    @DisplayName("GET /api/words/search - existing word returns word")
    void searchWord_existingWord_returnsWord() throws Exception {
        Word word = createMockWord(1L, "hello");
        word.setDefinition("a greeting");

        when(wordService.getWordByWord("hello")).thenReturn(word);

        mockMvc.perform(get("/api/words/search")
                        .param("word", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("hello"))
                .andExpect(jsonPath("$.data.definition").value("a greeting"));
    }

    @Test
    @DisplayName("GET /api/words/search - non-existent word returns error")
    void searchWord_nonExistent_returnsError() throws Exception {
        when(wordService.getWordByWord("xyznotexist")).thenReturn(null);

        mockMvc.perform(get("/api/words/search")
                        .param("word", "xyznotexist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    // ========== GET /api/words/random ==========

    @Test
    @DisplayName("GET /api/words/random - returns random words with default count")
    void getRandomWords_defaultCount() throws Exception {
        List<Word> words = List.of(
                createMockWord(1L, "hello"),
                createMockWord(2L, "world")
        );

        when(wordService.getRandomWords(10)).thenReturn(words);

        mockMvc.perform(get("/api/words/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/words/random - count capped at 100")
    void getRandomWords_countCappedAt100() throws Exception {
        when(wordService.getRandomWords(100)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/words/random")
                        .param("count", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Math.min(200, 100) = 100
        verify(wordService).getRandomWords(100);
    }

    // ========== GET /api/words/{id} ==========

    @Test
    @DisplayName("GET /api/words/{id} - existing word returns details")
    void getWordById_existingWord_returnsDetails() throws Exception {
        Word word = createMockWord(1L, "hello");
        word.setDefinition("a greeting");
        word.setPronunciation("/həˈloʊ/");
        word.setDifficulty("EASY");

        when(wordService.getWordById(1L)).thenReturn(word);

        mockMvc.perform(get("/api/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.word").value("hello"))
                .andExpect(jsonPath("$.data.definition").value("a greeting"))
                .andExpect(jsonPath("$.data.pronunciation").value("/həˈloʊ/"))
                .andExpect(jsonPath("$.data.difficulty").value("EASY"));
    }

    @Test
    @DisplayName("GET /api/words/{id} - non-existent word returns error")
    void getWordById_nonExistent_returnsError() throws Exception {
        when(wordService.getWordById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/words/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    // ========== POST /api/words ==========

    @Test
    @DisplayName("POST /api/words - add new word successfully")
    void addWord_success() throws Exception {
        Word word = new Word();
        word.setWord("banana");
        word.setDefinition("a yellow fruit");
        word.setDifficulty("EASY");

        when(wordService.getWordByWord("banana")).thenReturn(null);
        when(wordService.createWord(any(Word.class))).thenReturn(true);

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(wordService).createWord(any(Word.class));
    }

    @Test
    @DisplayName("POST /api/words - duplicate word returns error")
    void addWord_duplicate_returnsError() throws Exception {
        Word word = new Word();
        word.setWord("hello");

        Word existingWord = createMockWord(1L, "hello");
        when(wordService.getWordByWord("hello")).thenReturn(existingWord);

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词已存在"));

        verify(wordService, never()).createWord(any(Word.class));
    }

    @Test
    @DisplayName("POST /api/words - createWord returns false returns error")
    void addWord_createFails_returnsError() throws Exception {
        Word word = new Word();
        word.setWord("banana");

        when(wordService.getWordByWord("banana")).thenReturn(null);
        when(wordService.createWord(any(Word.class))).thenReturn(false);

        mockMvc.perform(post("/api/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词添加失败"));
    }

    // ========== PUT /api/words/{id} ==========

    @Test
    @DisplayName("PUT /api/words/{id} - update word successfully")
    void updateWord_success() throws Exception {
        Word word = new Word();
        word.setWord("hello");
        word.setDefinition("updated definition");

        when(wordService.updateWord(any(Word.class))).thenReturn(true);

        mockMvc.perform(put("/api/words/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        ArgumentCaptor<Word> wordCaptor = ArgumentCaptor.forClass(Word.class);
        verify(wordService).updateWord(wordCaptor.capture());
        Assertions.assertEquals(1L, wordCaptor.getValue().getId());
    }

    @Test
    @DisplayName("PUT /api/words/{id} - update returns false returns error")
    void updateWord_fails_returnsError() throws Exception {
        Word word = new Word();
        word.setWord("hello");

        when(wordService.updateWord(any(Word.class))).thenReturn(false);

        mockMvc.perform(put("/api/words/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词更新失败"));
    }

    // ========== DELETE /api/words/{id} ==========

    @Test
    @DisplayName("DELETE /api/words/{id} - soft delete success")
    void deleteWord_success() throws Exception {
        when(wordService.deleteWord(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(wordService).deleteWord(1L);
    }

    @Test
    @DisplayName("DELETE /api/words/{id} - delete returns false returns error")
    void deleteWord_fails_returnsError() throws Exception {
        when(wordService.deleteWord(1L)).thenReturn(false);

        mockMvc.perform(delete("/api/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词删除失败"));
    }

    // ========== GET /api/words/stats ==========

    @Test
    @DisplayName("GET /api/words/stats - returns word statistics")
    void getWordStats_returnsStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWords", 500);
        stats.put("easyWords", 200);
        stats.put("mediumWords", 200);
        stats.put("hardWords", 100);

        when(wordService.getWordStats()).thenReturn(stats);

        mockMvc.perform(get("/api/words/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(500))
                .andExpect(jsonPath("$.data.easyWords").value(200));
    }

    @Test
    @DisplayName("GET /api/words/stats - exception returns error")
    void getWordStats_exception_returnsError() throws Exception {
        when(wordService.getWordStats()).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/words/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    // ========== Helper Methods ==========

    private Word createMockWord(Long id, String word) {
        Word w = new Word();
        w.setId(id);
        w.setWord(word);
        return w;
    }
}
