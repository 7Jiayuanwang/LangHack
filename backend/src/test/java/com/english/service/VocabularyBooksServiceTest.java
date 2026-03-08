package com.english.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.VocabularyBook;
import com.english.entity.Word;
import com.english.mapper.VocabularyBookMapper;
import com.english.mapper.WordMapper;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.UnitMapper;
import com.english.service.impl.VocabularyBooksServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        MockitoAnnotations.openMocks(this);
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
            vb.getReviewCount() == 0 &&
            vb.getRepetition() == 0 &&
            vb.getIntervalDays() == 0 &&
            vb.getEasinessFactor() == 2.5
        ));
    }
}
