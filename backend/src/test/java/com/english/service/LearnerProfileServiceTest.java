package com.english.service;

import com.english.dto.learner.*;
import com.english.entity.SchoolStudent;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.mapper.SchoolStudentMapper;
import com.english.mapper.StudentClassMapper;
import com.english.mapper.TextLearningRecordMapper;
import com.english.service.impl.LearnerProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearnerProfileServiceTest {

    @Mock
    private LearningRecordsService learningRecordsService;

    @Mock
    private VocabularyBooksService vocabularyBooksService;

    @Mock
    private TextLearningRecordMapper textLearningRecordMapper;

    @Mock
    private UserService userService;

    @Mock
    private SchoolStudentMapper schoolStudentMapper;

    @Mock
    private StudentClassMapper studentClassMapper;

    @Mock
    private CompositeTagEngine compositeTagEngine;

    @InjectMocks
    private LearnerProfileServiceImpl service;

    @BeforeEach
    void defaultSetup() {
        lenient().when(compositeTagEngine.evaluateAll(any())).thenReturn(new ArrayList<>());
    }

    private void enableProfile() {
        ReflectionTestUtils.setField(service, "profileEnabled", true);
    }

    private void disableProfile() {
        ReflectionTestUtils.setField(service, "profileEnabled", false);
    }

    private void setupActiveStudent(Long userId) {
        User user = new User();
        user.setId(userId);
        user.setUsername("student1");
        user.setRealName("张三");
        user.setRole("STUDENT");
        when(userService.getUserById(userId)).thenReturn(user);

        Map<String, Object> vocabStats = new HashMap<>();
        vocabStats.put("totalWords", 100L);
        vocabStats.put("newWords", 10L);
        vocabStats.put("learningWords", 30L);
        vocabStats.put("masteredWords", 60L);
        when(vocabularyBooksService.getWordStudyStats(eq(userId), isNull())).thenReturn(vocabStats);

        Map<String, Object> reviewStats = new HashMap<>();
        reviewStats.put("overdueCount", 5);
        reviewStats.put("dueCount", 20);
        reviewStats.put("overdueRatio", 0.25);
        when(vocabularyBooksService.getReviewStats(userId)).thenReturn(reviewStats);

        when(learningRecordsService.countActiveDays(eq(userId), eq(7))).thenReturn(5);
        when(learningRecordsService.countActiveDays(eq(userId), eq(14))).thenReturn(10);
        when(learningRecordsService.getConsecutiveDays(userId)).thenReturn(3);
        when(learningRecordsService.getStudyMinutesInRange(eq(userId), any(), any())).thenReturn(120L);
        when(learningRecordsService.getTodayStudyTime(userId)).thenReturn(30);

        List<Map<String, Object>> versionDist = new ArrayList<>();
        Map<String, Object> cet4 = new HashMap<>();
        cet4.put("content_version", "cet4");
        cet4.put("total_seconds", 3600L);
        versionDist.add(cet4);
        Map<String, Object> cet6 = new HashMap<>();
        cet6.put("content_version", "cet6");
        cet6.put("total_seconds", 1800L);
        versionDist.add(cet6);
        when(textLearningRecordMapper.selectVersionDistribution(userId)).thenReturn(versionDist);
        when(textLearningRecordMapper.selectPrimaryVersion(userId)).thenReturn("cet4");
        when(textLearningRecordMapper.countDistinctTexts(userId)).thenReturn(15L);
        when(textLearningRecordMapper.countDistinctCompletedTexts(userId)).thenReturn(10L);
    }

    @Nested
    @DisplayName("Feature Toggle")
    class FeatureToggleTests {

        @Test
        @DisplayName("disabled → returns EMPTY profile")
        void disabled_returnsEmpty() {
            disableProfile();
            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("empty", result.getProfileStatus());
            assertNull(result.getIdentity());
        }

        @Test
        @DisplayName("disabled → getClassProfileSummary returns empty list")
        void disabled_classReturnsEmpty() {
            disableProfile();
            List<LearnerProfileClassSummaryDto> result = service.getClassProfileSummary(1L);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getUserProfile - Normal Flow")
    class GetUserProfileTests {

        @Test
        @DisplayName("active student → READY status with all sections populated")
        void activeStudent_returnsReadyProfile() {
            enableProfile();
            setupActiveStudent(1L);

            LearnerProfileDto result = service.getUserProfile(1L);

            assertEquals("ready", result.getProfileStatus());
            assertNotNull(result.getIdentity());
            assertEquals("张三", result.getIdentity().getRealName());
            assertNotNull(result.getAbility());
            assertEquals(100, result.getAbility().getTotalWords());
            assertEquals(60, result.getAbility().getMasteredWords());
            assertEquals(0.4, result.getAbility().getWeakRatio(), 0.01);
            assertNotNull(result.getHabit());
            assertEquals(5, result.getHabit().getActiveDays7d());
            assertEquals(10, result.getHabit().getActiveDays14d());
            assertNotNull(result.getPreference());
            assertEquals("cet4", result.getPreference().getPrimaryContentVersion());
            assertNotNull(result.getRisk());
            assertNotNull(result.getTags());
            assertNotNull(result.getEvidence());
        }

        @Test
        @DisplayName("user with no data → EMPTY status")
        void noDataUser_returnsEmpty() {
            enableProfile();
            when(userService.getUserById(99L)).thenReturn(null);

            Map<String, Object> emptyVocab = new HashMap<>();
            emptyVocab.put("totalWords", 0L);
            emptyVocab.put("newWords", 0L);
            emptyVocab.put("learningWords", 0L);
            emptyVocab.put("masteredWords", 0L);
            when(vocabularyBooksService.getWordStudyStats(eq(99L), isNull())).thenReturn(emptyVocab);

            Map<String, Object> emptyReview = new HashMap<>();
            emptyReview.put("overdueCount", 0);
            emptyReview.put("dueCount", 0);
            emptyReview.put("overdueRatio", 0.0);
            when(vocabularyBooksService.getReviewStats(99L)).thenReturn(emptyReview);

            when(learningRecordsService.countActiveDays(eq(99L), anyInt())).thenReturn(0);
            when(learningRecordsService.getConsecutiveDays(99L)).thenReturn(0);
            when(learningRecordsService.getStudyMinutesInRange(eq(99L), any(), any())).thenReturn(0L);
            when(learningRecordsService.getTodayStudyTime(99L)).thenReturn(0);
            when(textLearningRecordMapper.selectVersionDistribution(99L)).thenReturn(Collections.emptyList());
            when(textLearningRecordMapper.countDistinctTexts(99L)).thenReturn(0L);
            when(textLearningRecordMapper.countDistinctCompletedTexts(99L)).thenReturn(0L);

            LearnerProfileDto result = service.getUserProfile(99L);
            assertEquals("empty", result.getProfileStatus());
        }
    }

    @Nested
    @DisplayName("Tag Evaluation (via CompositeTagEngine)")
    class TagEvaluationTests {

        @Test
        @DisplayName("engine returns INACTIVE_RISK → tag appears in profile")
        void inactiveRisk_fromEngine() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(new LearnerTagDto("INACTIVE_RISK", "流失风险", "risk", 10, "提醒")))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "INACTIVE_RISK".equals(t.getCode())));
        }

        @Test
        @DisplayName("engine returns empty → no tags")
        void noTags_fromEngine() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(new ArrayList<>());

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().isEmpty());
        }

        @Test
        @DisplayName("engine receives correct metrics map from habit/ability/preference")
        void metricsMapPassedCorrectly() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                Map<String, Double> metrics = invocation.getArgument(0);
                assertEquals(5.0, metrics.get("activeDays7d"));
                assertEquals(10.0, metrics.get("activeDays14d"));
                assertEquals(100.0, metrics.get("totalWords"));
                assertEquals(0.4, metrics.get("weakRatio"), 0.01);
                assertEquals(20.0, metrics.get("dueReviewCount"));
                assertEquals(0.25, metrics.get("overdueReviewRatio"), 0.01);
                assertEquals(15.0, metrics.get("textsRead"));
                assertEquals(2.0, metrics.get("versionDistSize"));
                return new ArrayList<>();
            });

            service.getUserProfile(1L);
            verify(compositeTagEngine).evaluateAll(any());
        }

        @Test
        @DisplayName("CONSISTENT_LEARNER from engine → tag appears")
        void consistentLearner_fromEngine() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(new LearnerTagDto("CONSISTENT_LEARNER", "学习稳定", "positive", 40, "保持")))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "CONSISTENT_LEARNER".equals(t.getCode())));
        }

        @Test
        @DisplayName("CONTENT_LEVEL_STUCK from engine → tag appears")
        void contentLevelStuck_fromEngine() {
            enableProfile();
            setupActiveStudent(1L);
            List<Map<String, Object>> singleVersionDist = new ArrayList<>();
            Map<String, Object> onlyOne = new HashMap<>();
            onlyOne.put("content_version", "cet4");
            onlyOne.put("total_seconds", 7200L);
            singleVersionDist.add(onlyOne);
            when(textLearningRecordMapper.selectVersionDistribution(1L)).thenReturn(singleVersionDist);
            when(textLearningRecordMapper.selectPrimaryVersion(1L)).thenReturn("cet4");
            when(textLearningRecordMapper.countDistinctTexts(1L)).thenReturn(20L);

            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(new LearnerTagDto("CONTENT_LEVEL_STUCK", "版本停滞", "ability", 50, "尝试")))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertTrue(result.getTags().stream().anyMatch(t -> "CONTENT_LEVEL_STUCK".equals(t.getCode())));
        }
    }

    @Nested
    @DisplayName("Risk Level Calculation")
    class RiskLevelTests {

        @Test
        @DisplayName("INACTIVE_RISK alone → HIGH risk")
        void inactiveRisk_alwaysHigh() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(new LearnerTagDto("INACTIVE_RISK", "流失风险", "risk", 10, "")))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("high", result.getRisk().getRiskLevel());
            assertTrue(result.getRisk().isChurnRisk());
        }

        @Test
        @DisplayName("Two risk tags (no INACTIVE_RISK) → HIGH risk")
        void twoRiskTags_high() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(
                            new LearnerTagDto("VOCAB_WEAK", "词汇弱势", "risk", 20, ""),
                            new LearnerTagDto("REVIEW_OVERDUE", "复习逾期", "risk", 30, "")
                    ))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("high", result.getRisk().getRiskLevel());
        }

        @Test
        @DisplayName("One risk tag (VOCAB_WEAK only) → MEDIUM risk")
        void oneRiskTag_medium() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(
                    new ArrayList<>(List.of(new LearnerTagDto("VOCAB_WEAK", "词汇弱势", "risk", 20, "")))
            );

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("medium", result.getRisk().getRiskLevel());
        }

        @Test
        @DisplayName("No risk tags → LOW risk")
        void noRiskTags_low() {
            enableProfile();
            setupActiveStudent(1L);
            when(compositeTagEngine.evaluateAll(any())).thenReturn(new ArrayList<>());

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("low", result.getRisk().getRiskLevel());
            assertFalse(result.getRisk().isChurnRisk());
        }
    }

    @Nested
    @DisplayName("Version Preference & Target Exam Level")
    class VersionPreferenceTests {

        @Test
        @DisplayName("cet4 >= 50% → targetExamLevel=CET-4")
        void cet4Dominant_infersCET4() {
            enableProfile();
            setupActiveStudent(1L);
            List<Map<String, Object>> dist = new ArrayList<>();
            Map<String, Object> cet4Row = new HashMap<>();
            cet4Row.put("content_version", "cet4");
            cet4Row.put("total_seconds", 6000L);
            dist.add(cet4Row);
            Map<String, Object> cet6Row = new HashMap<>();
            cet6Row.put("content_version", "cet6");
            cet6Row.put("total_seconds", 4000L);
            dist.add(cet6Row);
            when(textLearningRecordMapper.selectVersionDistribution(1L)).thenReturn(dist);
            when(textLearningRecordMapper.selectPrimaryVersion(1L)).thenReturn("cet4");

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("CET-4", result.getPreference().getTargetExamLevel());
            assertEquals("cet4", result.getPreference().getPrimaryContentVersion());
            assertEquals(0.6, result.getPreference().getVersionDistribution().get("cet4"), 0.01);
        }

        @Test
        @DisplayName("cet6 >= 40% → targetExamLevel=CET-6")
        void cet6Dominant_infersCET6() {
            enableProfile();
            setupActiveStudent(1L);
            List<Map<String, Object>> dist = new ArrayList<>();
            Map<String, Object> cet6Row = new HashMap<>();
            cet6Row.put("content_version", "cet6");
            cet6Row.put("total_seconds", 4500L);
            dist.add(cet6Row);
            Map<String, Object> cet4Row = new HashMap<>();
            cet4Row.put("content_version", "cet4");
            cet4Row.put("total_seconds", 3000L);
            dist.add(cet4Row);
            Map<String, Object> academicRow = new HashMap<>();
            academicRow.put("content_version", "academic");
            academicRow.put("total_seconds", 2500L);
            dist.add(academicRow);
            when(textLearningRecordMapper.selectVersionDistribution(1L)).thenReturn(dist);
            when(textLearningRecordMapper.selectPrimaryVersion(1L)).thenReturn("cet6");

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("CET-6", result.getPreference().getTargetExamLevel());
        }

        @Test
        @DisplayName("academic >= 30% → targetExamLevel=考研")
        void academicDominant_infersAcademic() {
            enableProfile();
            setupActiveStudent(1L);
            List<Map<String, Object>> dist = new ArrayList<>();
            Map<String, Object> acRow = new HashMap<>();
            acRow.put("content_version", "academic");
            acRow.put("total_seconds", 3500L);
            dist.add(acRow);
            Map<String, Object> cet6Row = new HashMap<>();
            cet6Row.put("content_version", "cet6");
            cet6Row.put("total_seconds", 3500L);
            dist.add(cet6Row);
            Map<String, Object> cet4Row = new HashMap<>();
            cet4Row.put("content_version", "cet4");
            cet4Row.put("total_seconds", 3000L);
            dist.add(cet4Row);
            when(textLearningRecordMapper.selectVersionDistribution(1L)).thenReturn(dist);
            when(textLearningRecordMapper.selectPrimaryVersion(1L)).thenReturn("academic");

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("考研", result.getPreference().getTargetExamLevel());
        }

        @Test
        @DisplayName("no version data → primaryContentVersion=unknown, targetExamLevel=null")
        void noVersionData_unknownDefault() {
            enableProfile();
            setupActiveStudent(1L);
            when(textLearningRecordMapper.selectVersionDistribution(1L)).thenReturn(Collections.emptyList());

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("unknown", result.getPreference().getPrimaryContentVersion());
            assertNull(result.getPreference().getTargetExamLevel());
        }
    }

    @Nested
    @DisplayName("Class Profile Summary")
    class ClassProfileSummaryTests {

        @Test
        @DisplayName("class with students → returns summaries")
        void classWithStudents_returnsSummaries() {
            enableProfile();
            Long classId = 10L;
            StudentClass sc1 = new StudentClass();
            sc1.setStudentId(1L);
            StudentClass sc2 = new StudentClass();
            sc2.setStudentId(2L);
            when(studentClassMapper.selectStudentsByClassId(classId)).thenReturn(Arrays.asList(sc1, sc2));

            setupActiveStudent(1L);
            setupActiveStudent(2L);

            List<LearnerProfileClassSummaryDto> result = service.getClassProfileSummary(classId);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("empty class → returns empty list")
        void emptyClass_returnsEmpty() {
            enableProfile();
            when(studentClassMapper.selectStudentsByClassId(99L)).thenReturn(Collections.emptyList());

            List<LearnerProfileClassSummaryDto> result = service.getClassProfileSummary(99L);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Evidence & Status Determination")
    class EvidenceTests {

        @Test
        @DisplayName("3+ data points → READY, completeness=high")
        void multipleDataPoints_ready() {
            enableProfile();
            setupActiveStudent(1L);

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("ready", result.getProfileStatus());
            assertEquals("high", result.getEvidence().getDataCompleteness());
            assertTrue(result.getEvidence().getDataPointCount() >= 3);
        }

        @Test
        @DisplayName("1-2 data points → PARTIAL")
        void fewDataPoints_partial() {
            enableProfile();
            User user = new User();
            user.setId(50L);
            user.setUsername("sparse");
            when(userService.getUserById(50L)).thenReturn(user);

            Map<String, Object> emptyVocab = new HashMap<>();
            emptyVocab.put("totalWords", 0L);
            emptyVocab.put("newWords", 0L);
            emptyVocab.put("learningWords", 0L);
            emptyVocab.put("masteredWords", 0L);
            when(vocabularyBooksService.getWordStudyStats(eq(50L), isNull())).thenReturn(emptyVocab);

            Map<String, Object> emptyReview = new HashMap<>();
            emptyReview.put("overdueCount", 0);
            emptyReview.put("dueCount", 0);
            emptyReview.put("overdueRatio", 0.0);
            when(vocabularyBooksService.getReviewStats(50L)).thenReturn(emptyReview);

            when(learningRecordsService.countActiveDays(eq(50L), eq(7))).thenReturn(0);
            when(learningRecordsService.countActiveDays(eq(50L), eq(14))).thenReturn(2);
            when(learningRecordsService.getConsecutiveDays(50L)).thenReturn(0);
            when(learningRecordsService.getStudyMinutesInRange(eq(50L), any(), any())).thenReturn(0L);
            when(learningRecordsService.getTodayStudyTime(50L)).thenReturn(0);
            when(textLearningRecordMapper.selectVersionDistribution(50L)).thenReturn(Collections.emptyList());
            when(textLearningRecordMapper.countDistinctTexts(50L)).thenReturn(5L);
            when(textLearningRecordMapper.countDistinctCompletedTexts(50L)).thenReturn(2L);

            LearnerProfileDto result = service.getUserProfile(50L);
            assertEquals("partial", result.getProfileStatus());
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("service exception → returns EMPTY, does not throw")
        void serviceException_gracefulDegradation() {
            enableProfile();
            when(userService.getUserById(1L)).thenThrow(new RuntimeException("DB error"));

            LearnerProfileDto result = service.getUserProfile(1L);
            assertEquals("empty", result.getProfileStatus());
        }
    }
}
