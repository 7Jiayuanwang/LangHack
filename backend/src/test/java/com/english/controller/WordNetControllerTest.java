package com.english.controller;
import com.english.test.TestUtils;

import com.english.service.ECDICTService;
import com.english.service.WordNetService;
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

class WordNetControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WordNetService wordNetService;

    @Mock
    private ECDICTService ecdictService;

    @InjectMocks
    private WordNetController wordNetController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(wordNetController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/wordnet/word/{word} ==========

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - returns word info")
    void getWordInfo_validWord_returnsInfo() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "apple");
        wordInfo.put("definition", "A round fruit");
        wordInfo.put("found", true);

        when(wordNetService.getWordInfo("apple")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("apple"))
                .andExpect(jsonPath("$.data.definition").value("A round fruit"))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - empty word returns error")
    void getWordInfo_emptyWord_returnsError() throws Exception {
        // PathVariable with empty string won't match the route, so we test with a very long word
        // The controller checks for null, empty, or length > 100
        String longWord = "a".repeat(101);

        mockMvc.perform(get("/api/wordnet/word/" + longWord))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词参数无效"));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - word not found returns info with found=false")
    void getWordInfo_notFound_returnsNotFoundInfo() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "xyzabc");
        wordInfo.put("found", false);

        when(wordNetService.getWordInfo("xyzabc")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/xyzabc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - exception returns error")
    void getWordInfo_exception_returnsError() throws Exception {
        when(wordNetService.getWordInfo("test"))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/word/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询单词信息失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/search ==========

    @Test
    @DisplayName("GET /api/wordnet/search - returns search results")
    void searchWords_validKeyword_returnsResults() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("word", "apple");
        item.put("definition", "A fruit");
        results.add(item);

        when(wordNetService.searchWords(eq("app"), anyInt())).thenReturn(results);

        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].word").value("apple"));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - empty keyword returns error")
    void searchWords_emptyKeyword_returnsError() throws Exception {
        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("关键词不能为空且不超过100字符"));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - keyword too long returns error")
    void searchWords_keywordTooLong_returnsError() throws Exception {
        String longKeyword = "a".repeat(101);

        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", longKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("关键词不能为空且不超过100字符"));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - custom limit parameter")
    void searchWords_customLimit() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        when(wordNetService.searchWords(eq("test"), eq(20))).thenReturn(results);

        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", "test")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - negative limit defaults to 10")
    void searchWords_negativeLimit_defaultsTo10() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        when(wordNetService.searchWords(eq("test"), anyInt())).thenReturn(results);

        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", "test")
                        .param("limit", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // The controller resets negative limit to 10, then clamps with Math.max(1, Math.min(10, 100)) = 10
        verify(wordNetService).searchWords(eq("test"), eq(10));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - exception returns error")
    void searchWords_exception_returnsError() throws Exception {
        when(wordNetService.searchWords(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/search")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索词汇失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/stats ==========

    @Test
    @DisplayName("GET /api/wordnet/stats - returns dictionary statistics")
    void getDictionaryStats_returnsStats() throws Exception {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWords", 50000);
        stats.put("primaryDictionary", "ECDICT");

        when(wordNetService.getDictionaryStats()).thenReturn(stats);

        mockMvc.perform(get("/api/wordnet/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(50000))
                .andExpect(jsonPath("$.data.primaryDictionary").value("ECDICT"));
    }

    @Test
    @DisplayName("GET /api/wordnet/stats - exception returns error")
    void getDictionaryStats_exception_returnsError() throws Exception {
        when(wordNetService.getDictionaryStats())
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取词典统计失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/ecdict/status ==========

    @Test
    @DisplayName("GET /api/wordnet/ecdict/status - ECDICT available returns full status")
    void getECDICTStatus_available_returnsFullStatus() throws Exception {
        when(ecdictService.isECDICTAvailable()).thenReturn(true);

        Map<String, Object> ecdictStats = new HashMap<>();
        ecdictStats.put("totalWords", 770000);
        when(ecdictService.getDictionaryStats()).thenReturn(ecdictStats);

        mockMvc.perform(get("/api/wordnet/ecdict/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.totalWords").value(770000));
    }

    @Test
    @DisplayName("GET /api/wordnet/ecdict/status - ECDICT unavailable returns message")
    void getECDICTStatus_unavailable_returnsMessage() throws Exception {
        when(ecdictService.isECDICTAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/wordnet/ecdict/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value("ECDICT服务不可用"));
    }

    @Test
    @DisplayName("GET /api/wordnet/ecdict/status - exception returns error")
    void getECDICTStatus_exception_returnsError() throws Exception {
        when(ecdictService.isECDICTAvailable())
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/ecdict/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取ECDICT状态失败，请稍后重试"));
    }

    // ========== POST /api/wordnet/text/words ==========

    @Test
    @DisplayName("POST /api/wordnet/text/words - returns clickable words from text")
    void getClickableWords_validText_returnsWords() throws Exception {
        List<Map<String, Object>> clickableWords = new ArrayList<>();
        Map<String, Object> word1 = new HashMap<>();
        word1.put("text", "Hello");
        word1.put("cleanWord", "hello");
        word1.put("clickable", true);
        clickableWords.add(word1);

        when(wordNetService.getClickableWordsFromText("Hello world")).thenReturn(clickableWords);

        Map<String, String> requestBody = Map.of("text", "Hello world");

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].text").value("Hello"));
    }

    @Test
    @DisplayName("POST /api/wordnet/text/words - empty text returns error")
    void getClickableWords_emptyText_returnsError() throws Exception {
        Map<String, String> requestBody = Map.of("text", "");

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文本内容不能为空"));
    }

    @Test
    @DisplayName("POST /api/wordnet/text/words - missing text field returns error")
    void getClickableWords_missingTextField_returnsError() throws Exception {
        Map<String, String> requestBody = new HashMap<>();

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文本内容不能为空"));
    }

    @Test
    @DisplayName("POST /api/wordnet/text/words - text too long returns error")
    void getClickableWords_textTooLong_returnsError() throws Exception {
        String longText = "a".repeat(50001);
        Map<String, String> requestBody = Map.of("text", longText);

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文本内容过长"));
    }

    @Test
    @DisplayName("POST /api/wordnet/text/words - exception returns error")
    void getClickableWords_exception_returnsError() throws Exception {
        when(wordNetService.getClickableWordsFromText(anyString()))
                .thenThrow(new RuntimeException("Service error"));

        Map<String, String> requestBody = Map.of("text", "Some text");

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取可点击单词失败，请稍后重试"));
    }

    // ========== POST /api/wordnet/words/batch ==========

    @Test
    @DisplayName("POST /api/wordnet/words/batch - returns batch word info")
    void getBatchWordInfo_validRequest_returnsInfo() throws Exception {
        Map<String, Object> appleInfo = new HashMap<>();
        appleInfo.put("word", "apple");
        appleInfo.put("found", true);

        when(wordNetService.getWordInfo("apple")).thenReturn(appleInfo);
        when(wordNetService.getWordInfo("banana")).thenReturn(Map.of("word", "banana", "found", true));

        Map<String, Object> requestBody = Map.of("words", List.of("apple", "banana"));

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.apple.word").value("apple"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - non-list words field returns error")
    void getBatchWordInfo_nonListWords_returnsError() throws Exception {
        Map<String, Object> requestBody = Map.of("words", "not-a-list");

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词列表格式错误"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - empty words list returns error")
    void getBatchWordInfo_emptyList_returnsError() throws Exception {
        Map<String, Object> requestBody = Map.of("words", Collections.emptyList());

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词列表不能为空"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - too many words returns error")
    void getBatchWordInfo_tooManyWords_returnsError() throws Exception {
        List<String> manyWords = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            manyWords.add("word" + i);
        }
        Map<String, Object> requestBody = Map.of("words", manyWords);

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("批量查询最多支持200个单词"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - word too long returns error")
    void getBatchWordInfo_wordTooLong_returnsError() throws Exception {
        String longWord = "a".repeat(101);
        Map<String, Object> requestBody = Map.of("words", List.of(longWord));

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词参数无效（长度应在1-100字符）"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - exception returns error")
    void getBatchWordInfo_exception_returnsError() throws Exception {
        when(wordNetService.getWordInfo("test"))
                .thenThrow(new RuntimeException("Service error"));

        Map<String, Object> requestBody = Map.of("words", List.of("test"));

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("批量查询单词信息失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/word/{word}/get-or-create ==========

    @Test
    @DisplayName("GET /api/wordnet/word/{word}/get-or-create - authenticated user returns word info")
    void getOrCreateWord_authenticated_returnsInfo() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "hello");
        wordInfo.put("found", true);
        wordInfo.put("wordId", 1L);

        when(wordNetService.getOrCreateWord("hello")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/hello/get-or-create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("hello"))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word}/get-or-create - word too long returns error")
    void getOrCreateWord_wordTooLong_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        String longWord = "a".repeat(101);

        mockMvc.perform(get("/api/wordnet/word/" + longWord + "/get-or-create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词参数无效"));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word}/get-or-create - exception returns error")
    void getOrCreateWord_exception_returnsError() throws Exception {
        TestHelper.createMockAuthentication("student1", "USER");

        when(wordNetService.getOrCreateWord("test"))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/word/test/get-or-create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询或创建单词信息失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/status ==========

    @Test
    @DisplayName("GET /api/wordnet/status - returns WordNet available status")
    void getWordNetStatus_available_returnsStatus() throws Exception {
        when(wordNetService.isWordNetAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/wordnet/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.message").value("WordNet服务正常"));
    }

    @Test
    @DisplayName("GET /api/wordnet/status - returns WordNet unavailable status")
    void getWordNetStatus_unavailable_returnsStatus() throws Exception {
        when(wordNetService.isWordNetAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/wordnet/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value("WordNet服务不可用，使用数据库词汇数据"));
    }

    @Test
    @DisplayName("GET /api/wordnet/status - exception returns error")
    void getWordNetStatus_exception_returnsError() throws Exception {
        when(wordNetService.isWordNetAvailable())
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取WordNet状态失败，请稍后重试"));
    }
}
