package com.english.controller;

import com.english.dto.learner.*;
import com.english.entity.LearnerCompositeTag;
import com.english.entity.LearnerProfileSummary;
import com.english.entity.User;
import com.english.mapper.LearnerCompositeTagMapper;
import com.english.mapper.LearnerProfileSnapshotMapper;
import com.english.service.*;
import com.english.test.TestUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
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

class LearnerProfileV3IntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── LearnerProfileController (Admin V3 endpoints) ──

    @Nested
    @DisplayName("Admin V3 端点 — LLM 摘要 + 队列分析")
    class AdminV3Tests {

        private MockMvc mockMvc;

        @Mock private LearnerProfileService learnerProfileService;
        @Mock private LearnerProfileSnapshotService snapshotService;
        @Mock private RecommendationTrackingService trackingService;
        @Mock private TagRecalibrationService recalibrationService;
        @Mock private LearnerProfileSummaryService summaryService;
        @Mock private ClassCohortAnalysisService cohortAnalysisService;
        @Mock private LearnerCompositeTagMapper compositeTagMapper;
        @Mock private LearnerProfileSnapshotMapper snapshotMapper;
        @Mock private CacheManager cacheManager;

        @InjectMocks
        private LearnerProfileController controller;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .addFilters(TestUtils.authenticationFilter()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("POST /{userId}/generate-summary → 生成 LLM 摘要")
        void generateSummary_success() throws Exception {
            LearnerProfileSummary summary = new LearnerProfileSummary();
            summary.setId(1L);
            summary.setUserId(1L);
            summary.setSummaryText("该学生学习积极，词汇掌握良好");
            summary.setSummaryType("TEACHER_FACING");
            when(summaryService.generateSummary(1L, "TEACHER_FACING")).thenReturn(summary);

            mockMvc.perform(post("/api/admin/learner-profile/1/generate-summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.summaryText").value("该学生学习积极，词汇掌握良好"))
                    .andExpect(jsonPath("$.data.summaryType").value("TEACHER_FACING"));
        }

        @Test
        @DisplayName("POST /{userId}/generate-summary — 生成失败返回错误")
        void generateSummary_failure() throws Exception {
            when(summaryService.generateSummary(1L, "TEACHER_FACING")).thenReturn(null);

            mockMvc.perform(post("/api/admin/learner-profile/1/generate-summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("生成摘要失败"));
        }

        @Test
        @DisplayName("POST /{userId}/generate-summary — summaryService 为 null")
        void generateSummary_serviceNull() throws Exception {
            LearnerProfileController ctrlNoSummary = new LearnerProfileController();
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSummary, "compositeTagMapper", compositeTagMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSummary, "snapshotMapper", snapshotMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSummary, "learnerProfileService", learnerProfileService);
            MockMvc mvc2 = MockMvcBuilders.standaloneSetup(ctrlNoSummary)
                    .addFilters(TestUtils.authenticationFilter()).build();

            mvc2.perform(post("/api/admin/learner-profile/1/generate-summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("LLM摘要功能未启用"));
        }

        @Test
        @DisplayName("GET /class/{classId}/cohort-analysis → 返回队列分析")
        void cohortAnalysis_success() throws Exception {
            ClassCohortAnalysisDto dto = new ClassCohortAnalysisDto();
            dto.setClassId(10L);
            dto.setClassName("英语1班");
            dto.setTotalStudents(30);
            dto.setRiskDistribution(Map.of("low", 20, "medium", 8, "high", 2));
            dto.setAvgActiveDays7d(4.5);
            when(cohortAnalysisService.analyzeCohort(10L)).thenReturn(dto);

            mockMvc.perform(get("/api/admin/learner-profile/class/10/cohort-analysis"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.classId").value(10))
                    .andExpect(jsonPath("$.data.totalStudents").value(30))
                    .andExpect(jsonPath("$.data.riskDistribution.high").value(2))
                    .andExpect(jsonPath("$.data.avgActiveDays7d").value(4.5));
        }

        @Test
        @DisplayName("GET /class/{classId}/cohort-analysis — 无效班级ID")
        void cohortAnalysis_invalidClassId() throws Exception {
            mockMvc.perform(get("/api/admin/learner-profile/class/-1/cohort-analysis"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("无效的班级ID"));
        }

        @Test
        @DisplayName("GET /class/{classId}/cohort-analysis — cohortService 为 null")
        void cohortAnalysis_serviceNull() throws Exception {
            LearnerProfileController ctrlNoCohort = new LearnerProfileController();
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoCohort, "compositeTagMapper", compositeTagMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoCohort, "snapshotMapper", snapshotMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoCohort, "learnerProfileService", learnerProfileService);
            MockMvc mvc2 = MockMvcBuilders.standaloneSetup(ctrlNoCohort)
                    .addFilters(TestUtils.authenticationFilter()).build();

            mvc2.perform(get("/api/admin/learner-profile/class/10/cohort-analysis"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("队列分析功能未启用"));
        }

        @Test
        @DisplayName("GET /class-comparison → 多班级对比")
        void classComparison_success() throws Exception {
            ClassCohortAnalysisDto dto1 = new ClassCohortAnalysisDto();
            dto1.setClassId(1L);
            dto1.setTotalStudents(25);
            ClassCohortAnalysisDto dto2 = new ClassCohortAnalysisDto();
            dto2.setClassId(2L);
            dto2.setTotalStudents(30);
            when(cohortAnalysisService.compareClasses(List.of(1L, 2L)))
                    .thenReturn(List.of(dto1, dto2));

            mockMvc.perform(get("/api/admin/learner-profile/class-comparison?classIds=1,2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].classId").value(1))
                    .andExpect(jsonPath("$.data[1].totalStudents").value(30));
        }

        @Test
        @DisplayName("GET /class-comparison — 超过5个班级报错")
        void classComparison_tooMany() throws Exception {
            mockMvc.perform(get("/api/admin/learner-profile/class-comparison?classIds=1,2,3,4,5,6"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("请提供1-5个班级ID"));
        }
    }

    // ── StudentProfileController ──

    @Nested
    @DisplayName("Student V3 端点 — 学生端画像")
    class StudentProfileTests {

        private MockMvc mockMvc;

        @Mock private LearnerProfileService learnerProfileService;
        @Mock private LearnerProfileSummaryService summaryService;
        @Mock private LearningRecordsService learningRecordsService;
        @Mock private UserService userService;

        @InjectMocks
        private StudentProfileController controller;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
            when(learningRecordsService.getAchievementStats(anyLong())).thenReturn(Collections.emptyMap());
            mockMvc = MockMvcBuilders.standaloneSetup(controller)
                    .addFilters(TestUtils.authenticationFilter()).build();

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("student1", null,
                            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        }

        @AfterEach
        void tearDown() {
            SecurityContextHolder.clearContext();
        }

        private LearnerProfileDto buildFullProfile() {
            LearnerProfileDto dto = new LearnerProfileDto();
            dto.setProfileStatus("ready");

            LearnerIdentityDto identity = new LearnerIdentityDto();
            identity.setRealName("李四");
            dto.setIdentity(identity);

            LearnerAbilityDto ability = new LearnerAbilityDto();
            ability.setTotalWords(200);
            ability.setMasteredWords(150);
            dto.setAbility(ability);

            LearnerHabitDto habit = new LearnerHabitDto();
            habit.setActiveDays7d(6);
            habit.setConsecutiveDays(3);
            habit.setStudyMinutes14d(180);
            dto.setHabit(habit);

            LearnerPreferenceDto pref = new LearnerPreferenceDto();
            pref.setPrimaryContentVersion("cet6");
            pref.setTargetExamLevel("CET-6");
            dto.setPreference(pref);

            LearnerRiskDto risk = new LearnerRiskDto();
            risk.setRiskLevel("low");
            dto.setRisk(risk);

            dto.setTags(List.of(
                    new LearnerTagDto("CONSISTENT_LEARNER", "学习稳定", "positive", 40, "保持"),
                    new LearnerTagDto("REVIEW_CHAMPION", "复习达人", "positive", 50, "继续保持"),
                    new LearnerTagDto("VOCAB_WEAK", "词汇弱势", "risk", 30, "加强词汇练习")
            ));

            return dto;
        }

        @Test
        @DisplayName("GET /me → 返回学生视角画像（隐藏风险标签）")
        void getMyProfile_success() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");
            when(userService.getUserByUsername("student1")).thenReturn(user);
            when(learnerProfileService.getUserProfile(1L)).thenReturn(buildFullProfile());

            LearnerProfileSummary summary = new LearnerProfileSummary();
            summary.setSummaryText("你的学习状态很好，继续加油！");
            when(summaryService.getLatestSummary(1L, "PERSONAL")).thenReturn(summary);

            mockMvc.perform(get("/api/student/profile/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.identity.realName").value("李四"))
                    .andExpect(jsonPath("$.data.activeDays7d").value(6))
                    .andExpect(jsonPath("$.data.studyMinutes14d").value(180))
                    .andExpect(jsonPath("$.data.primaryContentVersion").value("cet6"))
                    .andExpect(jsonPath("$.data.naturalLanguageSummary").value("你的学习状态很好，继续加油！"))
                    .andExpect(jsonPath("$.data.positiveTags[0].code").value("CONSISTENT_LEARNER"))
                    .andExpect(jsonPath("$.data.positiveTags[1].code").value("REVIEW_CHAMPION"));
        }

        @Test
        @DisplayName("GET /me — 过滤 risk 标签，仅返回 positive/ability")
        void getMyProfile_filtersRiskTags() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");
            when(userService.getUserByUsername("student1")).thenReturn(user);
            when(learnerProfileService.getUserProfile(1L)).thenReturn(buildFullProfile());

            mockMvc.perform(get("/api/student/profile/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.positiveTags.length()").value(2));
        }

        @Test
        @DisplayName("GET /me — 空画像返回 null data")
        void getMyProfile_emptyProfile() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");
            when(userService.getUserByUsername("student1")).thenReturn(user);

            LearnerProfileDto empty = new LearnerProfileDto();
            empty.setProfileStatus("empty");
            when(learnerProfileService.getUserProfile(1L)).thenReturn(empty);

            mockMvc.perform(get("/api/student/profile/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("GET /me — 用户不存在返回错误")
        void getMyProfile_userNotFound() throws Exception {
            when(userService.getUserByUsername("student1")).thenReturn(null);

            mockMvc.perform(get("/api/student/profile/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("用户不存在"));
        }

        @Test
        @DisplayName("GET /me/summary → 返回 LLM 摘要文本")
        void getMySummary_success() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");
            when(userService.getUserByUsername("student1")).thenReturn(user);

            LearnerProfileSummary summary = new LearnerProfileSummary();
            summary.setSummaryText("你最近7天学习了6天，词汇正在稳步增长。");
            when(summaryService.getLatestSummary(1L, "PERSONAL")).thenReturn(summary);

            mockMvc.perform(get("/api/student/profile/me/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("你最近7天学习了6天，词汇正在稳步增长。"));
        }

        @Test
        @DisplayName("GET /me/summary — summaryService 为 null 返回 null")
        void getMySummary_serviceNull() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");

            StudentProfileController ctrlNoSummary = new StudentProfileController();
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSummary, "userService", userService);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSummary, "learnerProfileService", learnerProfileService);
            MockMvc mvc2 = MockMvcBuilders.standaloneSetup(ctrlNoSummary)
                    .addFilters(TestUtils.authenticationFilter()).build();

            when(userService.getUserByUsername("student1")).thenReturn(user);

            mvc2.perform(get("/api/student/profile/me/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("GET /me/summary — 无已生成摘要返回 null")
        void getMySummary_noSummary() throws Exception {
            User user = new User();
            user.setId(1L);
            user.setUsername("student1");
            when(userService.getUserByUsername("student1")).thenReturn(user);
            when(summaryService.getLatestSummary(1L, "PERSONAL")).thenReturn(null);

            mockMvc.perform(get("/api/student/profile/me/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }
}
