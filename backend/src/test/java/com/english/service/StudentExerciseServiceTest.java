package com.english.service;

import com.english.test.TestUtils;
import com.english.entity.StudentExerciseAnswer;
import com.english.exception.BusinessException;
import com.english.mapper.ExerciseOptionMapper;
import com.english.mapper.StudentExerciseAnswerMapper;
import com.english.service.impl.StudentExerciseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StudentExerciseServiceImpl.
 *
 * Key observations from source code:
 * - StudentExerciseServiceImpl extends ServiceImpl<StudentExerciseAnswerMapper, StudentExerciseAnswer>
 * - Injects StudentExerciseAnswerMapper, ExerciseOptionMapper, TextExerciseService
 * - submitAnswer() validates input, checks answer via textExerciseService.checkAnswer(),
 *   then upserts (update existing or insert new)
 * - getStudentAnswersByText/Assignment wrap mapper calls in try-catch, return emptyList on error
 * - getStudentAnswerStats() computes accuracy from mapper stats, returns safe defaults on error/empty
 * - hasCompletedTextExercises() uses getExerciseCount and distinct exerciseId count
 * - getTextExerciseProgress() computes progress percentage, accuracy, and completion status
 */
@ExtendWith(MockitoExtension.class)
class StudentExerciseServiceTest {

    @InjectMocks
    private StudentExerciseServiceImpl studentExerciseService;

    @Mock
    private StudentExerciseAnswerMapper studentExerciseAnswerMapper;

    @Mock
    private ExerciseOptionMapper exerciseOptionMapper;

    @Mock
    private TextExerciseService textExerciseService;

    private StudentExerciseAnswer testAnswer;

    @BeforeEach
    void setUp() {
        TestUtils.injectBaseMapper(studentExerciseService, studentExerciseAnswerMapper);
        testAnswer = new StudentExerciseAnswer();
        testAnswer.setId(1L);
        testAnswer.setStudentId(100L);
        testAnswer.setExerciseId(200L);
        testAnswer.setAssignmentId(300L);
        testAnswer.setAnswerContent("A");
        testAnswer.setIsCorrect(true);
        testAnswer.setAnswerTime(LocalDateTime.now());
        testAnswer.setTimeSpentSeconds(30);
    }

    // ==================== submitAnswer tests ====================

    @Test
    void submitAnswer_newAnswer_insertsAndReturnsCorrect() {
        // Arrange
        StudentExerciseAnswer newAnswer = new StudentExerciseAnswer();
        newAnswer.setStudentId(100L);
        newAnswer.setExerciseId(200L);
        newAnswer.setAssignmentId(300L);
        newAnswer.setAnswerContent("A");

        when(textExerciseService.checkAnswer(200L, "A")).thenReturn(true);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(null);
        when(studentExerciseAnswerMapper.insert(any(StudentExerciseAnswer.class))).thenReturn(1);

        // Act
        Map<String, Object> result = studentExerciseService.submitAnswer(newAnswer);

        // Assert
        assertTrue((Boolean) result.get("isCorrect"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("回答正确！", result.get("message"));
        assertNotNull(newAnswer.getAnswerTime());
        verify(studentExerciseAnswerMapper).insert(any(StudentExerciseAnswer.class));
    }

    @Test
    void submitAnswer_existingAnswer_updatesRecord() {
        // Arrange
        StudentExerciseAnswer newAnswer = new StudentExerciseAnswer();
        newAnswer.setStudentId(100L);
        newAnswer.setExerciseId(200L);
        newAnswer.setAssignmentId(300L);
        newAnswer.setAnswerContent("B");

        StudentExerciseAnswer existing = new StudentExerciseAnswer();
        existing.setId(5L);

        when(textExerciseService.checkAnswer(200L, "B")).thenReturn(false);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(existing);
        when(studentExerciseAnswerMapper.updateById(any(StudentExerciseAnswer.class))).thenReturn(1);

        // Act
        Map<String, Object> result = studentExerciseService.submitAnswer(newAnswer);

        // Assert
        assertFalse((Boolean) result.get("isCorrect"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("回答错误，请继续努力！", result.get("message"));
        assertEquals(5L, newAnswer.getId()); // ID set from existing record
        verify(studentExerciseAnswerMapper).updateById(any(StudentExerciseAnswer.class));
        verify(studentExerciseAnswerMapper, never()).insert(any(StudentExerciseAnswer.class));
    }

    @Test
    void submitAnswer_nullAnswer_throwsBusinessException() {
        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> studentExerciseService.submitAnswer(null));
        assertEquals("答案对象不能为空", exception.getMessage());
    }

    @Test
    void submitAnswer_nullStudentId_throwsBusinessException() {
        // Arrange
        StudentExerciseAnswer answer = new StudentExerciseAnswer();
        answer.setStudentId(null);
        answer.setExerciseId(200L);
        answer.setAnswerContent("A");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> studentExerciseService.submitAnswer(answer));
        assertEquals("学生ID不能为空", exception.getMessage());
    }

    @Test
    void submitAnswer_nullExerciseId_throwsBusinessException() {
        // Arrange
        StudentExerciseAnswer answer = new StudentExerciseAnswer();
        answer.setStudentId(100L);
        answer.setExerciseId(null);
        answer.setAnswerContent("A");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> studentExerciseService.submitAnswer(answer));
        assertEquals("习题ID不能为空", exception.getMessage());
    }

    @Test
    void submitAnswer_nullAnswerContent_throwsBusinessException() {
        // Arrange
        StudentExerciseAnswer answer = new StudentExerciseAnswer();
        answer.setStudentId(100L);
        answer.setExerciseId(200L);
        answer.setAnswerContent(null);

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> studentExerciseService.submitAnswer(answer));
        assertEquals("答案内容不能为空", exception.getMessage());
    }

