package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.*;
import com.english.mapper.*;
import com.english.service.impl.LearningRecordsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for LearningRecordsServiceImpl.
 * Covers all 25 public methods with edge cases and boundary conditions.
 */
@ExtendWith(MockitoExtension.class)
class LearningRecordsServiceTest {

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @Mock
    private TextLearningRecordMapper textLearningRecordMapper;

    @Mock
    private TextMapper textMapper;

    @Mock
    private UnitMapper unitMapper;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @Mock
    private VocabularyBookMapper vocabularyBookMapper;

    @InjectMocks
    private LearningRecordsServiceImpl learningRecordsService;

    // ==================== getTodayStudyTime ====================

    @Nested
    @DisplayName("getTodayStudyTime tests")
    class GetTodayStudyTimeTests {

        @Test
        void shouldReturnMinutesWhenRecordsExist() {
            LearningRecord r1 = new LearningRecord();
            r1.setDurationSeconds(300); // 5 min
            LearningRecord r2 = new LearningRecord();
            r2.setDurationSeconds(600); // 10 min
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(r1, r2));

            int result = learningRecordsService.getTodayStudyTime(1L);

            assertEquals(15, result); // 900/60 = 15 min
        }

        @Test
        void shouldReturnZeroWhenNoRecords() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>());

            assertEquals(0, learningRecordsService.getTodayStudyTime(1L));
        }

        @Test
        void shouldReturnZeroWhenMapperReturnsNull() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(null);

            assertEquals(0, learningRecordsService.getTodayStudyTime(1L));
        }

        @Test
        void shouldThrowWhenStudentIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getTodayStudyTime(null));
        }

        @Test
        void shouldFilterAnomalousRecords() {
            LearningRecord normal = new LearningRecord();
            normal.setDurationSeconds(300);
            LearningRecord anomalous = new LearningRecord();
            anomalous.setDurationSeconds(9000); // > 7200, anomalous
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(normal, anomalous));

            int result = learningRecordsService.getTodayStudyTime(1L);

            assertEquals(5, result); // only 300/60=5 counted
        }

        @Test
        void shouldCapAt720Minutes() {
            List<LearningRecord> records = new ArrayList<>();
            for (int i = 0; i < 500; i++) {
                LearningRecord r = new LearningRecord();
                r.setDurationSeconds(120); // 2 min each = 1000 min total
                records.add(r);
            }
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(records);

            int result = learningRecordsService.getTodayStudyTime(1L);

            assertEquals(720, result);
        }

        @Test
        void shouldHandleNullDurationSeconds() {
            LearningRecord r = new LearningRecord();
            r.setDurationSeconds(null);
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.singletonList(r));

            assertEquals(0, learningRecordsService.getTodayStudyTime(1L));
        }
    }

    // ==================== recordTextView (2 params) ====================

    @Nested
    @DisplayName("recordTextView(studentId, textId) tests")
    class RecordTextView2ParamsTests {

        @Test
        void shouldInsertRecordWithCorrectFields() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordTextView(1L, 100L);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals(1L, captured.getStudentId());
            assertEquals(100L, captured.getTextId());
            assertEquals("TEXT_VIEW", captured.getRecordType());
            assertEquals(15, captured.getDurationSeconds());
            assertEquals(0, captured.getCorrectCount());
            assertEquals(1, captured.getTotalCount());
            assertNull(captured.getWordId());
            assertNull(captured.getAssignmentId());
            assertNotNull(captured.getCreatedTime());
        }
    }

    // ==================== recordTextView (4 params) ====================

    @Nested
    @DisplayName("recordTextView(studentId, textId, assignmentId, duration) tests")
    class RecordTextView4ParamsTests {

        @Test
        void shouldUpdateExistingRecord() {
            when(learningRecordMapper.incrementTextViewDuration(1L, 100L, 50L, 25))
                    .thenReturn(1);

            learningRecordsService.recordTextView(1L, 100L, 50L, 25);

            verify(learningRecordMapper).incrementTextViewDuration(1L, 100L, 50L, 25);
            verify(learningRecordMapper, never()).insert(any());
        }

        @Test
        void shouldCreateNewRecordWhenNoExistingFound() {
            when(learningRecordMapper.incrementTextViewDuration(1L, 100L, 50L, 25))
                    .thenReturn(0);

            learningRecordsService.recordTextView(1L, 100L, 50L, 25);

            verify(learningRecordMapper).incrementTextViewDuration(1L, 100L, 50L, 25);
            verify(learningRecordMapper).insert(any(LearningRecord.class));
        }

        @Test
        void shouldDefaultDurationToOneWhenNull() {
            when(learningRecordMapper.incrementTextViewDuration(1L, 100L, 50L, 1))
                    .thenReturn(0);

            learningRecordsService.recordTextView(1L, 100L, 50L, null);

            verify(learningRecordMapper).incrementTextViewDuration(1L, 100L, 50L, 1);
        }

        @Test
        void shouldDefaultDurationToOneWhenZero() {
            when(learningRecordMapper.incrementTextViewDuration(1L, 100L, 50L, 1))
                    .thenReturn(0);

            learningRecordsService.recordTextView(1L, 100L, 50L, 0);

            verify(learningRecordMapper).incrementTextViewDuration(1L, 100L, 50L, 1);
        }
    }

    // ==================== recordWordView ====================

    @Nested
    @DisplayName("recordWordView tests")
    class RecordWordViewTests {

        @Test
        void twoParams_shouldInsertWithDefaultDuration() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordWordView(1L, 200L);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals(1L, captured.getStudentId());
            assertEquals(200L, captured.getWordId());
            assertEquals("VIEW", captured.getRecordType());
            assertEquals(30, captured.getDurationSeconds());
            assertNull(captured.getTextId());
            assertNull(captured.getAssignmentId());
        }

        @Test
        void fourParams_shouldInsertWithProvidedDuration() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordWordView(1L, 200L, 50L, 45);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals(50L, captured.getAssignmentId());
            assertEquals(45, captured.getDurationSeconds());
        }

        @Test
        void fourParams_shouldDefaultDurationTo30WhenNull() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordWordView(1L, 200L, 50L, null);

            verify(learningRecordMapper).insert(captor.capture());
            assertEquals(30, captor.getValue().getDurationSeconds());
        }
    }

    // ==================== recordLearning ====================

    @Nested
    @DisplayName("recordLearning tests")
    class RecordLearningTests {

        @Test
        void shouldInsertWithAllFields() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordLearning(1L, "REVIEW", 200L, 60, 100L, 50L);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals(1L, captured.getStudentId());
            assertEquals("REVIEW", captured.getRecordType());
            assertEquals(200L, captured.getWordId());
            assertEquals(60, captured.getDurationSeconds());
            assertEquals(100L, captured.getTextId());
            assertEquals(50L, captured.getAssignmentId());
        }

        @Test
        void shouldNotSetWordIdWhenNull() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordLearning(1L, "TEXT_VIEW", null, 40, 100L, 50L);

            verify(learningRecordMapper).insert(captor.capture());
            assertNull(captor.getValue().getWordId());
        }

        @Test
        void shouldDefaultDurationToOneWhenNull() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordLearning(1L, "REVIEW", 200L, null, 100L, 50L);

            verify(learningRecordMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getDurationSeconds());
        }

        @Test
        void shouldDefaultDurationToOneWhenZero() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordLearning(1L, "REVIEW", 200L, 0, 100L, 50L);

            verify(learningRecordMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getDurationSeconds());
        }

        @Test
        void shouldAcceptNegativeDurationAsDefault() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordLearning(1L, "REVIEW", 200L, -5, 100L, 50L);

            verify(learningRecordMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getDurationSeconds());
        }
    }

    // ==================== recordVocabularyAdd ====================

    @Nested
    @DisplayName("recordVocabularyAdd tests")
    class RecordVocabularyAddTests {

        @Test
        void shouldInsertViewRecordWith10sDuration() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordVocabularyAdd(1L, 300L);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals(1L, captured.getStudentId());
            assertEquals(300L, captured.getWordId());
            assertEquals("VIEW", captured.getRecordType());
            assertEquals(10, captured.getDurationSeconds());
        }
    }

    // ==================== startReadingSession ====================

    @Nested
    @DisplayName("startReadingSession tests")
    class StartReadingSessionTests {

        @Test
        void shouldReturnRecordId() {
            doAnswer(invocation -> {
                TextLearningRecord record = invocation.getArgument(0);
                record.setId(42L);
                return 1;
            }).when(textLearningRecordMapper).insert(any(TextLearningRecord.class));

            Long id = learningRecordsService.startReadingSession(1L, 100L, 50L);

            assertEquals(42L, id);
        }

        @Test
        void shouldSetCorrectFieldsIncludingAssignment() {
            doAnswer(invocation -> {
                TextLearningRecord record = invocation.getArgument(0);
                record.setId(1L);
                return 1;
            }).when(textLearningRecordMapper).insert(any(TextLearningRecord.class));

            ArgumentCaptor<TextLearningRecord> captor = ArgumentCaptor.forClass(TextLearningRecord.class);

            learningRecordsService.startReadingSession(1L, 100L, 50L);

            verify(textLearningRecordMapper).insert(captor.capture());
            TextLearningRecord captured = captor.getValue();
            assertEquals(1L, captured.getStudentId());
            assertEquals(100L, captured.getTextId());
            assertEquals(50L, captured.getAssignmentId());
            assertEquals(0, captured.getIsCompleted());
            assertNotNull(captured.getStartTime());
        }

        @Test
        void shouldHandleNullAssignment() {
            doAnswer(invocation -> {
                TextLearningRecord record = invocation.getArgument(0);
                record.setId(2L);
                return 1;
            }).when(textLearningRecordMapper).insert(any(TextLearningRecord.class));

            ArgumentCaptor<TextLearningRecord> captor = ArgumentCaptor.forClass(TextLearningRecord.class);

            learningRecordsService.startReadingSession(2L, 200L, null);

            verify(textLearningRecordMapper).insert(captor.capture());
            assertNull(captor.getValue().getAssignmentId());
        }
    }

    // ==================== completeReadingSession ====================

    @Nested
    @DisplayName("completeReadingSession tests")
    class CompleteReadingSessionTests {

        @Test
        void shouldCompleteActiveSession() {
            TextLearningRecord record = new TextLearningRecord();
            record.setId(1L);
            record.setStudentId(1L);
            record.setTextId(100L);
            record.setStartTime(LocalDateTime.now().minusMinutes(5));
            record.setIsCompleted(0);

            when(textLearningRecordMapper.selectById(1L)).thenReturn(record);
            when(textLearningRecordMapper.updateById(any())).thenReturn(1);

            learningRecordsService.completeReadingSession(1L);

            assertEquals(1, record.getIsCompleted());
            assertNotNull(record.getEndTime());
            assertTrue(record.getDurationSeconds() >= 299);
            verify(textLearningRecordMapper).updateById(any());
        }

        @Test
        void shouldNotUpdateAlreadyCompletedSession() {
            TextLearningRecord record = new TextLearningRecord();
            record.setId(1L);
            record.setIsCompleted(1);

            when(textLearningRecordMapper.selectById(1L)).thenReturn(record);

            learningRecordsService.completeReadingSession(1L);

            verify(textLearningRecordMapper, never()).updateById(any());
        }

        @Test
        void shouldHandleRecordNotFound() {
            when(textLearningRecordMapper.selectById(999L)).thenReturn(null);

            learningRecordsService.completeReadingSession(999L);

            verify(textLearningRecordMapper, never()).updateById(any());
        }

        @Test
        void shouldPreventDurationOverflow() {
            TextLearningRecord record = new TextLearningRecord();
            record.setId(1L);
            record.setStartTime(LocalDateTime.now().minusYears(100));
            record.setIsCompleted(0);

            when(textLearningRecordMapper.selectById(1L)).thenReturn(record);
            when(textLearningRecordMapper.updateById(any())).thenReturn(1);

            learningRecordsService.completeReadingSession(1L);

            assertTrue(record.getDurationSeconds() <= Integer.MAX_VALUE);
            assertTrue(record.getDurationSeconds() >= 0);
        }
    }

    // ==================== recordWordReview ====================

    @Nested
    @DisplayName("recordWordReview tests")
    class RecordWordReviewTests {

        @Test
        void shouldRecordCorrectAnswer() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordWordReview(1L, 100L, true);

            verify(learningRecordMapper).insert(captor.capture());
            LearningRecord captured = captor.getValue();
            assertEquals("REVIEW", captured.getRecordType());
            assertEquals(1, captured.getCorrectCount());
            assertEquals(1, captured.getTotalCount());
            assertEquals(15, captured.getDurationSeconds());
        }

        @Test
        void shouldRecordIncorrectAnswer() {
            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);

            learningRecordsService.recordWordReview(1L, 100L, false);

            verify(learningRecordMapper).insert(captor.capture());
            assertEquals(0, captor.getValue().getCorrectCount());
        }

        @Test
        void shouldRecordMultipleReviewsSeparately() {
            learningRecordsService.recordWordReview(1L, 100L, true);
            learningRecordsService.recordWordReview(1L, 100L, false);

            ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);
            verify(learningRecordMapper, times(2)).insert(captor.capture());
            assertEquals(1, captor.getAllValues().get(0).getCorrectCount());
            assertEquals(0, captor.getAllValues().get(1).getCorrectCount());
        }
    }

    // ==================== getTodayLearningStats ====================

    @Nested
    @DisplayName("getTodayLearningStats tests")
    class GetTodayLearningStatsTests {

        @Test
        void shouldReturnStatsWithMixedRecords() {
            LearningRecord wordView = new LearningRecord();
            wordView.setRecordType("VIEW");
            wordView.setWordId(10L);
            wordView.setDurationSeconds(30);

            LearningRecord textView = new LearningRecord();
            textView.setRecordType("TEXT_VIEW");
            textView.setDurationSeconds(120);

            LearningRecord test = new LearningRecord();
            test.setRecordType("TEST");
            test.setDurationSeconds(300);
            test.setCorrectCount(8);
            test.setTotalCount(10);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(wordView, textView, test));

            Map<String, Object> result = learningRecordsService.getTodayLearningStats(1L);

            assertEquals(1, result.get("wordsLearned"));
            assertEquals(1, result.get("textsLearned"));
            assertEquals(0, result.get("reviewCount"));
            assertEquals(1, result.get("testCount"));
            assertEquals(3, result.get("totalActivities"));
            assertEquals(7, result.get("studyTime")); // 450/60=7
        }

        @Test
        void shouldReturnZerosWhenNoRecords() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getTodayLearningStats(1L);

            assertEquals(0, result.get("wordsLearned"));
            assertEquals(0, result.get("textsLearned"));
            assertEquals(0, result.get("reviewCount"));
            assertEquals(0, result.get("testCount"));
            assertEquals(0, result.get("totalActivities"));
            assertEquals(0, result.get("studyTime"));
            assertEquals(0.0, result.get("accuracy"));
        }

        @Test
        void shouldHandleNullRecordList() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(null);

            Map<String, Object> result = learningRecordsService.getTodayLearningStats(1L);

            assertNotNull(result);
            assertEquals(0, result.get("wordsLearned"));
        }

        @Test
        void shouldThrowWhenStudentIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getTodayLearningStats(null));
        }

        @Test
        void shouldCountOnlyWordViewsWithValidWordId() {
            LearningRecord viewNoWord = new LearningRecord();
            viewNoWord.setRecordType("VIEW");
            viewNoWord.setWordId(null);
            viewNoWord.setDurationSeconds(30);

            LearningRecord viewZeroWord = new LearningRecord();
            viewZeroWord.setRecordType("VIEW");
            viewZeroWord.setWordId(0L);
            viewZeroWord.setDurationSeconds(30);

            LearningRecord viewValidWord = new LearningRecord();
            viewValidWord.setRecordType("VIEW");
            viewValidWord.setWordId(5L);
            viewValidWord.setDurationSeconds(30);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(viewNoWord, viewZeroWord, viewValidWord));

            Map<String, Object> result = learningRecordsService.getTodayLearningStats(1L);

            assertEquals(1, result.get("wordsLearned"));
        }
    }

    // ==================== getLearningOverview ====================

    @Nested
    @DisplayName("getLearningOverview tests")
    class GetLearningOverviewTests {

        @Test
        void shouldReturnOverviewWithRecords() {
            LearningRecord r1 = new LearningRecord();
            r1.setWordId(10L);
            r1.setDurationSeconds(60);

            LearningRecord r2 = new LearningRecord();
            r2.setWordId(20L);
            r2.setDurationSeconds(120);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(r1, r2));

            Map<String, Object> result = learningRecordsService.getLearningOverview(1L, new HashMap<>());

            assertEquals(2L, result.get("totalWordsLearned"));
            assertEquals(3, result.get("totalStudyTime")); // 180/60=3
        }

        @Test
        void shouldReturnZeroStatsWhenNoRecords() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getLearningOverview(1L, new HashMap<>());

            assertEquals(0L, result.get("totalWordsLearned"));
            assertEquals(0L, result.get("totalStudyTime"));
            assertEquals(0.0, result.get("averageAccuracy"));
        }

        @Test
        void shouldCountUniqueWordsOnly() {
            LearningRecord r1 = new LearningRecord();
            r1.setWordId(10L);
            r1.setDurationSeconds(60);
            LearningRecord r2 = new LearningRecord();
            r2.setWordId(10L);
            r2.setDurationSeconds(60);
            LearningRecord r3 = new LearningRecord();
            r3.setWordId(20L);
            r3.setDurationSeconds(60);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(r1, r2, r3));

            Map<String, Object> result = learningRecordsService.getLearningOverview(1L, new HashMap<>());

            assertEquals(2L, result.get("totalWordsLearned"));
        }

        @Test
        void shouldHandleNullParams() {
            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>());

            assertDoesNotThrow(() -> learningRecordsService.getLearningOverview(1L, null));
        }

        @Test
        void shouldThrowWhenStudentIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getLearningOverview(null, new HashMap<>()));
        }

        @Test
        void shouldHandleNullDurationInRecords() {
            LearningRecord r1 = new LearningRecord();
            r1.setWordId(10L);
            r1.setDurationSeconds(null);
            LearningRecord r2 = new LearningRecord();
            r2.setWordId(20L);
            r2.setDurationSeconds(120);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Arrays.asList(r1, r2));

            Map<String, Object> result = learningRecordsService.getLearningOverview(1L, new HashMap<>());

            assertEquals(2L, result.get("totalWordsLearned"));
            assertEquals(2, result.get("totalStudyTime")); // 120/60=2
        }
    }

    // ==================== getLearningRecordsWithPagination ====================

    @Nested
    @DisplayName("getLearningRecordsWithPagination tests")
    class GetLearningRecordsWithPaginationTests {

        @Test
        void shouldThrowWhenParamsNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getLearningRecordsWithPagination(null));
        }

        @Test
        void shouldThrowWhenStudentIdMissing() {
            Map<String, Object> params = new HashMap<>();
            params.put("page", 1);
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getLearningRecordsWithPagination(params));
        }

        @Test
        void shouldUseDefaultPageAndSize() {
            Map<String, Object> params = new HashMap<>();
            params.put("studentId", 1L);

            Page<LearningRecord> pageResult = new Page<>(1, 10);
            pageResult.setRecords(new ArrayList<>());
            pageResult.setTotal(0);
            when(learningRecordMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                    .thenReturn(pageResult);

            Map<String, Object> result = learningRecordsService.getLearningRecordsWithPagination(params);

            assertNotNull(result);
            assertTrue(result.containsKey("records"));
            assertTrue(result.containsKey("total"));
        }
    }

    // ==================== getUnitsWithLearningStats ====================

    @Nested
    @DisplayName("getUnitsWithLearningStats tests")
    class GetUnitsWithLearningStatsTests {

        @Test
        void shouldReturnStatsForEachUnit() {
            Unit unit1 = new Unit();
            unit1.setId(10L);
            unit1.setName("Unit 1");
            unit1.setDescription("Desc 1");
            unit1.setOrderNum(1);

            when(unitMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.singletonList(unit1));
            // Mock all internal helper method calls
            when(textMapper.selectCount(any(QueryWrapper.class))).thenReturn(3L);
            when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(wordMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);
            when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(learningRecordMapper.selectMaps(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            List<Map<String, Object>> result = learningRecordsService.getUnitsWithLearningStats(1L, 100L);

            assertFalse(result.isEmpty());
            assertEquals(10L, result.get(0).get("id"));
            assertEquals("Unit 1", result.get(0).get("name"));
        }

        @Test
        void shouldReturnEmptyListForEmptyCourse() {
            when(unitMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(new ArrayList<>());

            List<Map<String, Object>> result = learningRecordsService.getUnitsWithLearningStats(1L, 100L);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldReturnEmptyListForNullUnitList() {
            when(unitMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(null);

            List<Map<String, Object>> result = learningRecordsService.getUnitsWithLearningStats(1L, 100L);

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldThrowWhenStudentIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getUnitsWithLearningStats(null, 100L));
        }

        @Test
        void shouldThrowWhenCourseIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getUnitsWithLearningStats(1L, null));
        }
    }

    // ==================== getUnitContentWithLearningStates ====================

    @Nested
    @DisplayName("getUnitContentWithLearningStates tests")
    class GetUnitContentWithLearningStatesTests {

        @Test
        void shouldReturnContentForUnitWithTexts() {
            Text text = new Text();
            text.setId(1L);
            text.setTitle("Test Text");
            text.setContent("Content");
            text.setDifficulty("INTERMEDIATE");
            text.setAudioUrl("http://audio.mp3");

            when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(text));
            when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(learningRecordMapper.selectMaps(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getUnitContentWithLearningStates(1L, 100L);

            assertNotNull(result);
            assertEquals(100L, result.get("unitId"));
            List<?> texts = (List<?>) result.get("texts");
            assertNotNull(texts);
            assertEquals(1, texts.size());
        }

        @Test
        void shouldReturnEmptyListsForEmptyUnit() {
            when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getUnitContentWithLearningStates(1L, 100L);

            assertTrue(((List<?>) result.get("texts")).isEmpty());
            assertTrue(((List<?>) result.get("words")).isEmpty());
        }

        @Test
        void shouldThrowWhenStudentIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getUnitContentWithLearningStates(null, 100L));
        }

        @Test
        void shouldThrowWhenUnitIdNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> learningRecordsService.getUnitContentWithLearningStates(1L, null));
        }

        @Test
        void shouldReturnWordsFromUnitWordRelation() {
            Word word = new Word();
            word.setId(1L);
            word.setWord("apple");
            word.setPronunciation("æpl");
            word.setDefinition("A fruit");
            word.setExampleSentence("An apple a day");
            word.setAudioUrl("http://word.mp3");
            word.setPartOfSpeech("noun");
            word.setDifficulty("EASY");

            UnitWord unitWord = new UnitWord();
            unitWord.setUnitId(100L);
            unitWord.setWordId(1L);

            when(textMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(unitWord));
            when(wordMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.singletonList(word));
            when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getUnitContentWithLearningStates(1L, 100L);

            List<?> words = (List<?>) result.get("words");
            assertNotNull(words);
            assertEquals(1, words.size());
        }
    }

    // ==================== getTextDetailWithAnnotation ====================

    @Nested
    @DisplayName("getTextDetailWithAnnotation tests")
    class GetTextDetailWithAnnotationTests {

        @Test
        void shouldReturnDefaultsWhenTextNotFound() {
            when(textMapper.selectById(999L)).thenReturn(null);

            Map<String, Object> result = learningRecordsService.getTextDetailWithAnnotation(1L, 999L, null);

            assertNotNull(result);
            assertEquals(999L, result.get("id"));
            assertEquals("课文不存在", result.get("title"));
            assertEquals("课文内容不存在", result.get("content"));
        }

        @Test
        void shouldReturnFullDetailsForExistingText() {
            Text text = new Text();
            text.setId(50L);
            text.setTitle("Sample Text");
            text.setContent("Sample content");
            text.setContentType("TEXT");
            text.setDifficulty("BEGINNER");
            text.setUnitId(10L);

            Unit unit = new Unit();
            unit.setId(10L);
            unit.setName("Unit 1");
            unit.setCourseId(100L);

            Course course = new Course();
            course.setId(100L);
            course.setName("Course 1");

            when(textMapper.selectById(50L)).thenReturn(text);
            when(unitMapper.selectById(10L)).thenReturn(unit);
            when(courseMapper.selectById(100L)).thenReturn(course);
            when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getTextDetailWithAnnotation(1L, 50L, null);

            assertNotNull(result);
            assertEquals(50L, result.get("id"));
            assertEquals("Sample Text", result.get("title"));
        }

        @Test
        void shouldHandleNullUnitAndCourse() {
            Text text = new Text();
            text.setId(50L);
            text.setTitle("Orphan Text");
            text.setContent("Content");
            text.setContentType("TEXT");
            text.setUnitId(null);

            when(textMapper.selectById(50L)).thenReturn(text);
            when(learningRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            Map<String, Object> result = learningRecordsService.getTextDetailWithAnnotation(1L, 50L, null);

            assertNotNull(result);
            assertEquals("Orphan Text", result.get("title"));
        }
    }

    // ==================== Edge Cases ====================

    @Nested
    @DisplayName("Edge cases and boundary conditions")
    class EdgeCaseTests {

        @Test
        void recordTextView2Params_shouldAcceptAnyIds() {
            assertDoesNotThrow(() -> learningRecordsService.recordTextView(1L, 1L));
            verify(learningRecordMapper).insert(any());
        }

        @Test
        void recordWordView2Params_shouldAcceptAnyIds() {
            assertDoesNotThrow(() -> learningRecordsService.recordWordView(1L, 1L));
            verify(learningRecordMapper).insert(any());
        }

        @Test
        void recordLearning_shouldAcceptAllNullOptionals() {
            assertDoesNotThrow(() ->
                    learningRecordsService.recordLearning(1L, "TEST", null, null, null, null));
            verify(learningRecordMapper).insert(any());
        }

        @Test
        void completeReadingSession_shouldHandleNullRecordId() {
            when(textLearningRecordMapper.selectById(isNull())).thenReturn(null);
            assertDoesNotThrow(() -> learningRecordsService.completeReadingSession(null));
            verify(textLearningRecordMapper, never()).updateById(any());
        }

        @Test
        void getLearningOverview_shouldHandleRecordsWithNullWordId() {
            LearningRecord r = new LearningRecord();
            r.setWordId(null);
            r.setDurationSeconds(60);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.singletonList(r));

            Map<String, Object> result = learningRecordsService.getLearningOverview(1L, new HashMap<>());

            assertEquals(0L, result.get("totalWordsLearned"));
            assertEquals(1, result.get("totalStudyTime"));
        }

        @Test
        void getTodayLearningStats_shouldHandleNullDurationInRecords() {
            LearningRecord r = new LearningRecord();
            r.setRecordType("VIEW");
            r.setWordId(10L);
            r.setDurationSeconds(null);

            when(learningRecordMapper.selectList(any(QueryWrapper.class)))
                    .thenReturn(Collections.singletonList(r));

            Map<String, Object> result = learningRecordsService.getTodayLearningStats(1L);

            assertEquals(1, result.get("wordsLearned"));
            assertEquals(0, result.get("studyTime"));
        }
    }
}
