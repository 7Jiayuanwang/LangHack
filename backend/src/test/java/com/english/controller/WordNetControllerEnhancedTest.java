package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.User;
import com.english.service.ECDICTService;
import com.english.service.WordNetService;
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

@DisplayName("WordNetController Enhanced Tests")
class WordNetControllerEnhancedTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private WordNetService wordNetService;
    @Mock private ECDICTService ecdictService;

    @InjectMocks
    private WordNetController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter())
                .build();

        setUpSecurityContext("student1", "ROLE_USER");
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

    // ========== GET /api/wordnet/word/{word} ==========

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - success")
    void getWordInfo_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("found", true);
        wordInfo.put("word", "hello");
        wordInfo.put("definition", "greeting");
        when(wordNetService.getWordInfo("hello")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - service exception")
    void getWordInfo_serviceException() throws Exception {
        when(wordNetService.getWordInfo("crash")).thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/wordnet/word/crash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("查询单词信息失败，请稍后重试"));
    }

    // ========== GET /api/wordnet/search ==========

    @Test
    @DisplayName("GET /api/wordnet/search - success")
    void searchWords_success() throws Exception {
        List<Map<String, Object>> results = List.of(Map.of("word", "test", "definition", "exam"));
        when(wordNetService.searchWords(eq("test"), anyInt())).thenReturn(results);

        mockMvc.perform(get("/api/wordnet/search").param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].word").value("test"));
    }

    @Test
    @DisplayName("GET /api/wordnet/search - empty keyword")
    void searchWords_emptyKeyword() throws Exception {
        mockMvc.perform(get("/api/wordnet/search").param("keyword", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("关键词不能为空且不超过100字符"));
    }

    // ========== GET /api/wordnet/stats ==========

    @Test
    @DisplayName("GET /api/wordnet/stats - success")
    void getDictionaryStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalWords", 5000, "primaryDictionary", "ECDICT");
        when(wordNetService.getDictionaryStats()).thenReturn(stats);

        mockMvc.perform(get("/api/wordnet/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(5000));
    }

    // ========== GET /api/wordnet/ecdict/status ==========

    @Test
    @DisplayName("GET /api/wordnet/ecdict/status - available")
    void getECDICTStatus_available() throws Exception {
        when(ecdictService.isECDICTAvailable()).thenReturn(true);
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalWords", 10000);
        when(ecdictService.getDictionaryStats()).thenReturn(stats);

        mockMvc.perform(get("/api/wordnet/ecdict/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/ecdict/status - unavailable")
    void getECDICTStatus_unavailable() throws Exception {
        when(ecdictService.isECDICTAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/wordnet/ecdict/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(false));
    }

    // ========== POST /api/wordnet/text/words ==========

    @Test
    @DisplayName("POST /api/wordnet/text/words - success")
    void getClickableWords_success() throws Exception {
        List<Map<String, Object>> words = List.of(
                Map.of("text", "Hello", "cleanWord", "hello", "position", 0));
        when(wordNetService.getClickableWordsFromText("Hello world")).thenReturn(words);

        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", "Hello world"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/wordnet/text/words - empty text")
    void getClickableWords_emptyText() throws Exception {
        mockMvc.perform(post("/api/wordnet/text/words")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("text", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("文本内容不能为空"));
    }

    // ========== POST /api/wordnet/words/batch ==========

    @Test
    @DisplayName("POST /api/wordnet/words/batch - success")
    void getBatchWordInfo_success() throws Exception {
        Map<String, Object> helloInfo = Map.of("found", true, "word", "hello");
        when(wordNetService.getWordInfo("hello")).thenReturn(helloInfo);
        Map<String, Object> worldInfo = Map.of("found", true, "word", "world");
        when(wordNetService.getWordInfo("world")).thenReturn(worldInfo);

        Map<String, Object> request = Map.of("words", List.of("hello", "world"));

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - invalid format")
    void getBatchWordInfo_invalidFormat() throws Exception {
        Map<String, Object> request = Map.of("words", "not-a-list");

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词列表格式错误"));
    }

    @Test
    @DisplayName("POST /api/wordnet/words/batch - empty list")
    void getBatchWordInfo_emptyList() throws Exception {
        Map<String, Object> request = Map.of("words", List.of());

        mockMvc.perform(post("/api/wordnet/words/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词列表不能为空"));
    }

    // ========== GET /api/wordnet/word/{word}/get-or-create ==========

    @Test
    @DisplayName("GET /api/wordnet/word/{word}/get-or-create - success")
    void getOrCreateWord_success() throws Exception {
        Map<String, Object> result = Map.of("found", true, "word", "example", "wordId", 1);
        when(wordNetService.getOrCreateWord("example")).thenReturn(result);

        mockMvc.perform(get("/api/wordnet/word/example/get-or-create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    // ========== GET /api/wordnet/status ==========

    @Test
    @DisplayName("GET /api/wordnet/status - success")
    void getWordNetStatus_success() throws Exception {
        when(wordNetService.isWordNetAvailable()).thenReturn(true);

        mockMvc.perform(get("/api/wordnet/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.available").value(true));
    }
}