    @Test
    void submitAnswer_emptyAnswerContent_throwsBusinessException() {
        // Arrange
        StudentExerciseAnswer answer = new StudentExerciseAnswer();
        answer.setStudentId(100L);
        answer.setExerciseId(200L);
        answer.setAnswerContent("   ");

        // Act & Assert
        BusinessException exception = assertThrows(BusinessException.class,
                () -> studentExerciseService.submitAnswer(answer));
        assertEquals("答案内容不能为空", exception.getMessage());
    }

    @Test
    void submitAnswer_setsAnswerTimeIfNull() {
        // Arrange
        StudentExerciseAnswer answer = new StudentExerciseAnswer();
        answer.setStudentId(100L);
        answer.setExerciseId(200L);
        answer.setAssignmentId(300L);
        answer.setAnswerContent("A");
        answer.setAnswerTime(null); // explicitly null

        when(textExerciseService.checkAnswer(200L, "A")).thenReturn(true);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(null);
        when(studentExerciseAnswerMapper.insert(any(StudentExerciseAnswer.class))).thenReturn(1);

        // Act
        studentExerciseService.submitAnswer(answer);

        // Assert: answerTime should be set automatically
        assertNotNull(answer.getAnswerTime());
    }

    // ==================== getStudentAnswersByText tests ====================

