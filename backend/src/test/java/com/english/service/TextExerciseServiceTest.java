package com.english.service;
import com.english.test.TestUtils;

import com.english.entity.TextExercise;
import com.english.entity.ExerciseOption;
import com.english.exception.BusinessException;
import com.english.mapper.TextExerciseMapper;
import com.english.mapper.ExerciseOptionMapper;
import com.english.service.impl.TextExerciseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.NoTransactionException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TextExerciseServiceImpl.
 *
 * Covers: create, getByTextId, update, delete, checkAnswer (Integer correctness),
 * and batch create with validation.
 */
@ExtendWith(MockitoExtension.class)
class TextExerciseServiceTest {

    @InjectMocks
    private TextExerciseServiceImpl textExerciseService;

    /**
     * The Impl class declares its own @Autowired TextExerciseMapper field,
     * AND extends ServiceImpl<TextExerciseMapper, TextExercise> which has baseMapper.
     * Mockito @InjectMocks injects this single mock into both fields (same type).
     */
    @Mock
    private TextExerciseMapper textExerciseMapper;

    @Mock
    private ExerciseOptionMapper exerciseOptionMapper;

    @BeforeEach
    void setUp() {
        TestUtils.injectBaseMapper(textExerciseService, textExerciseMapper);
    }

    // ==================== Create Exercise ====================

    @Test
    @DisplayName("createTextExercise - should return true when insert succeeds")
    void createTextExercise_success() {
        TextExercise exercise = buildExercise(1L, 100L, "What is the answer?", "CHOICE", 1);

        when(textExerciseMapper.insert(any(TextExercise.class))).thenReturn(1);

        boolean result = textExerciseService.createTextExercise(exercise);

        assertTrue(result);
        verify(textExerciseMapper, times(1)).insert(exercise);
    }

    @Test
    @DisplayName("createTextExercise - should return false when insert fails")
    void createTextExercise_insertFails_returnsFalse() {
        TextExercise exercise = buildExercise(null, 100L, "Question?", "CHOICE", 1);

        when(textExerciseMapper.insert(any(TextExercise.class))).thenReturn(0);

        boolean result = textExerciseService.createTextExercise(exercise);

        assertFalse(result);
    }

    // ==================== Get Exercises By Text ID ====================

    @Test
    @DisplayName("getExercisesWithOptions - should return exercises with their options")
    void getExercisesWithOptions_success() {
        Long textId = 100L;

        TextExercise ex1 = buildExercise(1L, textId, "Question 1", "CHOICE", 1);
        TextExercise ex2 = buildExercise(2L, textId, "Question 2", "FILL", 2);

        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(Arrays.asList(ex1, ex2));

        ExerciseOption opt1 = buildOption(10L, 1L, "Option A", "A", 1, 1);
        ExerciseOption opt2 = buildOption(11L, 1L, "Option B", "B", 0, 2);
        when(exerciseOptionMapper.getOptionsByExerciseId(1L)).thenReturn(Arrays.asList(opt1, opt2));
        when(exerciseOptionMapper.getOptionsByExerciseId(2L)).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = textExerciseService.getExercisesWithOptions(textId);

        assertEquals(2, result.size());

        Map<String, Object> first = result.get(0);
        assertEquals(1L, first.get("id"));
        assertEquals("Question 1", first.get("question"));
        assertEquals("CHOICE", first.get("exerciseType"));
        assertEquals(textId, first.get("textId"));
        @SuppressWarnings("unchecked")
        List<ExerciseOption> firstOptions = (List<ExerciseOption>) first.get("options");
        assertEquals(2, firstOptions.size());

        Map<String, Object> second = result.get(1);
        @SuppressWarnings("unchecked")
        List<ExerciseOption> secondOptions = (List<ExerciseOption>) second.get("options");
        assertEquals(0, secondOptions.size());
    }

