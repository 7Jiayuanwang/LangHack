package com.english.controller;

import com.english.dto.Result;
import com.english.dto.learner.*;
import com.english.entity.LearnerCompositeTag;
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

class LearnerProfileIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private LearnerProfileDto buildReadyProfile() {
        LearnerProfileDto dto = new LearnerProfileDto();
        dto.setProfileStatus("ready");

        LearnerIdentityDto identity = new LearnerIdentityDto();
        identity.setRealName("张三");
        dto.setIdentity(identity);

        LearnerAbilityDto ability = new LearnerAbilityDto();
        ability.setTotalWords(100);
        ability.setMasteredWords(60);
        ability.setWeakRatio(0.4);
        dto.setAbility(ability);

        LearnerHabitDto habit = new LearnerHabitDto();
        habit.setActiveDays7d(5);
        habit.setActiveDays14d(10);
        dto.setHabit(habit);

        LearnerPreferenceDto pref = new LearnerPreferenceDto();
        pref.setPrimaryContentVersion("cet4");
        dto.setPreference(pref);

        LearnerRiskDto risk = new LearnerRiskDto();
        risk.setRiskLevel("low");
        dto.setRisk(risk);

        dto.setTags(List.of(
                new LearnerTagDto("CONSISTENT_LEARNER", "学习稳定", "positive", 40, "保持")
        ));

        LearnerEvidenceDto evidence = new LearnerEvidenceDto();
        evidence.setDataCompleteness("high");
        evidence.setDataPointCount(5);
        dto.setEvidence(evidence);

        return dto;
    }

    // ── V1: Profile ──

    @Nested
    @DisplayName("GET /{userId} — 用户画像")
    class GetUserProfileTests {

        @Test
        @DisplayName("正常用户 → 返回完整画像")
        void normalUser_returnsProfile() throws Exception {
            when(learnerProfileService.getUserProfile(1L)).thenReturn(buildReadyProfile());

            mockMvc.perform(get("/api/admin/learner-profile/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.profileStatus").value("ready"))
                    .andExpect(jsonPath("$.data.identity.realName").value("张三"))
                    .andExpect(jsonPath("$.data.ability.totalWords").value(100))
                    .andExpect(jsonPath("$.data.risk.riskLevel").value("low"))
                    .andExpect(jsonPath("$.data.tags[0].code").value("CONSISTENT_LEARNER"));
        }

        @Test
        @DisplayName("无效ID → 返回错误")
        void invalidId_returnsError() throws Exception {
            mockMvc.perform(get("/api/admin/learner-profile/0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("无效的用户ID"));
        }
    }

    @Nested
    @DisplayName("GET /class/{classId} — 班级画像汇总")
    class ClassSummaryTests {

        @Test
        @DisplayName("正常班级 → 返回学生汇总列表")
        void normalClass_returnsSummary() throws Exception {
            List<LearnerProfileClassSummaryDto> summaries = new ArrayList<>();
            LearnerProfileClassSummaryDto s = new LearnerProfileClassSummaryDto();
            s.setUserId(1L);
            s.setRiskLevel("low");
            summaries.add(s);
            when(learnerProfileService.getClassProfileSummary(10L)).thenReturn(summaries);

            mockMvc.perform(get("/api/admin/learner-profile/class/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].userId").value(1))
                    .andExpect(jsonPath("$.data[0].riskLevel").value("low"));
        }

        @Test
        @DisplayName("无效班级ID → 返回错误")
        void invalidClassId_returnsError() throws Exception {
            mockMvc.perform(get("/api/admin/learner-profile/class/-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("无效的班级ID"));
        }
    }

    // ── V1: Tag CRUD ──

    @Nested
    @DisplayName("Tag Definition CRUD")
    class TagCrudTests {

        @Test
        @DisplayName("GET /tag-definitions → 返回标签列表")
        void listTags() throws Exception {
            LearnerCompositeTag tag = new LearnerCompositeTag();
            tag.setId(1L);
            tag.setCode("VOCAB_WEAK");
            tag.setDisplayName("词汇弱势");
            when(compositeTagMapper.selectAllOrdered()).thenReturn(List.of(tag));

            mockMvc.perform(get("/api/admin/learner-profile/tag-definitions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].code").value("VOCAB_WEAK"));
        }

        @Test
        @DisplayName("POST /tag-definitions — 创建标签成功")
        void createTag_success() throws Exception {
            when(compositeTagMapper.countByCode("NEW_TAG")).thenReturn(0);
            when(compositeTagMapper.insert(any())).thenReturn(1);

            Map<String, Object> body = Map.of(
                    "code", "NEW_TAG",
                    "displayName", "新标签",
                    "tagType", "risk",
                    "ruleDefinition", "{\"conditions\":[{\"source\":\"metric\",\"field\":\"activeDays7d\",\"operator\":\"LT\",\"value\":2}],\"combiner\":\"AND\"}"
            );
            mockMvc.perform(post("/api/admin/learner-profile/tag-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.code").value("NEW_TAG"));
        }

        @Test
        @DisplayName("POST /tag-definitions — 编码重复返回错误")
        void createTag_duplicateCode() throws Exception {
            when(compositeTagMapper.countByCode("EXISTING")).thenReturn(1);

            Map<String, Object> body = Map.of(
                    "code", "EXISTING",
                    "displayName", "已存在",
                    "ruleDefinition", "{\"conditions\":[]}"
            );
            mockMvc.perform(post("/api/admin/learner-profile/tag-definitions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500));
        }

        @Test
        @DisplayName("DELETE /tag-definitions/{id} — 删除成功")
        void deleteTag_success() throws Exception {
            LearnerCompositeTag existing = new LearnerCompositeTag();
            existing.setId(5L);
            when(compositeTagMapper.selectById(5L)).thenReturn(existing);

            mockMvc.perform(delete("/api/admin/learner-profile/tag-definitions/5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            verify(compositeTagMapper).deleteById(5L);
        }

        @Test
        @DisplayName("PATCH /tag-definitions/{id}/enabled — 切换启用")
        void toggleTag_success() throws Exception {
            LearnerCompositeTag existing = new LearnerCompositeTag();
            existing.setId(3L);
            existing.setEnabled(true);
            when(compositeTagMapper.selectById(3L)).thenReturn(existing);

            mockMvc.perform(patch("/api/admin/learner-profile/tag-definitions/3/enabled")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
            verify(compositeTagMapper).updateById(any());
        }

        @Test
        @DisplayName("GET /tag-definitions/metrics → 返回16个指标")
        void listMetrics() throws Exception {
            mockMvc.perform(get("/api/admin/learner-profile/tag-definitions/metrics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.activeDays7d").value("7天活跃天数"))
                    .andExpect(jsonPath("$.data.weakRatio").value("词汇弱势率"));
        }
    }
}