    @Test
    void getStudentAnswersByText_returnsAnswers() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(testAnswer));

        // Act
        List<StudentExerciseAnswer> result = studentExerciseService.getStudentAnswersByText(100L, 50L, 300L);

        // Assert
        assertEquals(1, result.size());
        assertEquals(200L, result.get(0).getExerciseId());
    }

    @Test
    void getStudentAnswersByText_exception_returnsEmptyList() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswersByTextId(anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("DB error"));

        // Act
        List<StudentExerciseAnswer> result = studentExerciseService.getStudentAnswersByText(100L, 50L, 300L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getStudentAnswersByAssignment tests ====================

    @Test
    void getStudentAnswersByAssignment_returnsAnswers() {
        // Arrange: note parameter order in mapper is (assignmentId, studentId)
        when(studentExerciseAnswerMapper.getAnswersByAssignmentId(300L, 100L))
                .thenReturn(List.of(testAnswer));

        // Act
        List<StudentExerciseAnswer> result = studentExerciseService.getStudentAnswersByAssignment(100L, 300L);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    void getStudentAnswersByAssignment_exception_returnsEmptyList() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswersByAssignmentId(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("DB error"));

        // Act
        List<StudentExerciseAnswer> result = studentExerciseService.getStudentAnswersByAssignment(100L, 300L);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== getStudentAnswerStats tests ====================

    @Test
    void getStudentAnswerStats_withData_computesCorrectly() {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 10);
        stats.put("correct", 7);

        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(stats);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(10, result.get("totalAnswers"));
        assertEquals(7, result.get("correctAnswers"));
        assertEquals(3, result.get("incorrectAnswers")); // total - correct
        assertEquals(70.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_nullStats_returnsEmptyDefaults() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(null);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(0, result.get("totalAnswers"));
        assertEquals(0, result.get("correctAnswers"));
        assertEquals(0, result.get("incorrectAnswers"));
        assertEquals(0.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_emptyMap_returnsEmptyDefaults() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(new HashMap<>());

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(0, result.get("totalAnswers"));
        assertEquals(0.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_zeroTotal_accuracyZero() {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("correct", 0);

        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(stats);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(0, result.get("totalAnswers"));
        assertEquals(0.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_exception_returnsSafeDefaults() {
        // Arrange
        when(studentExerciseAnswerMapper.getAnswerStats(anyLong(), anyLong()))
                .thenThrow(new RuntimeException("DB error"));

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(0, result.get("totalAnswers"));
        assertEquals(0, result.get("correctAnswers"));
        assertEquals(0, result.get("incorrectAnswers"));
        assertEquals(0.0, result.get("accuracy"));
    }

    // ==================== hasCompletedTextExercises tests ====================

    @Test
    void hasCompletedTextExercises_allAnswered_returnsTrue() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(2);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(2L);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2));

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasCompletedTextExercises_partiallyAnswered_returnsFalse() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(3);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1));

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertFalse(result);
    }

    @Test
    void hasCompletedTextExercises_zeroExercises_returnsTrue() {
        // Arrange: no exercises means completed
        when(textExerciseService.getExerciseCount(50L)).thenReturn(0);

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasCompletedTextExercises_duplicateExerciseIds_deduplicates() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(2);

        // Two answers for the same exerciseId
        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(1L); // duplicate

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2));

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert: distinct count is 1, need 2 -> not completed
        assertFalse(result);
    }

    @Test
    void hasCompletedTextExercises_exception_returnsFalse() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenThrow(new RuntimeException("error"));

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertFalse(result);
    }

    @Test
    void hasCompletedTextExercises_nullAnswers_returnsFalse() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(2);
        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L)).thenReturn(null);

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert: null answers -> distinct count = 0, need 2 -> false
        assertFalse(result);
    }

    // ==================== getTextExerciseProgress tests ====================

    @Test
    void getTextExerciseProgress_zeroExercises_returns100Progress() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(0);

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(0, result.get("totalExercises"));
        assertEquals(0, result.get("completedExercises"));
        assertEquals(100, result.get("progress"));
        assertTrue((Boolean) result.get("isCompleted"));
    }

    @Test
    void getTextExerciseProgress_allCorrect_returnsFullProgress() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(2);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        ans1.setIsCorrect(true);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(2L);
        ans2.setIsCorrect(true);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2));

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(2, result.get("totalExercises"));
        assertEquals(2, result.get("completedExercises"));
        assertEquals(2, result.get("correctAnswers"));
        assertTrue((Boolean) result.get("isCompleted"));
        assertEquals(100.0, result.get("progress"));
        assertEquals(100.0, result.get("accuracy"));
    }

    @Test
    void getTextExerciseProgress_partialProgress_computesCorrectly() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(4);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        ans1.setIsCorrect(true);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(2L);
        ans2.setIsCorrect(false);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2));

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(4, result.get("totalExercises"));
        assertEquals(2, result.get("completedExercises"));
        assertEquals(1, result.get("correctAnswers"));
        assertFalse((Boolean) result.get("isCompleted"));
        assertEquals(50.0, result.get("progress")); // 2/4 * 100
        assertEquals(50.0, result.get("accuracy")); // 1/2 * 100
    }

    @Test
    void getTextExerciseProgress_duplicateExerciseIds_deduplicatesLatest() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(1);

        // Two submissions for same exercise, latest one overwrites
        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        ans1.setIsCorrect(false);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(1L);
        ans2.setIsCorrect(true); // This overwrites ans1 (LinkedHashMap.put)

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2));

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert: only 1 distinct exercise
        assertEquals(1, result.get("completedExercises"));
        assertEquals(1, result.get("correctAnswers")); // latest answer (true) wins
        assertTrue((Boolean) result.get("isCompleted"));
    }

    @Test
    void getTextExerciseProgress_exception_returnsSafeDefaults() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenThrow(new RuntimeException("error"));

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(0, result.get("totalExercises"));
        assertEquals(0, result.get("completedExercises"));
        assertEquals(0, result.get("progress"));
        assertFalse((Boolean) result.get("isCompleted"));
        assertEquals(0.0, result.get("accuracy"));
    }

    @Test
    void getTextExerciseProgress_nullAnswers_returnsZeroProgress() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(3);
        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L)).thenReturn(null);

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(3, result.get("totalExercises"));
        assertEquals(0, result.get("completedExercises"));
        assertFalse((Boolean) result.get("isCompleted"));
        assertEquals(0.0, result.get("accuracy"));
    }

    // ==================== submitAnswer - additional edge cases ====================

    @Test
    void submitAnswer_updateExistingAnswer_updatesAnswerTime() {
        // Arrange: answer already exists and will be updated
        StudentExerciseAnswer newAnswer = new StudentExerciseAnswer();
        newAnswer.setStudentId(100L);
        newAnswer.setExerciseId(200L);
        newAnswer.setAssignmentId(300L);
        newAnswer.setAnswerContent("B");
        newAnswer.setAnswerTime(null);

        StudentExerciseAnswer existing = new StudentExerciseAnswer();
        existing.setId(5L);
        existing.setAnswerTime(LocalDateTime.now().minusHours(1));

        when(textExerciseService.checkAnswer(200L, "B")).thenReturn(false);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(existing);
        when(studentExerciseAnswerMapper.updateById(any(StudentExerciseAnswer.class))).thenReturn(1);

        LocalDateTime beforeCall = LocalDateTime.now();

        // Act
        Map<String, Object> result = studentExerciseService.submitAnswer(newAnswer);

        LocalDateTime afterCall = LocalDateTime.now();

        // Assert
        assertTrue((Boolean) result.get("success"));
        assertNotNull(newAnswer.getAnswerTime());
        assertTrue(newAnswer.getAnswerTime().isAfter(beforeCall.minusSeconds(1)));
        assertTrue(newAnswer.getAnswerTime().isBefore(afterCall.plusSeconds(1)));
    }

    @Test
    void submitAnswer_insertFailure_returnsFalseSuccess() {
        // Arrange
        StudentExerciseAnswer newAnswer = new StudentExerciseAnswer();
        newAnswer.setStudentId(100L);
        newAnswer.setExerciseId(200L);
        newAnswer.setAssignmentId(300L);
        newAnswer.setAnswerContent("A");

        when(textExerciseService.checkAnswer(200L, "A")).thenReturn(true);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(null);
        when(studentExerciseAnswerMapper.insert(any(StudentExerciseAnswer.class))).thenReturn(0); // Insert fails

        // Act
        Map<String, Object> result = studentExerciseService.submitAnswer(newAnswer);

        // Assert
        assertFalse((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("isCorrect"));
    }

    @Test
    void submitAnswer_updateFailure_returnsFalseSuccess() {
        // Arrange
        StudentExerciseAnswer newAnswer = new StudentExerciseAnswer();
        newAnswer.setStudentId(100L);
        newAnswer.setExerciseId(200L);
        newAnswer.setAssignmentId(300L);
        newAnswer.setAnswerContent("A");

        StudentExerciseAnswer existing = new StudentExerciseAnswer();
        existing.setId(5L);

        when(textExerciseService.checkAnswer(200L, "A")).thenReturn(true);
        when(studentExerciseAnswerMapper.getAnswer(100L, 200L, 300L)).thenReturn(existing);
        when(studentExerciseAnswerMapper.updateById(any(StudentExerciseAnswer.class))).thenReturn(0); // Update fails

        // Act
        Map<String, Object> result = studentExerciseService.submitAnswer(newAnswer);

        // Assert
        assertFalse((Boolean) result.get("success"));
        assertTrue((Boolean) result.get("isCorrect"));
    }

    // ==================== getStudentAnswerStats - additional boundary tests ====================

    @Test
    void getStudentAnswerStats_allCorrect_100Accuracy() {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 5);
        stats.put("correct", 5);

        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(stats);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(5, result.get("totalAnswers"));
        assertEquals(5, result.get("correctAnswers"));
        assertEquals(0, result.get("incorrectAnswers"));
        assertEquals(100.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_allWrong_0Accuracy() {
        // Arrange
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 5);
        stats.put("correct", 0);

        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(stats);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(5, result.get("totalAnswers"));
        assertEquals(0, result.get("correctAnswers"));
        assertEquals(5, result.get("incorrectAnswers"));
        assertEquals(0.0, result.get("accuracy"));
    }

    @Test
    void getStudentAnswerStats_partialAccuracy_calculatesCorrectly() {
        // Arrange: 6 correct out of 10 = 60% accuracy
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 10);
        stats.put("correct", 6);

        when(studentExerciseAnswerMapper.getAnswerStats(300L, 100L)).thenReturn(stats);

        // Act
        Map<String, Object> result = studentExerciseService.getStudentAnswerStats(100L, 300L);

        // Assert
        assertEquals(10, result.get("totalAnswers"));
        assertEquals(6, result.get("correctAnswers"));
        assertEquals(4, result.get("incorrectAnswers"));
        assertEquals(60.0, result.get("accuracy"));
    }

    // ==================== hasCompletedTextExercises - boundary tests ====================

    @Test
    void hasCompletedTextExercises_emptyAnswerList_returnsFalse() {
        // Arrange
        when(textExerciseService.getExerciseCount(50L)).thenReturn(2);
        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(Collections.emptyList());

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertFalse(result);
    }

    @Test
    void hasCompletedTextExercises_exactlyMatches_returnsTrue() {
        // Arrange: number of distinct exercises matches total exercises
        when(textExerciseService.getExerciseCount(50L)).thenReturn(3);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(2L);
        StudentExerciseAnswer ans3 = new StudentExerciseAnswer();
        ans3.setExerciseId(3L);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2, ans3));

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert
        assertTrue(result);
    }

    @Test
    void hasCompletedTextExercises_manyDuplicates_deduplicatesCorrectly() {
        // Arrange: 10 answers but only 2 distinct exercises
        when(textExerciseService.getExerciseCount(50L)).thenReturn(3);

        List<StudentExerciseAnswer> answers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            StudentExerciseAnswer ans = new StudentExerciseAnswer();
            ans.setExerciseId(1L);
            answers.add(ans);
        }
        for (int i = 0; i < 5; i++) {
            StudentExerciseAnswer ans = new StudentExerciseAnswer();
            ans.setExerciseId(2L);
            answers.add(ans);
        }

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(answers);

        // Act
        boolean result = studentExerciseService.hasCompletedTextExercises(100L, 50L, 300L);

        // Assert: 2 distinct exercises < 3 required
        assertFalse(result);
    }

    // ==================== getTextExerciseProgress - detailed statistics ====================

    @Test
    void getTextExerciseProgress_mixedCorrectIncorrect_calculatesStats() {
        // Arrange: 5 total, 3 answered (2 correct, 1 incorrect)
        when(textExerciseService.getExerciseCount(50L)).thenReturn(5);

        StudentExerciseAnswer ans1 = new StudentExerciseAnswer();
        ans1.setExerciseId(1L);
        ans1.setIsCorrect(true);
        StudentExerciseAnswer ans2 = new StudentExerciseAnswer();
        ans2.setExerciseId(2L);
        ans2.setIsCorrect(false);
        StudentExerciseAnswer ans3 = new StudentExerciseAnswer();
        ans3.setExerciseId(3L);
        ans3.setIsCorrect(true);

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(List.of(ans1, ans2, ans3));

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(5, result.get("totalExercises"));
        assertEquals(3, result.get("completedExercises"));
        assertEquals(2, result.get("correctAnswers"));
        assertFalse((Boolean) result.get("isCompleted")); // 3/5 != 5/5
        assertEquals(60.0, result.get("progress")); // 3/5 * 100
        assertEquals(66.67, Math.round((Double) result.get("accuracy") * 100.0) / 100.0); // 2/3 * 100 ≈ 66.67
    }

    @Test
    void getTextExerciseProgress_largeDataset_handlesCorrectly() {
        // Arrange: 1000 total exercises, 500 answered (400 correct)
        when(textExerciseService.getExerciseCount(50L)).thenReturn(1000);

        List<StudentExerciseAnswer> answers = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            StudentExerciseAnswer ans = new StudentExerciseAnswer();
            ans.setExerciseId((long)i);
            ans.setIsCorrect(i <= 400); // First 400 correct, rest incorrect
            answers.add(ans);
        }

        when(studentExerciseAnswerMapper.getAnswersByTextId(100L, 50L, 300L))
                .thenReturn(answers);

        // Act
        Map<String, Object> result = studentExerciseService.getTextExerciseProgress(100L, 50L, 300L);

        // Assert
        assertEquals(1000, result.get("totalExercises"));
        assertEquals(500, result.get("completedExercises"));
        assertEquals(400, result.get("correctAnswers"));
        assertFalse((Boolean) result.get("isCompleted"));
        assertEquals(50.0, result.get("progress")); // 500/1000 * 100
        assertEquals(80.0, result.get("accuracy")); // 400/500 * 100
    }
}
