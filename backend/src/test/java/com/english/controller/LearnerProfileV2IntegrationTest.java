package com.english.controller;

import com.english.entity.LearnerCompositeTag;
import com.english.entity.LearnerProfileSnapshot;
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

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LearnerProfileV2IntegrationTest {

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

    // ── Snapshot History ──

    @Nested
    @DisplayName("GET /{userId}/snapshots — 快照历史")
    class SnapshotHistoryTests {

        @Test
        @DisplayName("返回快照列表")
        void returnsSnapshots() throws Exception {
            LearnerProfileSnapshot snap = new LearnerProfileSnapshot();
            snap.setId(1L);
            snap.setUserId(1L);
            snap.setRiskLevel("low");
            snap.setActiveDays7d(5);
            when(snapshotService.getSnapshotHistory(1L, 30)).thenReturn(List.of(snap));

            mockMvc.perform(get("/api/admin/learner-profile/1/snapshots?lastNDays=30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].riskLevel").value("low"))
                    .andExpect(jsonPath("$.data[0].activeDays7d").value(5));
        }

        @Test
        @DisplayName("snapshotService 为 null → 功能未启用")
        void serviceNull_returnsError() throws Exception {
            LearnerProfileController ctrlNoSnap = new LearnerProfileController();
            // snapshotService field is null by default
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSnap, "compositeTagMapper", compositeTagMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSnap, "snapshotMapper", snapshotMapper);
            org.springframework.test.util.ReflectionTestUtils.setField(ctrlNoSnap, "learnerProfileService", learnerProfileService);
            MockMvc mvc2 = MockMvcBuilders.standaloneSetup(ctrlNoSnap)
                    .addFilters(TestUtils.authenticationFilter()).build();

            mvc2.perform(get("/api/admin/learner-profile/1/snapshots"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(500))
                    .andExpect(jsonPath("$.message").value("快照功能未启用"));
        }
    }

    // ── Tag Effectiveness & Calibration ──

    @Nested
    @DisplayName("Tag Effectiveness & Recalibration")
    class TagCalibrationTests {

        @Test
        @DisplayName("GET /tags/effectiveness → 返回标签效果数据")
        void getTagEffectiveness() throws Exception {
            Map<String, Object> row = new HashMap<>();
            row.put("personaTags", "VOCAB_WEAK");
            row.put("impressions", 100L);
            row.put("clicks", 15L);
            row.put("ctr", 0.15);
            when(recalibrationService.getTagEffectiveness(30)).thenReturn(List.of(row));

            mockMvc.perform(get("/api/admin/learner-profile/tags/effectiveness?lastNDays=30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].personaTags").value("VOCAB_WEAK"))
                    .andExpect(jsonPath("$.data[0].ctr").value(0.15));
        }

        @Test
        @DisplayName("GET /tags/proposals → 返回校准建议")
        void getProposals() throws Exception {
            Map<String, Object> proposal = new HashMap<>();
            proposal.put("tagCode", "VOCAB_WEAK");
            proposal.put("hitRate", 0.9);
            proposal.put("proposal", "tighten");
            proposal.put("reason", "命中率过高(90.0%), 建议收紧阈值");
            when(recalibrationService.getRecalibrationProposals()).thenReturn(List.of(proposal));

            mockMvc.perform(get("/api/admin/learner-profile/tags/proposals"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].proposal").value("tighten"))
                    .andExpect(jsonPath("$.data[0].tagCode").value("VOCAB_WEAK"));
        }

        @Test
        @DisplayName("POST /tags/recalibrate → 创建校准日志")
        void recalibrate() throws Exception {
            com.english.entity.TagRecalibrationLog log = new com.english.entity.TagRecalibrationLog();
            log.setId(1L);
            log.setTagCode("VOCAB_WEAK");
            log.setNewThreshold("0.5");
            when(recalibrationService.recalibrate(eq("VOCAB_WEAK"), eq("0.5"), eq("太宽松"), eq("admin")))
                    .thenReturn(log);

            mockMvc.perform(post("/api/admin/learner-profile/tags/recalibrate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"tagCode\":\"VOCAB_WEAK\",\"newThreshold\":\"0.5\",\"reason\":\"太宽松\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.tagCode").value("VOCAB_WEAK"));
        }
    }

    // ── Analytics ──

    @Nested
    @DisplayName("Analytics 端点")
    class AnalyticsTests {

        @Test
        @DisplayName("GET /analytics/overview → 聚合分析概览")
        void overview() throws Exception {
            when(trackingService.getClickThroughRateBySource(30)).thenReturn(List.of());
            when(recalibrationService.getTagEffectiveness(30)).thenReturn(List.of());
            when(recalibrationService.getRecalibrationProposals()).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/learner-profile/analytics/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.ctrBySource").isArray())
                    .andExpect(jsonPath("$.data.tagEffectiveness").isArray());
        }

        @Test
        @DisplayName("GET /analytics/recommendation-effectiveness → CTR + persona 数据")
        void recommendationEffectiveness() throws Exception {
            Map<String, Object> ctr = Map.of("source", "RECOMMEND", "impressions", 100, "clicks", 10);
            when(trackingService.getClickThroughRateBySource(7)).thenReturn(List.of(ctr));
            when(trackingService.getPersonaEffectivenessMetrics(7)).thenReturn(List.of());

            mockMvc.perform(get("/api/admin/learner-profile/analytics/recommendation-effectiveness?lastNDays=7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ctrBySource[0].source").value("RECOMMEND"))
                    .andExpect(jsonPath("$.data.personaMetrics").isArray());
        }

        @Test
        @DisplayName("GET /analytics/trends → 日趋势 + 标签命中")
        void trends() throws Exception {
            Map<String, Object> dayRow = new HashMap<>();
            dayRow.put("snapshotDate", Date.valueOf("2026-05-27"));
            dayRow.put("totalUsers", 50L);
            dayRow.put("riskHigh", 5L);
            dayRow.put("riskMedium", 15L);
            dayRow.put("riskLow", 30L);
            dayRow.put("avgActiveDays7d", BigDecimal.valueOf(3.5));
            dayRow.put("avgStudyMinutes14d", BigDecimal.valueOf(120));
            dayRow.put("avgOverdueReviewRatio", BigDecimal.valueOf(0.15));
            when(snapshotMapper.selectDailyAggregation(any())).thenReturn(List.of(dayRow));
            when(snapshotMapper.selectTagCodesSince(any())).thenReturn(List.of("VOCAB_WEAK,INACTIVE_RISK", "VOCAB_WEAK"));

            mockMvc.perform(get("/api/admin/learner-profile/analytics/trends?lastNDays=30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dailyPoints[0].totalUsers").value(50))
                    .andExpect(jsonPath("$.data.dailyPoints[0].riskHigh").value(5))
                    .andExpect(jsonPath("$.data.dailyPoints[0].avgActiveDays7d").value(3.5))
                    .andExpect(jsonPath("$.data.tagHitCounts.VOCAB_WEAK").value(2))
                    .andExpect(jsonPath("$.data.tagHitCounts.INACTIVE_RISK").value(1))
                    .andExpect(jsonPath("$.data.totalSnapshotsWithTags").value(2));
        }

        @Test
        @DisplayName("GET /analytics/trends — 无数据 → 空列表")
        void trends_noData() throws Exception {
            when(snapshotMapper.selectDailyAggregation(any())).thenReturn(Collections.emptyList());
            when(snapshotMapper.selectTagCodesSince(any())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/admin/learner-profile/analytics/trends"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.dailyPoints").isEmpty())
                    .andExpect(jsonPath("$.data.tagHitCounts").isEmpty());
        }
    }
}
