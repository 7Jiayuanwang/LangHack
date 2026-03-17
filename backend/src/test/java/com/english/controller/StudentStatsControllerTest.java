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
 * StudentStatsController unit tests.
 *
 * Covers: today stats, overview, learning records (with pagination),
 * word study stats, course learning stats (specific and all courses),
 * study time stats, progress report, achievements, ranking, export,
 * and error paths.
 */
class StudentStatsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private VocabularyBooksService vocabularyBooksService;

    @Mock
    private StudentClassesService studentClassesService;

    @Mock
    private UserService userService;

    @InjectMocks
    private StudentStatsController controller;

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
    void getTodayLearningStats_success() throws Exception {
        Map<String, Object> stats = Map.of(
                "studyTime", 120,
                "wordsLearned", 15,
                "textsRead", 3);
        when(learningRecordsService.getTodayLearningStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/stats/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studyTime").value(120))
                .andExpect(jsonPath("$.data.wordsLearned").value(15));
    }

    @Test
    void getLearningOverview_success() throws Exception {
        Map<String, Object> overview = Map.of(
                "totalStudyTime", 3600,
                "totalWordsLearned", 100);
        when(learningRecordsService.getLearningOverview(eq(1L), anyMap())).thenReturn(overview);

        mockMvc.perform(get("/api/student/stats/overview")
                        .param("period", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalStudyTime").value(3600));
    }

    @Test
    void getLearningRecords_success() throws Exception {
        Map<String, Object> records = Map.of("records", List.of(), "total", 0);
        when(learningRecordsService.getLearningRecordsWithPagination(anyMap())).thenReturn(records);

        mockMvc.perform(get("/api/student/stats/records")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getLearningRecords_invalidArgument_returnsError() throws Exception {
        when(learningRecordsService.getLearningRecordsWithPagination(anyMap()))
                .thenThrow(new IllegalArgumentException("Invalid date range"));

        mockMvc.perform(get("/api/student/stats/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("请求参数无效，请检查后重试"));
    }

    @Test
    void getWordStudyStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalWords", 200, "mastered", 80);
        when(vocabularyBooksService.getWordStudyStats(eq(1L), isNull(), isNull(), isNull()))
                .thenReturn(stats);

        mockMvc.perform(get("/api/student/stats/words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalWords").value(200));
    }

    @Test
    void getCourseLearningStats_specificCourse_success() throws Exception {
        Map<String, Object> stats = Map.of("progress", 75);
        when(learningRecordsService.getCourseLearningStats(1L, 5L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/stats/courses")
                        .param("courseId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.progress").value(75));
    }

    @Test
    void getCourseLearningStats_allCourses_success() throws Exception {
        Map<String, Object> stats = Map.of("courses", List.of());
        when(learningRecordsService.getAllCoursesLearningStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/student/stats/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getStudyTimeStats_success() throws Exception {
        Map<String, Object> stats = Map.of("totalMinutes", 600);
        when(learningRecordsService.getStudyTimeStats(eq(1L), anyMap())).thenReturn(stats);

        mockMvc.perform(get("/api/student/stats/study-time")
                        .param("period", "monthly").param("granularity", "daily"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalMinutes").value(600));
    }

    @Test
    void getProgressReport_success() throws Exception {
        Map<String, Object> report = Map.of("period", "weekly", "improvement", 15);
        when(learningRecordsService.generateProgressReport(1L, "weekly")).thenReturn(report);

        mockMvc.perform(get("/api/student/stats/progress-report")
                        .param("type", "weekly"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.improvement").value(15));
    }

    @Test
    void getAchievementStats_success() throws Exception {
        Map<String, Object> achievements = Map.of("badges", List.of("streak_7"));
        when(learningRecordsService.getAchievementStats(1L)).thenReturn(achievements);

        mockMvc.perform(get("/api/student/stats/achievements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.badges").isArray());
    }

    @Test
    void getLearningRanking_success() throws Exception {
        Map<String, Object> ranking = Map.of("rank", 5, "total", 30);
        when(learningRecordsService.getLearningRanking(eq(1L), eq("class"), eq(100L)))
                .thenReturn(ranking);

        mockMvc.perform(get("/api/student/stats/ranking")
                        .param("type", "class").param("targetId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.rank").value(5));
    }

    @Test
    void exportLearningData_success() throws Exception {
        Map<String, Object> exportResult = Map.of("url", "/exports/data.xlsx");
        when(learningRecordsService.exportLearningData(eq(1L), anyMap())).thenReturn(exportResult);

        mockMvc.perform(get("/api/student/stats/export")
                        .param("format", "excel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("/exports/data.xlsx"));
    }

    @Test
    void anyEndpoint_userNotFound_returnsBusinessException() throws Exception {
        when(userService.getUserByUsername("student1")).thenReturn(null);

        mockMvc.perform(get("/api/student/stats/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("用户不存在"));
    }

    @Test
    void anyEndpoint_serviceException_returnsGenericError() throws Exception {
        when(learningRecordsService.getTodayLearningStats(1L))
                .thenThrow(new RuntimeException("DB connection lost"));

        mockMvc.perform(get("/api/student/stats/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("获取今日统计失败，请稍后重试"));
    }
}
