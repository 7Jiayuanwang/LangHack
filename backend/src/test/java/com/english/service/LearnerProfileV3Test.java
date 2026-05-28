package com.english.service;

import com.english.dto.learner.*;
import com.english.entity.LearnerCompositeTag;
import com.english.entity.LearnerProfileSnapshot;
import com.english.entity.LearnerProfileSummary;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.*;
import com.english.service.impl.ClassCohortAnalysisServiceImpl;
import com.english.service.impl.CompositeTagEngineImpl;
import com.english.service.impl.LearnerProfileServiceImpl;
import com.english.service.impl.LearnerProfileSummaryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearnerProfileV3Test {

    // ── Trend Tags Tests ──

    @Nested
    @DisplayName("Trend Tags (T-038)")
    class TrendTagTests {

        @Mock private LearningRecordsService learningRecordsService;
        @Mock private VocabularyBooksService vocabularyBooksService;
        @Mock private TextLearningRecordMapper textLearningRecordMapper;
        @Mock private UserService userService;
        @Mock private SchoolStudentMapper schoolStudentMapper;
        @Mock private StudentClassMapper studentClassMapper;
        @Mock private LearnerProfileSnapshotService snapshotService;
        @Mock private CompositeTagEngine compositeTagEngine;
        @InjectMocks private LearnerProfileServiceImpl service;

        private void enableProfile() {
            ReflectionTestUtils.setField(service, "profileEnabled", true);
        }

        private void setupBaseStudent(Long userId) {
            User user = new User();
            user.setId(userId);
            user.setUsername("student1");
            user.setRole("STUDENT");
            when(userService.getUserById(userId)).thenReturn(user);

            Map<String, Object> vocabStats = new HashMap<>();
            vocabStats.put("totalWords", 100L);
            vocabStats.put("newWords", 10L);
            vocabStats.put("learningWords", 30L);
            vocabStats.put("masteredWords", 60L);
            when(vocabularyBooksService.getWordStudyStats(eq(userId), isNull())).thenReturn(vocabStats);

            Map<String, Object> reviewStats = new HashMap<>();
            reviewStats.put("overdueCount", 1);
            reviewStats.put("dueCount", 20);
            reviewStats.put("overdueRatio", 0.05);
            when(vocabularyBooksService.getReviewStats(userId)).thenReturn(reviewStats);

            when(learningRecordsService.countActiveDays(eq(userId), eq(7))).thenReturn(5);
            when(learningRecordsService.countActiveDays(eq(userId), eq(14))).thenReturn(10);
            when(learningRecordsService.getConsecutiveDays(userId)).thenReturn(3);
            when(learningRecordsService.getStudyMinutesInRange(eq(userId), any(), any())).thenReturn(120L);
            when(learningRecordsService.getTodayStudyTime(userId)).thenReturn(30);
            when(textLearningRecordMapper.selectVersionDistribution(userId)).thenReturn(Collections.emptyList());
            when(textLearningRecordMapper.countDistinctTexts(userId)).thenReturn(15L);
            when(textLearningRecordMapper.countDistinctCompletedTexts(userId)).thenReturn(10L);
        }

        private LearnerProfileSnapshot makeSnapshot(int daysAgo, int active14d, int active7d, double overdueRatio) {
            LearnerProfileSnapshot s = new LearnerProfileSnapshot();
            s.setSnapshotDate(LocalDate.now().minusDays(daysAgo));
            s.setActiveDays14d(active14d);
            s.setActiveDays7d(active7d);
            s.setOverdueReviewRatio(overdueRatio);
            return s;
        }

        @Test
        @DisplayName("IMPROVING_TREND: 30% improvement → metrics map has trendRatio >= 1.3")
        void improvingTrend_triggered() {
            enableProfile();
            setupBaseStudent(1L);
            List<LearnerProfileSnapshot> snapshots = Arrays.asList(
                    makeSnapshot(0, 10, 5, 0.1),
                    makeSnapshot(14, 7, 4, 0.2)
            );
            when(snapshotService.getSnapshotHistory(eq(1L), eq(28))).thenReturn(snapshots);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = inv.getArgument(0);
                double trendRatio = metrics.getOrDefault("trendRatio", 0.0);
                List<LearnerTagDto> tags = new ArrayList<>();
                if (trendRatio >= 1.3) {
                    tags.add(new LearnerTagDto("IMPROVING_TREND", "进步趋势", "positive", 70, ""));
                }
                return tags;
            });

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "IMPROVING_TREND".equals(t.getCode())),
                    "Should have IMPROVING_TREND tag when activeDays14d increased by ≥30%");
        }

        @Test
        @DisplayName("IMPROVING_TREND: <30% improvement does NOT trigger")
        void improvingTrend_notTriggered() {
            enableProfile();
            setupBaseStudent(1L);
            List<LearnerProfileSnapshot> snapshots = Arrays.asList(
                    makeSnapshot(0, 9, 5, 0.1),
                    makeSnapshot(14, 8, 4, 0.2)
            );
            when(snapshotService.getSnapshotHistory(eq(1L), eq(28))).thenReturn(snapshots);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = inv.getArgument(0);
                double trendRatio = metrics.getOrDefault("trendRatio", 0.0);
                List<LearnerTagDto> tags = new ArrayList<>();
                if (trendRatio >= 1.3) {
                    tags.add(new LearnerTagDto("IMPROVING_TREND", "进步趋势", "positive", 70, ""));
                }
                return tags;
            });

            LearnerProfileDto result = service.getUserProfile(1L);
            assertFalse(result.getTags().stream().anyMatch(t -> "IMPROVING_TREND".equals(t.getCode())));
        }

        @Test
        @DisplayName("DECLINING_TREND: 40% decline → metrics map has trendRatio <= 0.6")
        void decliningTrend_triggered() {
            enableProfile();
            setupBaseStudent(1L);
            List<LearnerProfileSnapshot> snapshots = Arrays.asList(
                    makeSnapshot(0, 5, 2, 0.1),
                    makeSnapshot(14, 10, 5, 0.1)
            );
            when(snapshotService.getSnapshotHistory(eq(1L), eq(28))).thenReturn(snapshots);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = inv.getArgument(0);
                double trendRatio = metrics.getOrDefault("trendRatio", 1.0);
                double oldActive = metrics.getOrDefault("oldActiveDays14d", 0.0);
                List<LearnerTagDto> tags = new ArrayList<>();
                if (oldActive > 0 && trendRatio <= 0.6) {
                    tags.add(new LearnerTagDto("DECLINING_TREND", "下滑趋势", "risk", 80, ""));
                }
                return tags;
            });

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "DECLINING_TREND".equals(t.getCode())),
                    "Should have DECLINING_TREND tag when activeDays14d dropped by ≥40%");
        }

        @Test
        @DisplayName("REVIEW_CHAMPION: 3 snapshots with <5% overdue → snap metrics correct")
        void reviewChampion_triggered() {
            enableProfile();
            setupBaseStudent(1L);
            List<LearnerProfileSnapshot> snapshots = Arrays.asList(
                    makeSnapshot(0, 10, 5, 0.02),
                    makeSnapshot(7, 9, 5, 0.03),
                    makeSnapshot(14, 8, 5, 0.04)
            );
            when(snapshotService.getSnapshotHistory(eq(1L), eq(28))).thenReturn(snapshots);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = inv.getArgument(0);
                List<LearnerTagDto> tags = new ArrayList<>();
                Double s0 = metrics.get("snap0OverdueRatio");
                Double s1 = metrics.get("snap1OverdueRatio");
                Double s2 = metrics.get("snap2OverdueRatio");
                if (s0 != null && s1 != null && s2 != null && s0 < 0.05 && s1 < 0.05 && s2 < 0.05) {
                    tags.add(new LearnerTagDto("REVIEW_CHAMPION", "复习达人", "positive", 90, ""));
                }
                return tags;
            });

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "REVIEW_CHAMPION".equals(t.getCode())),
                    "Should have REVIEW_CHAMPION tag with 3 consecutive low-overdue snapshots");
        }

        @Test
        @DisplayName("REVIEW_CHAMPION: only 2 snapshots → NOT triggered")
        void reviewChampion_notEnoughSnapshots() {
            enableProfile();
            setupBaseStudent(1L);
            List<LearnerProfileSnapshot> snapshots = Arrays.asList(
                    makeSnapshot(0, 10, 5, 0.02),
                    makeSnapshot(7, 9, 5, 0.03)
            );
            when(snapshotService.getSnapshotHistory(eq(1L), eq(28))).thenReturn(snapshots);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = inv.getArgument(0);
                List<LearnerTagDto> tags = new ArrayList<>();
                Double s0 = metrics.get("snap0OverdueRatio");
                Double s1 = metrics.get("snap1OverdueRatio");
                Double s2 = metrics.get("snap2OverdueRatio");
                if (s0 != null && s1 != null && s2 != null && s0 < 0.05 && s1 < 0.05 && s2 < 0.05) {
                    tags.add(new LearnerTagDto("REVIEW_CHAMPION", "复习达人", "positive", 90, ""));
                }
                return tags;
            });

            LearnerProfileDto result = service.getUserProfile(1L);
            assertFalse(result.getTags().stream().anyMatch(t -> "REVIEW_CHAMPION".equals(t.getCode())));
        }

        @Test
        @DisplayName("no snapshot service → trend tags skipped gracefully")
        void noSnapshotService_graceful() {
            enableProfile();
            setupBaseStudent(1L);
            ReflectionTestUtils.setField(service, "snapshotService", null);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(new ArrayList<>());

            LearnerProfileDto result = service.getUserProfile(1L);
            assertNotNull(result);
            assertFalse(result.getTags().stream().anyMatch(
                    t -> "IMPROVING_TREND".equals(t.getCode()) || "DECLINING_TREND".equals(t.getCode())));
        }
    }

    // ── Composite Tags Tests ──

    @Nested
    @DisplayName("Composite Tag Engine (T-039)")
    class CompositeTagEngineTests {

        @Mock private LearnerCompositeTagMapper compositeTagMapper;
        @InjectMocks private CompositeTagEngineImpl engine;

        @Test
        @DisplayName("AND combiner: all conditions match → tag produced")
        void andCombiner_allMatch() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("STRUGGLING_STUDENT");
            def.setDisplayName("挣扎中的学生");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("需要综合干预");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"tag\",\"code\":\"VOCAB_WEAK\",\"operator\":\"MATCHED\"},{\"source\":\"tag\",\"code\":\"INACTIVE_RISK\",\"operator\":\"MATCHED\"}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Set<String> baseTags = new HashSet<>(Arrays.asList("VOCAB_WEAK", "INACTIVE_RISK", "REVIEW_OVERDUE"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertEquals(1, result.size());
            assertEquals("STRUGGLING_STUDENT", result.get(0).getCode());
        }

        @Test
        @DisplayName("AND combiner: one condition missing → no tag")
        void andCombiner_partialMatch() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("STRUGGLING_STUDENT");
            def.setDisplayName("挣扎中的学生");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("需要综合干预");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"tag\",\"code\":\"VOCAB_WEAK\",\"operator\":\"MATCHED\"},{\"source\":\"tag\",\"code\":\"INACTIVE_RISK\",\"operator\":\"MATCHED\"}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Set<String> baseTags = new HashSet<>(Collections.singletonList("VOCAB_WEAK"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("OR combiner: one condition matches → tag produced")
        void orCombiner_oneMatch() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("AT_RISK");
            def.setDisplayName("有风险");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("关注");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"tag\",\"code\":\"VOCAB_WEAK\",\"operator\":\"MATCHED\"},{\"source\":\"tag\",\"code\":\"INACTIVE_RISK\",\"operator\":\"MATCHED\"}],\"combiner\":\"OR\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Set<String> baseTags = new HashSet<>(Collections.singletonList("VOCAB_WEAK"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("NOT_MATCHED operator: tag absent → condition true")
        void notMatchedOperator() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("GOOD_BUT_INACTIVE");
            def.setDisplayName("能力好但不活跃");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("鼓励参与");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"tag\",\"code\":\"VOCAB_WEAK\",\"operator\":\"NOT_MATCHED\"},{\"source\":\"tag\",\"code\":\"INACTIVE_RISK\",\"operator\":\"MATCHED\"}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Set<String> baseTags = new HashSet<>(Collections.singletonList("INACTIVE_RISK"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertEquals(1, result.size());
            assertEquals("GOOD_BUT_INACTIVE", result.get(0).getCode());
        }

        @Test
        @DisplayName("no enabled definitions → empty result")
        void noDefinitions_empty() {
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.emptyList());

            Set<String> baseTags = new HashSet<>(Arrays.asList("VOCAB_WEAK", "INACTIVE_RISK"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("invalid JSON rule → skipped gracefully")
        void invalidJson_skipped() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("BAD_RULE");
            def.setDisplayName("无效规则");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("test");
            def.setRuleDefinition("not valid json");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Set<String> baseTags = new HashSet<>(Collections.singletonList("VOCAB_WEAK"));
            List<LearnerTagDto> result = engine.evaluateCompositeTags(baseTags);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("metric condition: LT threshold → tag triggered")
        void metricCondition_LT_triggered() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("INACTIVE_RISK");
            def.setDisplayName("流失风险");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("提醒");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"metric\",\"field\":\"activeDays14d\",\"operator\":\"LT\",\"value\":3}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Map<String, Double> metrics = new HashMap<>();
            metrics.put("activeDays14d", 2.0);
            List<LearnerTagDto> result = engine.evaluateAll(metrics);

            assertEquals(1, result.size());
            assertEquals("INACTIVE_RISK", result.get(0).getCode());
        }

        @Test
        @DisplayName("metric condition: value at threshold → LT not triggered")
        void metricCondition_LT_boundary_notTriggered() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("INACTIVE_RISK");
            def.setDisplayName("流失风险");
            def.setTagType("risk");
            def.setPriority(10);
            def.setActionHint("提醒");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"metric\",\"field\":\"activeDays14d\",\"operator\":\"LT\",\"value\":3}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Map<String, Double> metrics = new HashMap<>();
            metrics.put("activeDays14d", 3.0);
            List<LearnerTagDto> result = engine.evaluateAll(metrics);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("metric AND conditions: both met → tag triggered")
        void metricCondition_AND_allMet() {
            LearnerCompositeTag def = new LearnerCompositeTag();
            def.setCode("VOCAB_WEAK");
            def.setDisplayName("词汇弱势");
            def.setTagType("risk");
            def.setPriority(20);
            def.setActionHint("复习");
            def.setRuleDefinition("{\"conditions\":[{\"source\":\"metric\",\"field\":\"totalWords\",\"operator\":\"GT\",\"value\":0},{\"source\":\"metric\",\"field\":\"weakRatio\",\"operator\":\"GTE\",\"value\":0.6}],\"combiner\":\"AND\"}");
            when(compositeTagMapper.selectAllEnabled()).thenReturn(Collections.singletonList(def));

            Map<String, Double> metrics = new HashMap<>();
            metrics.put("totalWords", 100.0);
            metrics.put("weakRatio", 0.7);
            List<LearnerTagDto> result = engine.evaluateAll(metrics);

            assertEquals(1, result.size());
            assertEquals("VOCAB_WEAK", result.get(0).getCode());
        }

        @Test
        @DisplayName("two-pass evaluation: composite tag references base tag")
        void twoPassEvaluation() {
            LearnerCompositeTag base = new LearnerCompositeTag();
            base.setCode("INACTIVE_RISK");
            base.setDisplayName("流失风险");
            base.setTagType("risk");
            base.setPriority(10);
            base.setActionHint("提醒");
            base.setRuleDefinition("{\"conditions\":[{\"source\":\"metric\",\"field\":\"activeDays14d\",\"operator\":\"LT\",\"value\":3}],\"combiner\":\"AND\"}");

            LearnerCompositeTag composite = new LearnerCompositeTag();
            composite.setCode("CRITICAL_ALERT");
            composite.setDisplayName("严重警告");
            composite.setTagType("risk");
            composite.setPriority(5);
            composite.setActionHint("立即干预");
            composite.setRuleDefinition("{\"conditions\":[{\"source\":\"tag\",\"code\":\"INACTIVE_RISK\",\"operator\":\"MATCHED\"},{\"source\":\"metric\",\"field\":\"weakRatio\",\"operator\":\"GTE\",\"value\":0.8}],\"combiner\":\"AND\"}");

            when(compositeTagMapper.selectAllEnabled()).thenReturn(Arrays.asList(base, composite));

            Map<String, Double> metrics = new HashMap<>();
            metrics.put("activeDays14d", 1.0);
            metrics.put("weakRatio", 0.9);
            List<LearnerTagDto> result = engine.evaluateAll(metrics);

            assertEquals(2, result.size());
            assertTrue(result.stream().anyMatch(t -> "INACTIVE_RISK".equals(t.getCode())));
            assertTrue(result.stream().anyMatch(t -> "CRITICAL_ALERT".equals(t.getCode())));
        }
    }

    // ── Cohort Analysis Tests ──

    @Nested
    @DisplayName("Class Cohort Analysis (T-040)")
    class CohortAnalysisTests {

        @Mock private LearnerProfileService learnerProfileService;
        @Mock private StudentClassMapper studentClassMapper;
        @Mock private ClassMapper classMapper;
        @InjectMocks private ClassCohortAnalysisServiceImpl cohortService;

        private LearnerProfileDto makeProfile(String riskLevel, int active7d, long studyMinutes, double weakRatio,
                                               String... tagCodes) {
            LearnerProfileDto p = new LearnerProfileDto();
            p.setProfileStatus("ready");

            LearnerIdentityDto identity = new LearnerIdentityDto();
            identity.setUsername("user");
            identity.setRealName("测试");
            p.setIdentity(identity);

            LearnerRiskDto risk = new LearnerRiskDto();
            risk.setRiskLevel(riskLevel);
            p.setRisk(risk);

            LearnerHabitDto habit = new LearnerHabitDto();
            habit.setActiveDays7d(active7d);
            habit.setStudyMinutes14d(studyMinutes);
            p.setHabit(habit);

            LearnerAbilityDto ability = new LearnerAbilityDto();
            ability.setWeakRatio(weakRatio);
            p.setAbility(ability);

            List<LearnerTagDto> tags = new ArrayList<>();
            for (String code : tagCodes) {
                tags.add(new LearnerTagDto(code, code, "risk", 1, "hint"));
            }
            p.setTags(tags);
            return p;
        }

        @Test
        @DisplayName("cohort analysis: risk distribution computed correctly")
        void riskDistribution() {
            Long classId = 1L;
            com.english.entity.Class cls = new com.english.entity.Class();
            cls.setName("测试班级");
            when(classMapper.selectById(classId)).thenReturn(cls);

            StudentClass sc1 = new StudentClass(); sc1.setStudentId(1L);
            StudentClass sc2 = new StudentClass(); sc2.setStudentId(2L);
            StudentClass sc3 = new StudentClass(); sc3.setStudentId(3L);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Arrays.asList(sc1, sc2, sc3));

            when(learnerProfileService.getUserProfile(1L)).thenReturn(makeProfile("HIGH", 1, 30, 0.8, "INACTIVE_RISK"));
            when(learnerProfileService.getUserProfile(2L)).thenReturn(makeProfile("MEDIUM", 3, 60, 0.6, "VOCAB_WEAK"));
            when(learnerProfileService.getUserProfile(3L)).thenReturn(makeProfile("LOW", 6, 150, 0.3, "CONSISTENT_LEARNER"));

            ClassCohortAnalysisDto result = cohortService.analyzeCohort(classId);

            assertEquals(3, result.getTotalStudents());
            assertEquals("测试班级", result.getClassName());
            assertEquals(1, result.getRiskDistribution().get("HIGH"));
            assertEquals(1, result.getRiskDistribution().get("MEDIUM"));
            assertEquals(1, result.getRiskDistribution().get("LOW"));
        }

        @Test
        @DisplayName("cohort analysis: tag hit distribution")
        void tagHitDistribution() {
            Long classId = 1L;
            when(classMapper.selectById(classId)).thenReturn(new com.english.entity.Class());
            StudentClass sc1 = new StudentClass(); sc1.setStudentId(1L);
            StudentClass sc2 = new StudentClass(); sc2.setStudentId(2L);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Arrays.asList(sc1, sc2));

            when(learnerProfileService.getUserProfile(1L)).thenReturn(makeProfile("HIGH", 1, 30, 0.8, "VOCAB_WEAK", "INACTIVE_RISK"));
            when(learnerProfileService.getUserProfile(2L)).thenReturn(makeProfile("MEDIUM", 3, 60, 0.7, "VOCAB_WEAK"));

            ClassCohortAnalysisDto result = cohortService.analyzeCohort(classId);

            assertEquals(2, result.getTagHitDistribution().get("VOCAB_WEAK"));
            assertEquals(1, result.getTagHitDistribution().get("INACTIVE_RISK"));
        }

        @Test
        @DisplayName("cohort analysis: subgroups generated for high-risk students")
        void subgroupsGenerated() {
            Long classId = 1L;
            when(classMapper.selectById(classId)).thenReturn(new com.english.entity.Class());
            StudentClass sc1 = new StudentClass(); sc1.setStudentId(1L);
            StudentClass sc2 = new StudentClass(); sc2.setStudentId(2L);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Arrays.asList(sc1, sc2));

            when(learnerProfileService.getUserProfile(1L)).thenReturn(makeProfile("HIGH", 1, 30, 0.8, "VOCAB_WEAK", "INACTIVE_RISK"));
            when(learnerProfileService.getUserProfile(2L)).thenReturn(makeProfile("LOW", 6, 150, 0.3, "CONSISTENT_LEARNER"));

            ClassCohortAnalysisDto result = cohortService.analyzeCohort(classId);

            assertFalse(result.getSubgroups().isEmpty());
            assertTrue(result.getSubgroups().stream().anyMatch(g -> "高风险学生".equals(g.getSubgroupName())));
        }

        @Test
        @DisplayName("cohort analysis: empty class returns zero counts")
        void emptyClass() {
            Long classId = 99L;
            when(classMapper.selectById(classId)).thenReturn(null);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Collections.emptyList());

            ClassCohortAnalysisDto result = cohortService.analyzeCohort(classId);

            assertEquals(0, result.getTotalStudents());
            assertTrue(result.getSubgroups().isEmpty());
        }

        @Test
        @DisplayName("compareClasses: returns one analysis per class")
        void compareClasses() {
            when(classMapper.selectById(anyLong())).thenReturn(new com.english.entity.Class());
            when(studentClassMapper.selectStudentsByClassId(anyLong())).thenReturn(Collections.emptyList());

            List<ClassCohortAnalysisDto> results = cohortService.compareClasses(Arrays.asList(1L, 2L, 3L));
            assertEquals(3, results.size());
        }

        @Test
        @DisplayName("cohort analysis: averages computed correctly")
        void averagesComputed() {
            Long classId = 1L;
            when(classMapper.selectById(classId)).thenReturn(new com.english.entity.Class());
            StudentClass sc1 = new StudentClass(); sc1.setStudentId(1L);
            StudentClass sc2 = new StudentClass(); sc2.setStudentId(2L);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Arrays.asList(sc1, sc2));

            when(learnerProfileService.getUserProfile(1L)).thenReturn(makeProfile("LOW", 4, 100, 0.4));
            when(learnerProfileService.getUserProfile(2L)).thenReturn(makeProfile("LOW", 6, 200, 0.2));

            ClassCohortAnalysisDto result = cohortService.analyzeCohort(classId);

            assertEquals(5.0, result.getAvgActiveDays7d(), 0.01);
            assertEquals(150.0, result.getAvgStudyMinutes14d(), 0.01);
            assertEquals(0.3, result.getAvgWeakRatio(), 0.01);
        }
    }
}