    @Test
    @DisplayName("getExercisesWithOptions - should throw BusinessException for invalid textId")
    void getExercisesWithOptions_invalidTextId_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                textExerciseService.getExercisesWithOptions(null));

        assertThrows(BusinessException.class, () ->
                textExerciseService.getExercisesWithOptions(0L));

        assertThrows(BusinessException.class, () ->
                textExerciseService.getExercisesWithOptions(-1L));
    }

    @Test
    @DisplayName("getExercisesWithOptions - should return empty list when no exercises found")
    void getExercisesWithOptions_noExercises_returnsEmptyList() {
        Long textId = 999L;
        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(null);

        List<Map<String, Object>> result = textExerciseService.getExercisesWithOptions(textId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== Update Exercise ====================

    @Test
    @DisplayName("updateExercise - should return true when update succeeds")
    void updateExercise_success() {
        TextExercise exercise = buildExercise(1L, 100L, "Updated question", "CHOICE", 1);

        when(textExerciseMapper.updateById(any(TextExercise.class))).thenReturn(1);

        boolean result = textExerciseService.updateExercise(exercise);

        assertTrue(result);
        verify(textExerciseMapper, times(1)).updateById(exercise);
    }

    @Test
    @DisplayName("updateExercise - should throw NoTransactionException in unit test (no Spring transaction context)")
    void updateExercise_exceptionCaught_throwsNoTransactionException() {
        TextExercise exercise = buildExercise(1L, 100L, "Question", "CHOICE", 1);

        when(textExerciseMapper.updateById(any(TextExercise.class)))
                .thenThrow(new RuntimeException("DB error"));

        // In unit tests without Spring context, TransactionAspectSupport.currentTransactionStatus()
        // throws NoTransactionException because there is no active transaction
        assertThrows(NoTransactionException.class,
                () -> textExerciseService.updateExercise(exercise));
    }

    // ==================== Delete Exercise ====================

    @Test
    @DisplayName("deleteExercise - should delete options first then exercise")
    void deleteExercise_deletesOptionsAndExercise() {
        Long exerciseId = 1L;

        when(textExerciseMapper.deleteById(exerciseId)).thenReturn(1);

        boolean result = textExerciseService.deleteExercise(exerciseId);

        assertTrue(result);

        // Verify options deleted first, then exercise
        var inOrder = inOrder(exerciseOptionMapper, textExerciseMapper);
        inOrder.verify(exerciseOptionMapper).deleteOptionsByExerciseId(exerciseId);
        inOrder.verify(textExerciseMapper).deleteById(exerciseId);
    }

    @Test
    @DisplayName("deleteAllExercisesByTextId - should delete all exercises and their options")
    void deleteAllExercisesByTextId_success() {
        Long textId = 100L;

        TextExercise ex1 = buildExercise(1L, textId, "Q1", "CHOICE", 1);
        TextExercise ex2 = buildExercise(2L, textId, "Q2", "FILL", 2);
        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(Arrays.asList(ex1, ex2));
        when(textExerciseMapper.deleteById(anyLong())).thenReturn(1);

        boolean result = textExerciseService.deleteAllExercisesByTextId(textId);

        assertTrue(result);
        verify(exerciseOptionMapper).deleteOptionsByExerciseId(1L);
        verify(exerciseOptionMapper).deleteOptionsByExerciseId(2L);
        verify(textExerciseMapper).deleteById(1L);
        verify(textExerciseMapper).deleteById(2L);
    }

    @Test
    @DisplayName("deleteAllExercisesByTextId - should return true when no exercises exist")
    void deleteAllExercisesByTextId_noExercises_returnsTrue() {
        Long textId = 999L;
        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(null);

        boolean result = textExerciseService.deleteAllExercisesByTextId(textId);

        assertTrue(result);
        verify(exerciseOptionMapper, never()).deleteOptionsByExerciseId(anyLong());
        verify(textExerciseMapper, never()).deleteById(anyLong());
    }

    // ==================== checkAnswer - Integer.valueOf(1).equals() correctness (R43 fix) ====================

    @Test
    @DisplayName("checkAnswer - should match correct answer using Integer.valueOf(1).equals() (R43 fix)")
    void checkAnswer_correctAnswer_returnsTrue() {
        Long exerciseId = 1L;

        // Build options where isCorrect is Integer (not Boolean) per R43 fix
        ExerciseOption correctOption = buildOption(10L, exerciseId, "Apple", "A", 1, 1);
        ExerciseOption wrongOption = buildOption(11L, exerciseId, "Banana", "B", 0, 2);

        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Arrays.asList(correctOption, wrongOption));

        // Student answers "A" which matches the correct option's optionKey
        boolean result = textExerciseService.checkAnswer(exerciseId, "A");

        assertTrue(result, "Should return true when student answer matches correct option key");
    }

    @Test
    @DisplayName("checkAnswer - should be case-insensitive")
    void checkAnswer_caseInsensitive_returnsTrue() {
        Long exerciseId = 1L;

        ExerciseOption correctOption = buildOption(10L, exerciseId, "Apple", "A", 1, 1);
        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Collections.singletonList(correctOption));

        // Student answers "a" (lowercase) which should match "A" (uppercase)
        boolean result = textExerciseService.checkAnswer(exerciseId, "a");

        assertTrue(result, "checkAnswer should be case-insensitive");
    }

    @Test
    @DisplayName("checkAnswer - should return false for wrong answer")
    void checkAnswer_wrongAnswer_returnsFalse() {
        Long exerciseId = 1L;

        ExerciseOption correctOption = buildOption(10L, exerciseId, "Apple", "A", 1, 1);
        ExerciseOption wrongOption = buildOption(11L, exerciseId, "Banana", "B", 0, 2);

        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Arrays.asList(correctOption, wrongOption));

        boolean result = textExerciseService.checkAnswer(exerciseId, "B");

        assertFalse(result, "Should return false when student answer does not match correct option key");
    }

    @Test
    @DisplayName("checkAnswer - Integer isCorrect field must NOT use Boolean.TRUE.equals() (R43 regression guard)")
    void checkAnswer_integerIsCorrect_notBooleanComparison() {
        Long exerciseId = 1L;

        // The critical R43 fix: isCorrect is Integer (1/0), NOT Boolean (true/false).
        // Integer.valueOf(1).equals(option.getIsCorrect()) is the correct check.
        // Boolean.TRUE.equals(Integer.valueOf(1)) would return false!
        ExerciseOption option = new ExerciseOption();
        option.setId(10L);
        option.setExerciseId(exerciseId);
        option.setOptionKey("A");
        option.setIsCorrect(Integer.valueOf(1)); // Integer, not Boolean
        option.setOrderNum(1);

        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Collections.singletonList(option));

        // This MUST return true. If the impl used Boolean.TRUE.equals(option.getIsCorrect()),
        // it would incorrectly return false because Boolean.TRUE.equals(Integer(1)) == false.
        boolean result = textExerciseService.checkAnswer(exerciseId, "A");

        assertTrue(result, "Integer.valueOf(1).equals() must be used for isCorrect, not Boolean.TRUE.equals()");
    }

    @Test
    @DisplayName("checkAnswer - should return false for null answer")
    void checkAnswer_nullAnswer_returnsFalse() {
        boolean result = textExerciseService.checkAnswer(1L, null);

        assertFalse(result);
        // Should not even query the database
        verify(exerciseOptionMapper, never()).getOptionsByExerciseId(anyLong());
    }

    @Test
    @DisplayName("checkAnswer - should return false for invalid exerciseId")
    void checkAnswer_invalidExerciseId_returnsFalse() {
        assertFalse(textExerciseService.checkAnswer(null, "A"));
        assertFalse(textExerciseService.checkAnswer(0L, "A"));
        assertFalse(textExerciseService.checkAnswer(-1L, "A"));
    }

    @Test
    @DisplayName("checkAnswer - should return false when no options exist")
    void checkAnswer_noOptions_returnsFalse() {
        Long exerciseId = 1L;

        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Collections.emptyList());

        boolean result = textExerciseService.checkAnswer(exerciseId, "A");

        assertFalse(result);
    }

    // ==================== Batch Create Exercises ====================

    @Test
    @DisplayName("batchCreateExercises - should throw BusinessException when sizes mismatch")
    void batchCreateExercises_sizesMismatch_throwsBusinessException() {
        TextExercise ex1 = buildExercise(null, 100L, "Q1", "CHOICE", 1);
        List<TextExercise> exercises = Collections.singletonList(ex1);

        // 2 option lists for 1 exercise = mismatch
        List<List<ExerciseOption>> options = Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList()
        );

        BusinessException exception = assertThrows(BusinessException.class, () ->
                textExerciseService.batchCreateExercises(exercises, options));

        assertTrue(exception.getMessage().contains("不匹配"));
    }

    @Test
    @DisplayName("batchCreateExercises - should return false for null exercises list")
    void batchCreateExercises_nullExercises_returnsFalse() {
        boolean result = textExerciseService.batchCreateExercises(null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("batchCreateExercises - should return false for empty exercises list")
    void batchCreateExercises_emptyExercises_returnsFalse() {
        boolean result = textExerciseService.batchCreateExercises(Collections.emptyList(), Collections.emptyList());

        assertFalse(result);
    }

    // ==================== getExerciseDetail ====================

    @Test
    @DisplayName("getExerciseDetail - should return exercise with options")
    void getExerciseDetail_success() {
        Long exerciseId = 1L;
        TextExercise exercise = buildExercise(exerciseId, 100L, "Question?", "CHOICE", 1);

        when(textExerciseMapper.selectById(exerciseId)).thenReturn(exercise);

        ExerciseOption opt = buildOption(10L, exerciseId, "Option A", "A", 1, 1);
        when(exerciseOptionMapper.getOptionsByExerciseId(exerciseId))
                .thenReturn(Collections.singletonList(opt));

        Map<String, Object> result = textExerciseService.getExerciseDetail(exerciseId);

        assertNotNull(result);
        assertEquals(exerciseId, result.get("id"));
        assertEquals("Question?", result.get("question"));
        assertEquals(100L, result.get("textId"));

        @SuppressWarnings("unchecked")
        List<ExerciseOption> options = (List<ExerciseOption>) result.get("options");
        assertEquals(1, options.size());
    }

    @Test
    @DisplayName("getExerciseDetail - should return null when exercise not found")
    void getExerciseDetail_notFound_returnsNull() {
        Long exerciseId = 999L;

        when(textExerciseMapper.selectById(exerciseId)).thenReturn(null);

        Map<String, Object> result = textExerciseService.getExerciseDetail(exerciseId);

        assertNull(result);
    }

    @Test
    @DisplayName("getExerciseDetail - should throw BusinessException for invalid exerciseId")
    void getExerciseDetail_invalidId_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                textExerciseService.getExerciseDetail(null));

        assertThrows(BusinessException.class, () ->
                textExerciseService.getExerciseDetail(0L));
    }

    // ==================== getExerciseIdsByTextId ====================

    @Test
    @DisplayName("getExerciseIdsByTextId - should return list of exercise IDs")
    void getExerciseIdsByTextId_success() {
        Long textId = 100L;
        TextExercise ex1 = buildExercise(1L, textId, "Q1", "CHOICE", 1);
        TextExercise ex2 = buildExercise(2L, textId, "Q2", "FILL", 2);

        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(Arrays.asList(ex1, ex2));

        List<Long> ids = textExerciseService.getExerciseIdsByTextId(textId);

        assertEquals(2, ids.size());
        assertEquals(1L, ids.get(0));
        assertEquals(2L, ids.get(1));
    }

    @Test
    @DisplayName("getExerciseIdsByTextId - should return empty list when no exercises exist")
    void getExerciseIdsByTextId_noExercises_returnsEmptyList() {
        Long textId = 999L;
        when(textExerciseMapper.getExercisesByTextId(textId)).thenReturn(null);

        List<Long> ids = textExerciseService.getExerciseIdsByTextId(textId);

        assertNotNull(ids);
        assertTrue(ids.isEmpty());
    }

    // ==================== Helper Methods ====================

    private TextExercise buildExercise(Long id, Long textId, String question, String type, int orderNum) {
        TextExercise exercise = new TextExercise();
        exercise.setId(id);
        exercise.setTextId(textId);
        exercise.setQuestion(question);
        exercise.setExerciseType(type);
        exercise.setOrderNum(orderNum);
        exercise.setCreatedBy(1L);
        exercise.setCreatedTime(LocalDateTime.now());
        return exercise;
    }

    private ExerciseOption buildOption(Long id, Long exerciseId, String content, String key,
                                       int isCorrect, int orderNum) {
        ExerciseOption option = new ExerciseOption();
        option.setId(id);
        option.setExerciseId(exerciseId);
        option.setOptionContent(content);
        option.setOptionKey(key);
        option.setIsCorrect(isCorrect);
        option.setOrderNum(orderNum);
        return option;
    }
}
