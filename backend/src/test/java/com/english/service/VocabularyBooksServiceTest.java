package com.english.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.VocabularyBook;
import com.english.entity.Word;
import com.english.entity.Unit;
import com.english.entity.UnitWord;
import com.english.mapper.VocabularyBookMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.UnitMapper;
import com.english.service.impl.VocabularyBooksServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VocabularyBooksServiceTest {

    @Mock
    private VocabularyBookMapper vocabularyBookMapper;

    @Mock
    private WordMapper wordMapper;

    @Mock
    private UnitWordMapper unitWordMapper;

    @Mock
    private UnitMapper unitMapper;

    @InjectMocks
    private VocabularyBooksServiceImpl vocabularyBooksService;

    @BeforeEach
    public void setUp() {
        // ExtendWith handles initialization
    }

    @Test
    public void testUpdateMasteryLevelWithSM2() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "MASTERED";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("repetition", 3);
        sm2Params.put("interval", 10);
        sm2Params.put("easinessFactor", 2.6);
        sm2Params.put("nextReviewTime", "2026-03-01T10:00:00");

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getMasteryLevel().equals("MASTERED") &&
            book.getReviewCount() == 6 &&
            book.getRepetition() == 3 &&
            book.getIntervalDays() == 10 &&
            book.getEasinessFactor() == 2.6 &&
            book.getNextReviewTime() != null
        ));
    }

    @Test
    public void testGetVocabularyWithPagination() {
        Long studentId = 1L;
        Map<String, Object> params = new HashMap<>();
        params.put("studentId", studentId);
        params.put("page", 1);
        params.put("size", 10);

        List<VocabularyBook> vbList = new ArrayList<>();
        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setWordId(100L);
        vb.setRepetition(2);
        vb.setIntervalDays(5);
        vb.setEasinessFactor(2.4);
        vbList.add(vb);

        Page<VocabularyBook> page = new Page<>(1, 10);
        page.setRecords(vbList);
        page.setTotal(1);

        Word word = new Word();
        word.setId(100L);
        word.setWord("example");

        when(vocabularyBookMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(wordMapper.selectBatchIds(any())).thenReturn(List.of(word));

        Map<String, Object> result = vocabularyBooksService.getVocabularyWithPagination(params);

        assertNotNull(result);
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        assertEquals(1, records.size());

        Map<String, Object> record = records.get(0);
        assertEquals("example", record.get("word"));
        assertEquals(2, record.get("repetition"));
        assertEquals(5, record.get("interval"));
        assertEquals(2.4, record.get("easinessFactor"));
    }

    @Test
    public void testAddToVocabularyWithLevel() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        when(vocabularyBookMapper.insert(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.addToVocabularyWithLevel(studentId, wordId, masteryLevel);

        verify(vocabularyBookMapper, times(1)).insert(argThat(vb ->
            vb.getStudentId().equals(studentId) &&
            vb.getWordId().equals(wordId) &&
            vb.getMasteryLevel().equals(masteryLevel) &&
            vb.getReviewCount() == 0
        ));
    }

    @Test
    void addToVocabulary_validInputs_shouldInsertRecord() {
        Long studentId = 1L;
        Long wordId = 100L;

        when(vocabularyBookMapper.insert(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.addToVocabulary(studentId, wordId);

        verify(vocabularyBookMapper, times(1)).insert(argThat(vb ->
            vb.getStudentId().equals(studentId) &&
            vb.getWordId().equals(wordId) &&
            "NEW".equals(vb.getMasteryLevel()) &&
            vb.getReviewCount() == 0
        ));
    }

    @Test
    void updateMasteryLevel_notFoundInVocabulary_shouldNotUpdate() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "MASTERED";

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, new HashMap<>());

        verify(vocabularyBookMapper, never()).updateById(any());
    }

    @Test
    void updateMasteryLevel_invalidSM2Params_shouldUseDefaults() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setReviewCount(2);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        Map<String, Object> invalidParams = new HashMap<>();
        invalidParams.put("repetition", "invalid");
        invalidParams.put("interval", -5);
        invalidParams.put("easinessFactor", 10.0); // exceeds MAX_EF

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, invalidParams);

        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getMasteryLevel().equals("LEARNING") &&
            book.getReviewCount() == 3 &&
            book.getEasinessFactor() == 5.0 // capped at MAX_EF
        ));
    }

    @Test
    void processStudyResults_validResults_shouldAddOrUpdateVocabulary() {
        Long studentId = 1L;
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> studyResults = new ArrayList<>();

        Map<String, Object> result1 = new HashMap<>();
        result1.put("wordId", 100L);
        result1.put("outcome", "KNOWN");

        studyResults.add(result1);
        results.put("results", studyResults);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        when(vocabularyBookMapper.insert(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.processStudyResults(studentId, results);

        verify(vocabularyBookMapper, times(1)).insert(argThat(vb ->
            vb.getStudentId().equals(studentId) &&
            vb.getWordId().equals(100L) &&
            "MASTERED".equals(vb.getMasteryLevel())
        ));
    }

    @Test
    void processStudyResults_nullResults_shouldReturnEarly() {
        Long studentId = 1L;
        Map<String, Object> results = new HashMap<>();
        results.put("results", null);

        vocabularyBooksService.processStudyResults(studentId, results);

        verify(vocabularyBookMapper, never()).selectOne(any());
        verify(vocabularyBookMapper, never()).insert(any());
    }

    @Test
    void processStudyResults_tooManyResults_shouldThrowException() {
        Long studentId = 1L;
        Map<String, Object> results = new HashMap<>();
        List<Map<String, Object>> studyResults = new ArrayList<>();

        for (int i = 0; i < 201; i++) {
            Map<String, Object> result = new HashMap<>();
            result.put("wordId", (long) i);
            result.put("outcome", "KNOWN");
            studyResults.add(result);
        }

        results.put("results", studyResults);

        assertThrows(Exception.class, () ->
            vocabularyBooksService.processStudyResults(studentId, results)
        );
    }

    @Test
    void batchDeleteVocabulary_validIds_shouldDelete() {
        Long studentId = 1L;
        List<Long> wordIds = List.of(100L, 101L, 102L);

        when(vocabularyBookMapper.delete(any(QueryWrapper.class))).thenReturn(3);

        vocabularyBooksService.batchDeleteVocabulary(studentId, wordIds);

        verify(vocabularyBookMapper, times(1)).delete(any(QueryWrapper.class));
    }

    @Test
    void batchDeleteVocabulary_emptyList_shouldReturnEarly() {
        Long studentId = 1L;
        List<Long> wordIds = new ArrayList<>();

        vocabularyBooksService.batchDeleteVocabulary(studentId, wordIds);

        verify(vocabularyBookMapper, never()).delete(any());
    }

    @Test
    void batchDeleteVocabulary_nullStudentId_shouldReturnEarly() {
        List<Long> wordIds = List.of(100L, 101L);

        vocabularyBooksService.batchDeleteVocabulary(null, wordIds);

        verify(vocabularyBookMapper, never()).delete(any());
    }

    @Test
    void batchUpdateMasteryLevel_validInputs_shouldUpdate() {
        Long studentId = 1L;
        List<Long> wordIds = List.of(100L, 101L);
        String masteryLevel = "MASTERED";

        when(vocabularyBookMapper.update(any(VocabularyBook.class), any(QueryWrapper.class))).thenReturn(2);

        vocabularyBooksService.batchUpdateMasteryLevel(studentId, wordIds, masteryLevel);

        verify(vocabularyBookMapper, times(1)).update(any(VocabularyBook.class), any(QueryWrapper.class));
    }

    @Test
    void batchUpdateMasteryLevel_nullWordIds_shouldReturnEarly() {
        Long studentId = 1L;

        vocabularyBooksService.batchUpdateMasteryLevel(studentId, null, "MASTERED");

        verify(vocabularyBookMapper, never()).update(any(), any());
    }

    @Test
    void getVocabularyStats_validStudentId_shouldReturnStats() {
        Long studentId = 1L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class)))
            .thenReturn(10L) // total
            .thenReturn(2L)  // new
            .thenReturn(3L)  // learning
            .thenReturn(5L)  // mastered
            .thenReturn(1L)  // today
            .thenReturn(6L); // weekly

        Map<String, Object> result = vocabularyBooksService.getVocabularyStats(studentId);

        assertNotNull(result);
        assertEquals(10L, result.get("totalVocabulary"));
        assertEquals(2L, result.get("newCount"));
        assertEquals(3L, result.get("learningCount"));
        assertEquals(5L, result.get("masteredCount"));
        assertEquals(50.0, result.get("masteryRate"));
        assertEquals(1L, result.get("todayAdded"));
        assertEquals(6L, result.get("weeklyReviews"));
    }

    @Test
    void getVocabularyStats_zeroTotal_shouldReturnZeroRate() {
        Long studentId = 1L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class)))
            .thenReturn(0L) // total
            .thenReturn(0L) // new
            .thenReturn(0L) // learning
            .thenReturn(0L) // mastered
            .thenReturn(0L) // today
            .thenReturn(0L); // weekly

        Map<String, Object> result = vocabularyBooksService.getVocabularyStats(studentId);

        assertEquals(0.0, result.get("masteryRate"));
    }

    @Test
    void getWordDetailWithLearningState_wordExists_shouldReturnDetails() {
        Long studentId = 1L;
        Long wordId = 100L;

        Word word = new Word();
        word.setId(wordId);
        word.setWord("example");
        word.setDefinition("sample definition");
        word.setDeleted(0);

        VocabularyBook vb = new VocabularyBook();
        vb.setMasteryLevel("LEARNING");
        vb.setReviewCount(3);

        when(wordMapper.selectById(wordId)).thenReturn(word);
        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);

        Map<String, Object> result = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);

        assertNotNull(result);
        assertEquals("example", result.get("word"));
        assertTrue((Boolean) result.get("inVocabulary"));
        assertEquals("LEARNING", result.get("masteryLevel"));
        assertEquals(3, result.get("reviewCount"));
    }

    @Test
    void getWordDetailWithLearningState_wordNotInVocabulary_shouldReturnBasics() {
        Long studentId = 1L;
        Long wordId = 100L;

        Word word = new Word();
        word.setId(wordId);
        word.setWord("example");
        word.setDeleted(0);

        when(wordMapper.selectById(wordId)).thenReturn(word);
        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        Map<String, Object> result = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);

        assertFalse((Boolean) result.get("inVocabulary"));
        assertNull(result.get("masteryLevel"));
    }

    @Test
    void getWordDetailWithLearningState_deletedWord_shouldReturnEmpty() {
        Long studentId = 1L;
        Long wordId = 100L;

        Word word = new Word();
        word.setId(wordId);
        word.setDeleted(1);

        when(wordMapper.selectById(wordId)).thenReturn(word);

        Map<String, Object> result = vocabularyBooksService.getWordDetailWithLearningState(studentId, wordId);

        assertTrue(result.isEmpty());
    }

    @Test
    void createStudySession_emptyUnit_shouldReturnEmptySession() {
        Long studentId = 1L;
        Long unitId = 1L;

        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

        Map<String, Object> result = vocabularyBooksService.createStudySession(studentId, unitId);

        assertNotNull(result);
        assertEquals(unitId, result.get("unitId"));
        assertEquals(0, result.get("totalWords"));
        assertEquals(Collections.emptyList(), result.get("words"));
    }

    @Test
    void createStudySession_allWordsDisabled_shouldReturnEmptyList() {
        Long studentId = 1L;
        Long unitId = 1L;

        UnitWord uw1 = new UnitWord();
        uw1.setWordId(100L);
        uw1.setUnitId(unitId);

        Word word1 = new Word();
        word1.setId(100L);
        word1.setWord("word1");
        word1.setDeleted(1); // disabled

        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(uw1));
        when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
        when(wordMapper.selectBatchIds(any())).thenReturn(List.of(word1));

        Map<String, Object> result = vocabularyBooksService.createStudySession(studentId, unitId);

        assertEquals(0, result.get("totalWords"));
    }

    @Test
    void createStudySession_wordsMastered_shouldFilterThem() {
        Long studentId = 1L;
        Long unitId = 1L;

        UnitWord uw1 = new UnitWord();
        uw1.setWordId(100L);
        uw1.setUnitId(unitId);

        Word word1 = new Word();
        word1.setId(100L);
        word1.setWord("word1");
        word1.setDefinition("def");
        word1.setDeleted(0);

        VocabularyBook masteredVb = new VocabularyBook();
        masteredVb.setWordId(100L);
        masteredVb.setMasteryLevel("MASTERED");

        when(unitWordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(uw1));
        when(vocabularyBookMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(masteredVb));
        when(wordMapper.selectBatchIds(any())).thenReturn(List.of(word1));

        Map<String, Object> result = vocabularyBooksService.createStudySession(studentId, unitId);

        assertEquals(0, result.get("totalWords"));
    }

    @Test
    void getWordStudyStats_validInputs_shouldReturnStats() {
        Long studentId = 1L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class)))
            .thenReturn(10L) // total
            .thenReturn(2L)  // new
            .thenReturn(4L)  // learning
            .thenReturn(4L); // mastered

        Map<String, Object> result = vocabularyBooksService.getWordStudyStats(studentId, "week");

        assertNotNull(result);
        assertEquals(10L, result.get("totalWords"));
        assertEquals(2L, result.get("newWords"));
        assertEquals(4L, result.get("learningWords"));
        assertEquals(4L, result.get("masteredWords"));
    }

    @Test
    void countByStudentId_validStudentId_shouldReturnCount() {
        Long studentId = 1L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        int result = vocabularyBooksService.countByStudentId(studentId);

        assertEquals(5, result);
    }

    @Test
    void existsByStudentAndWord_exists_shouldReturnTrue() {
        Long studentId = 1L;
        Long wordId = 100L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        boolean result = vocabularyBooksService.existsByStudentAndWord(studentId, wordId);

        assertTrue(result);
    }

    @Test
    void existsByStudentAndWord_notExists_shouldReturnFalse() {
        Long studentId = 1L;
        Long wordId = 100L;

        when(vocabularyBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        boolean result = vocabularyBooksService.existsByStudentAndWord(studentId, wordId);

        assertFalse(result);
    }

    @Test
    void getRecentWords_noStudent_shouldReturnEmpty() {
        Long studentId = null;
        int limit = 10;

        Page<VocabularyBook> emptyPage = new Page<>(1, limit);
        emptyPage.setRecords(new ArrayList<>());

        when(vocabularyBookMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        List<Map<String, Object>> result = vocabularyBooksService.getRecentWords(studentId, limit);

        assertEquals(0, result.size());
    }

    @Test
    void removeFromVocabulary_validIds_shouldDelete() {
        Long studentId = 1L;
        Long wordId = 100L;

        when(vocabularyBookMapper.delete(any(QueryWrapper.class))).thenReturn(1);

        vocabularyBooksService.removeFromVocabulary(studentId, wordId);

        verify(vocabularyBookMapper, times(1)).delete(any(QueryWrapper.class));
    }
}
