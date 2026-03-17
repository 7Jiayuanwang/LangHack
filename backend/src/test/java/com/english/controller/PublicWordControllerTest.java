package com.english.controller;
import com.english.test.TestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

class PublicWordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WordService wordService;

    @InjectMocks
    private PublicWordController publicWordController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(publicWordController).addFilters(TestUtils.authenticationFilter()).build();
    }

    @AfterEach
    void tearDown() {
        TestHelper.clearSecurityContext();
    }

    // ========== GET /api/public/words ==========

    @Test
    @DisplayName("GET /api/public/words - returns paginated word list")
    void getWords_returnsPaginatedList() throws Exception {
        Page<Word> mockPage = new Page<>(1, 100);
        mockPage.setRecords(List.of(createMockWord(1L, "apple"), createMockWord(2L, "banana")));
        mockPage.setTotal(2);

        when(wordService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        mockMvc.perform(get("/api/public/words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @DisplayName("GET /api/public/words - with pagination parameters")
    void getWords_withPagination() throws Exception {
        Page<Word> mockPage = new Page<>(2, 50);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(100);

        when(wordService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        mockMvc.perform(get("/api/public/words")
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/public/words - with keyword search")
    void getWords_withKeyword() throws Exception {
        Page<Word> mockPage = new Page<>(1, 100);
        mockPage.setRecords(List.of(createMockWord(1L, "apple")));
        mockPage.setTotal(1);

        when(wordService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        mockMvc.perform(get("/api/public/words")
                        .param("keyword", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("GET /api/public/words - keyword too long returns error")
    void getWords_keywordTooLong_returnsError() throws Exception {
        String longKeyword = "a".repeat(101);

        mockMvc.perform(get("/api/public/words")
                        .param("keyword", longKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索关键词过长"));
    }

    @Test
    @DisplayName("GET /api/public/words - page/size parameters are clamped")
    void getWords_parametersClamped() throws Exception {
        Page<Word> mockPage = new Page<>(1, 100);
        mockPage.setRecords(Collections.emptyList());
        mockPage.setTotal(0);

        when(wordService.page(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);

        // Negative page and size should be clamped
        mockMvc.perform(get("/api/public/words")
                        .param("page", "-1")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("GET /api/public/words - exception returns error")
    void getWords_exception_returnsError() throws Exception {
        when(wordService.page(any(Page.class), any(QueryWrapper.class)))
                .thenThrow(new RuntimeException("Database error"));

        mockMvc.perform(get("/api/public/words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取单词列表失败，请稍后重试"));
    }

    // ========== GET /api/public/words/search ==========

    @Test
    @DisplayName("GET /api/public/words/search - returns matching words")
    void searchWords_returnsResults() throws Exception {
        List<Word> words = List.of(createMockWord(1L, "apple"), createMockWord(2L, "application"));
        when(wordService.list(any(QueryWrapper.class))).thenReturn(words);

        mockMvc.perform(get("/api/public/words/search")
                        .param("word", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/public/words/search - empty keyword returns error")
    void searchWords_emptyKeyword_returnsError() throws Exception {
        mockMvc.perform(get("/api/public/words/search")
                        .param("word", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索关键词不能为空"));
    }

    @Test
    @DisplayName("GET /api/public/words/search - whitespace-only keyword returns error")
    void searchWords_whitespaceKeyword_returnsError() throws Exception {
        mockMvc.perform(get("/api/public/words/search")
                        .param("word", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索关键词不能为空"));
    }

    @Test
    @DisplayName("GET /api/public/words/search - keyword too long returns error")
    void searchWords_keywordTooLong_returnsError() throws Exception {
        String longKeyword = "a".repeat(101);

        mockMvc.perform(get("/api/public/words/search")
                        .param("word", longKeyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索关键词过长"));
    }

    @Test
    @DisplayName("GET /api/public/words/search - exception returns error")
    void searchWords_exception_returnsError() throws Exception {
        when(wordService.list(any(QueryWrapper.class)))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/public/words/search")
                        .param("word", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("搜索单词失败，请稍后重试"));
    }

    // ========== GET /api/public/words/{wordId} ==========

    @Test
    @DisplayName("GET /api/public/words/{wordId} - existing word returns detail")
    void getWordDetail_existingWord_returnsDetail() throws Exception {
        Word word = createMockWord(1L, "apple");
        word.setDefinition("A round fruit");
        word.setPronunciation("/aepl/");

        when(wordService.getOne(any(QueryWrapper.class))).thenReturn(word);

        mockMvc.perform(get("/api/public/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.word").value("apple"))
                .andExpect(jsonPath("$.data.definition").value("A round fruit"));
    }

    @Test
    @DisplayName("GET /api/public/words/{wordId} - non-existent word returns error")
    void getWordDetail_nonExistent_returnsError() throws Exception {
        when(wordService.getOne(any(QueryWrapper.class))).thenReturn(null);

        mockMvc.perform(get("/api/public/words/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("单词不存在"));
    }

    @Test
    @DisplayName("GET /api/public/words/{wordId} - exception returns error")
    void getWordDetail_exception_returnsError() throws Exception {
        when(wordService.getOne(any(QueryWrapper.class)))
                .thenThrow(new RuntimeException("DB error"));

        mockMvc.perform(get("/api/public/words/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取单词详情失败，请稍后重试"));
    }

    // ========== Helper Methods ==========

    private Word createMockWord(Long id, String wordText) {
        Word word = new Word();
        word.setId(id);
        word.setWord(wordText);
        word.setDeleted(0);
        return word;
    }
}
