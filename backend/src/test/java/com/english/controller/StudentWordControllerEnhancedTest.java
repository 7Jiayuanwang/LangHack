package com.english.controller;

import com.english.test.TestUtils;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.service.*;
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
 * Enhanced StudentWordController unit tests.
 * Covers: word detail, add to vocabulary, vocabulary list, update mastery level.
 */
@DisplayName("StudentWordController Enhanced Tests")
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

    private User studentUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(TestUtils.authenticationFilter()).build();

        setUpSecurityContext("student1", "ROLE_USER");

        studentUser = new User();
        studentUser.setId(1L);
        studentUser.setUsername("student1");
        studentUser.setRole("USER");
        when(userService.getUserByUsername("student1")).thenReturn(studentUser);
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

    @Test
    @DisplayName("GET /api/student/words/{wordId} - get word detail success")
    void getWordDetail_success() throws Exception {
        Map<String, Object> wordInfo = new HashMap<>();
        wordInfo.put("wordId", 1L);
        wordInfo.put("word", "apple");
        wordInfo.put("definition", "a fruit");
        when(wordsService.getWordById(1L)).thenReturn(wordInfo);

        mockMvc.perform(get("/api/student/words/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.word").value("apple"));
    }

    @Test
    @DisplayName("POST /api/student/vocabulary - add word by wordId success")
    void addWordToVocabulary_byWordId_success() throws Exception {
        doNothing().when(vocabularyBooksService).addWord(1L, 1L);

        Map<String, Object> req = new HashMap<>();
        req.put("wordId", 1);

        mockMvc.perform(post("/api/student/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(vocabularyBooksService).addWord(1L, 1L);
    }

    @Test
    @DisplayName("GET /api/student/vocabulary - get vocabulary list success")
    void getVocabulary_success() throws Exception {
        Map<String, Object> result = Map.of("records", List.of(), "total", 0);
        when(vocabularyBooksService.getStudentVocabulary(1L, anyMap())).thenReturn(result);

        mockMvc.perform(get("/api/student/vocabulary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    @DisplayName("PUT /api/student/vocabulary/{wordId} - update mastery level success")
    void updateMasteryLevel_success() throws Exception {
        doNothing().when(learningRecordsService).updateMastery(1L, 1L, 3);

        Map<String, Object> req = new HashMap<>();
        req.put("masteryLevel", 3);

        mockMvc.perform(put("/api/student/vocabulary/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(learningRecordsService).updateMastery(1L, 1L, 3);
    }

    @Test
    @DisplayName("POST /api/student/vocabulary - user not found returns error")
    void addWordToVocabulary_userNotFound_returnsError() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        Map<String, Object> req = new HashMap<>();
        req.put("wordId", 1);

        mockMvc.perform(post("/api/student/vocabulary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    @DisplayName("GET /api/student/words/stats - get word study stats success")
    void getWordStudyStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalWords", 100, "masteredWords", 50);
        when(learningRecordsService.getStudyStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/words/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(100));
    }
}
