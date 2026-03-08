package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.VocabularyBook;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class VocabularyBooksServiceSM2Test {

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

    // 测试：easinessFactor 过低时被限制到 1.3
    @Test
    public void testUpdateMastery_easinessFactorClamped_tooLow() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 0.5); // 远低于最小值 1.3

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 easinessFactor 被限制到最小值 1.3
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getEasinessFactor() == 1.3 &&
            book.getMasteryLevel().equals("LEARNING")
        ));
    }

    // 测试：easinessFactor 过高时被限制到 5.0
    @Test
    public void testUpdateMastery_easinessFactorClamped_tooHigh() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "MASTERED";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 9.5); // 远高于最大值 5.0

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 easinessFactor 被限制到最大值 5.0
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getEasinessFactor() == 5.0 &&
            book.getMasteryLevel().equals("MASTERED")
        ));
    }

    // 测试：easinessFactor 在有效范围内时保持原值
    @Test
    public void testUpdateMastery_easinessFactorInRange_preservesValue() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 2.6); // 在 [1.3, 5.0] 范围内

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 easinessFactor 保持原值
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getEasinessFactor() == 2.6
        ));
    }

    // 测试：负数 repetition 被限制到 0
    @Test
    public void testUpdateMastery_negativeRepetition_clampedToZero() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("repetition", -5); // 负数应被限制到 0

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setRepetition(2);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 repetition 被限制到 0
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getRepetition() == 0
        ));
    }

    // 测试：正数 repetition 保持原值
    @Test
    public void testUpdateMastery_positiveRepetition_preservesValue() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("repetition", 5);

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setRepetition(0);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 repetition 保持原值
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getRepetition() == 5
        ));
    }

    // 测试：负数 interval 被限制到 0
    @Test
    public void testUpdateMastery_negativeInterval_clampedToZero() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("interval", -10); // 负数应被限制到 0

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setIntervalDays(5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 interval 被限制到 0
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getIntervalDays() == 0
        ));
    }

    // 测试：正数 interval 保持原值
    @Test
    public void testUpdateMastery_positiveInterval_preservesValue() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("interval", 10);

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setIntervalDays(0);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, sm2Params);

        // 验证 interval 保持原值
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getIntervalDays() == 10
        ));
    }

    // 测试：单词不在生词本中时不崩溃
    @Test
    public void testUpdateMastery_nullVocabularyBook_noCrash() {
        Long studentId = 1L;
        Long wordId = 100L;

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 2.5);
        sm2Params.put("repetition", 3);
        sm2Params.put("interval", 5);

        // 模拟生词本不存在
        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        // 应该不抛出任何异常
        assertDoesNotThrow(() -> {
            vocabularyBooksService.updateMasteryLevel(studentId, wordId, "LEARNING", sm2Params);
        });

        // 验证不尝试更新
        verify(vocabularyBookMapper, never()).updateById(any(VocabularyBook.class));
    }

    // 测试：SM2 参数为 null 时的处理
    @Test
    public void testUpdateMastery_nullSM2Params_handledGracefully() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "MASTERED";

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        // 传递 null SM2 参数
        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, null);

        // 验证掌握等级和复习次数仍被更新
        assertEquals("MASTERED", vb.getMasteryLevel());
        assertEquals(6, vb.getReviewCount());
        verify(vocabularyBookMapper, times(1)).updateById(vb);
    }

    // 测试：SM2 参数为空 Map 时的处理
    @Test
    public void testUpdateMastery_emptySM2Params_onlyUpdatesMasteryLevel() {
        Long studentId = 1L;
        Long wordId = 100L;
        String masteryLevel = "LEARNING";

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setRepetition(0);
        vb.setIntervalDays(0);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        // 传递空 Map
        vocabularyBooksService.updateMasteryLevel(studentId, wordId, masteryLevel, new HashMap<>());

        // 验证只更新了掌握等级和复习次数
        assertEquals("LEARNING", vb.getMasteryLevel());
        assertEquals(6, vb.getReviewCount());
        // SM2 参数应保持原值
        assertEquals(0, vb.getRepetition());
        assertEquals(0, vb.getIntervalDays());
        assertEquals(2.5, vb.getEasinessFactor());
        verify(vocabularyBookMapper, times(1)).updateById(vb);
    }

    // 测试：边界值 easinessFactor = 1.3（最小值）
    @Test
    public void testUpdateMastery_easinessFactorBoundary_minValue() {
        Long studentId = 1L;
        Long wordId = 100L;

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 1.3); // 恰好等于最小值

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, "LEARNING", sm2Params);

        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getEasinessFactor() == 1.3
        ));
    }

    // 测试：边界值 easinessFactor = 5.0（最大值）
    @Test
    public void testUpdateMastery_easinessFactorBoundary_maxValue() {
        Long studentId = 1L;
        Long wordId = 100L;

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("easinessFactor", 5.0); // 恰好等于最大值

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, "MASTERED", sm2Params);

        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getEasinessFactor() == 5.0
        ));
    }

    // 测试：所有 SM2 参数同时更新，确保相互独立
    @Test
    public void testUpdateMastery_allSM2ParamsUpdated_independently() {
        Long studentId = 1L;
        Long wordId = 100L;

        Map<String, Object> sm2Params = new HashMap<>();
        sm2Params.put("repetition", 8);
        sm2Params.put("interval", 20);
        sm2Params.put("easinessFactor", 2.8);

        VocabularyBook vb = new VocabularyBook();
        vb.setId(1L);
        vb.setStudentId(studentId);
        vb.setWordId(wordId);
        vb.setReviewCount(5);
        vb.setRepetition(0);
        vb.setIntervalDays(0);
        vb.setEasinessFactor(2.5);

        when(vocabularyBookMapper.selectOne(any(QueryWrapper.class))).thenReturn(vb);
        when(vocabularyBookMapper.updateById(any(VocabularyBook.class))).thenReturn(1);

        vocabularyBooksService.updateMasteryLevel(studentId, wordId, "LEARNING", sm2Params);

        // 验证所有参数都被正确更新
        verify(vocabularyBookMapper, times(1)).updateById(argThat(book ->
            book.getRepetition() == 8 &&
            book.getIntervalDays() == 20 &&
            book.getEasinessFactor() == 2.8
        ));
    }
}
