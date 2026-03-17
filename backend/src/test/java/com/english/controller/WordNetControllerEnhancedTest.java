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

/**
 * Enhanced WordNetController unit tests.
 * Covers: public access (no auth required), word info lookup,
 * special character inputs, word not found, validation.
 */
@DisplayName("WordNetController Enhanced Tests")
class WordNetControllerEnhancedTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(wordNetController)
                .addFilters(TestUtils.authenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - get valid word info success")
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
                .andExpect(jsonPath("$.data.found").value(true));

        verify(wordNetService).getWordInfo("apple");
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - word not found")
    void getWordInfo_wordNotFound_returnsFalse() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("found", false);
        when(wordNetService.getWordInfo("nonexistentword123")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/nonexistentword123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - word too long returns error")
    void getWordInfo_wordTooLong_returnsError() throws Exception {
        String longWord = "a".repeat(101);

        mockMvc.perform(get("/api/wordnet/word/" + longWord))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词参数无效"));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - special characters in word")
    void getWordInfo_specialCharacters_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "don't");
        wordInfo.put("found", true);
        when(wordNetService.getWordInfo("don't")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/don't"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("don't"));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - word with numbers")
    void getWordInfo_wordWithNumbers_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "3d");
        wordInfo.put("found", true);
        when(wordNetService.getWordInfo("3d")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/3d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - case insensitive lookup")
    void getWordInfo_caseInsensitive_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "APPLE");
        wordInfo.put("found", true);
        when(wordNetService.getWordInfo("APPLE")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/APPLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - service exception returns error")
    void getWordInfo_serviceException_returnsError() throws Exception {
        when(wordNetService.getWordInfo("apple"))
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/wordnet/word/apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - no authentication required (public)")
    void getWordInfo_publicAccess_noAuthRequired() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "apple");
        wordInfo.put("found", true);
        when(wordNetService.getWordInfo("apple")).thenReturn(wordInfo);

        // No authentication header, should still work
        mockMvc.perform(get("/api/wordnet/word/apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - with multiple related words")
    void getWordInfo_withRelatedWords_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "run");
        wordInfo.put("found", true);
        wordInfo.put("synonyms", List.of("sprint", "jog", "dash"));
        wordInfo.put("antonyms", List.of("walk", "stop"));
        when(wordNetService.getWordInfo("run")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.synonyms").isArray())
                .andExpect(jsonPath("$.data.synonyms[0]").value("sprint"));
    }

    @Test
    @DisplayName("GET /api/wordnet/word/{word} - unicode characters")
    void getWordInfo_unicodeCharacters_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("word", "café");
        wordInfo.put("found", true);
        when(wordNetService.getWordInfo("café")).thenReturn(wordInfo);

        mockMvc.perform(get("/api/wordnet/word/café"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("café"));
    }

    @Test
    @DisplayName("GET /api/wordnet/definitions/{word} - get ECDICT definitions")
    void getDefinitions_success() throws Exception {
        Map<String, Object> definitions = new HashMap<>();
        definitions.put("word", "apple");
        definitions.put("definitions", List.of("a round red fruit"));
        when(ecdictService.getDefinition("apple")).thenReturn(definitions);

        mockMvc.perform(get("/api/wordnet/definitions/apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("apple"));
    }
}
