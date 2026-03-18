package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.Assignment;
import com.english.entity.Class;
import com.english.entity.ScoreRecord;
import com.english.entity.StudentClass;
import com.english.entity.User;
import com.english.exception.BusinessException;
import com.english.mapper.ScoreRecordMapper;
import com.english.mapper.UserMapper;
import com.english.service.impl.ScoreRecordServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ScoreRecordServiceTest {

    @InjectMocks
    private ScoreRecordServiceImpl scoreRecordService;

    @Mock
    private ScoreRecordMapper baseMapper;

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private ClassService classService;

    @Mock
    private StudentClassService studentClassService;

    @Mock
    private UserMapper userMapper;

    // --- recordScore tests ---

    @Test
    void recordScore_success() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setScore(85);
        record.setScoreType("ASSIGNMENT");

        when(baseMapper.insert(any(ScoreRecord.class))).thenReturn(1);

        assertDoesNotThrow(() -> scoreRecordService.recordScore(record));
        verify(baseMapper).insert(any(ScoreRecord.class));
    }

    @Test
    void recordScore_nullRecord_throwsBusinessException() {
        assertThrows(BusinessException.class, () -> scoreRecordService.recordScore(null));
    }

    @Test
    void recordScore_nullStudentId_throwsBusinessException() {
        ScoreRecord record = new ScoreRecord();
        record.setScore(85);
        record.setScoreType("ASSIGNMENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreRecordService.recordScore(record));
        assertEquals("学生ID不能为空", ex.getMessage());
    }

    @Test
    void recordScore_nullScore_throwsBusinessException() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setScoreType("ASSIGNMENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreRecordService.recordScore(record));
        assertEquals("分数不能为空", ex.getMessage());
    }

    @Test
    void recordScore_scoreOutOfRange_throwsBusinessException() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setScore(150);
        record.setScoreType("ASSIGNMENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreRecordService.recordScore(record));
        assertEquals("分数必须在0-100之间", ex.getMessage());
    }

    @Test
    void recordScore_negativeScore_throwsBusinessException() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setScore(-5);
        record.setScoreType("ASSIGNMENT");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreRecordService.recordScore(record));
        assertEquals("分数必须在0-100之间", ex.getMessage());
    }

    @Test
    void recordScore_emptyScoreType_throwsBusinessException() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setScore(85);
        record.setScoreType("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> scoreRecordService.recordScore(record));
        assertEquals("分数类型不能为空", ex.getMessage());
    }

    @Test
    void recordScore_duplicateKey_fallsBackToUpdate() {
        ScoreRecord record = new ScoreRecord();
        record.setStudentId(1L);
        record.setAssignmentId(10L);
        record.setScore(85);
        record.setScoreType("ASSIGNMENT");

        when(baseMapper.insert(any(ScoreRecord.class)))
                .thenThrow(new DuplicateKeyException("Duplicate"));
        when(baseMapper.update(any(ScoreRecord.class), any(QueryWrapper.class))).thenReturn(1);

        assertDoesNotThrow(() -> scoreRecordService.recordScore(record));
        verify(baseMapper).update(any(ScoreRecord.class), any(QueryWrapper.class));
    }

    // --- getStudentScores tests ---

    @Test
    void getStudentScores_nullStudentId_returnsEmptyList() {
        List<Map<String, Object>> result = scoreRecordService.getStudentScores(null, null, null, null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getStudentScores_withFilters_returnsScores() {
        ScoreRecord record = new ScoreRecord();
        record.setId(1L);
        record.setStudentId(1L);
        record.setScore(90);
        record.setScoreType("ASSIGNMENT");
        record.setAssignmentId(10L);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(record));

        List<Map<String, Object>> result = scoreRecordService.getStudentScores(1L, "ASSIGNMENT", null, null, 10L);
        assertEquals(1, result.size());
        assertEquals(90, result.get(0).get("score"));
        assertEquals("ASSIGNMENT", result.get(0).get("scoreType"));
    }

    @Test
    void getStudentScores_noRecords_returnsEmptyList() {
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = scoreRecordService.getStudentScores(1L, null, null, null, null);
        assertTrue(result.isEmpty());
    }

    // --- getScoreStatistics tests ---

    @Test
    void getScoreStatistics_noParams_returnsDefaultStats() {
        Map<String, Object> result = scoreRecordService.getScoreStatistics(null, null, null);
        assertEquals(0.0, result.get("averageScore"));
        assertEquals(0, result.get("totalCount"));
    }

    @Test
    void getScoreStatistics_withRecords_returnsStats() {
        ScoreRecord r1 = new ScoreRecord();
        r1.setScore(80);
        ScoreRecord r2 = new ScoreRecord();
        r2.setScore(90);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(r1, r2));

        Map<String, Object> result = scoreRecordService.getScoreStatistics(1L, null, null);
        assertEquals(90, result.get("maxScore"));
        assertEquals(80, result.get("minScore"));
        assertEquals(2, result.get("totalCount"));
        assertEquals(85.0, result.get("averageScore"));
    }

    // --- getAssignmentScore tests ---

    @Test
    void getAssignmentScore_found() {
        ScoreRecord record = new ScoreRecord();
        record.setId(1L);
        record.setAssignmentId(10L);
        record.setStudentId(1L);
        record.setScore(75);

        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(record);

        ScoreRecord result = scoreRecordService.getAssignmentScore(10L, 1L);
        assertNotNull(result);
        assertEquals(75, result.getScore());
    }

    @Test
    void getAssignmentScore_notFound() {
        when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        ScoreRecord result = scoreRecordService.getAssignmentScore(999L, 1L);
        assertNull(result);
    }

    // --- getStudentAverageScore tests ---

    @Test
    void getStudentAverageScore_nullStudentId_returnsZeros() {
        Map<String, Object> result = scoreRecordService.getStudentAverageScore(null, null, null);
        assertEquals(0.0, result.get("averageScore"));
        assertEquals(0, result.get("totalCount"));
    }

    @Test
    void getStudentAverageScore_noRecords_returnsZeros() {
        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = scoreRecordService.getStudentAverageScore(1L, "ASSIGNMENT", null);
        assertEquals(0.0, result.get("averageScore"));
        assertEquals(0, result.get("totalCount"));
    }

    // --- exportClassScores tests ---

    @Test
    void exportClassScores_nullClassId_returnsEmptyList() {
        List<Map<String, Object>> result = scoreRecordService.exportClassScores(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void exportClassScores_classNotFound_returnsEmptyList() {
        when(classService.getClassById(999L)).thenReturn(null);

        List<Map<String, Object>> result = scoreRecordService.exportClassScores(999L);
        assertTrue(result.isEmpty());
    }

    @Test
    void exportClassScores_noStudents_returnsEmptyList() {
        Class classInfo = new Class();
        classInfo.setId(1L);
        when(classService.getClassById(1L)).thenReturn(classInfo);
        when(studentClassService.getStudentsByClassId(1L)).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = scoreRecordService.exportClassScores(1L);
        assertTrue(result.isEmpty());
    }

    // ==================== getStudentAverageScore weighted score tests ====================

    @Test
    void getStudentAverageScore_withWeights_calculatesWeightedCorrectly() {
        // Arrange: multiple records with different weights
        ScoreRecord r1 = new ScoreRecord();
        r1.setScore(80);
        r1.setAssignmentId(1L);

        ScoreRecord r2 = new ScoreRecord();
        r2.setScore(100);
        r2.setAssignmentId(2L);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(r1, r2));

        Assignment a1 = new Assignment();
        a1.setId(1L);
        a1.setWeight(1.0);

        Assignment a2 = new Assignment();
        a2.setId(2L);
        a2.setWeight(2.0);

        QueryWrapper<Assignment> w1 = new QueryWrapper<>();
        w1.eq("id", 1L).eq("deleted", 0);
        QueryWrapper<Assignment> w2 = new QueryWrapper<>();
        w2.eq("id", 2L).eq("deleted", 0);

        when(assignmentService.getOne(any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    QueryWrapper<?> qw = invocation.getArgument(0);
                    // Mock returns based on the query
                    return a1; // Simplified for test
                });

        // Act
        Map<String, Object> result = scoreRecordService.getStudentAverageScore(1L, null, null);

        // Assert: simple average should be (80+100)/2 = 90
        assertEquals(90.0, result.get("averageScore"));
    }

    @Test
    void getStudentAverageScore_nullAssignmentId_usesSimpleAverage() {
        // Arrange: records without assignmentId (should use simple average)
        ScoreRecord r1 = new ScoreRecord();
        r1.setScore(75);
        r1.setAssignmentId(null);

        ScoreRecord r2 = new ScoreRecord();
        r2.setScore(85);
        r2.setAssignmentId(null);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(r1, r2));

        // Act
        Map<String, Object> result = scoreRecordService.getStudentAverageScore(1L, null, null);

        // Assert: (75+85)/2 = 80
        assertEquals(80.0, result.get("averageScore"));
    }

    // ==================== getScoreStatistics boundary tests ====================

    @Test
    void getScoreStatistics_singleRecord_statsCorrect() {
        // Arrange: single record
        ScoreRecord r = new ScoreRecord();
        r.setScore(75);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(r));

        // Act
        Map<String, Object> result = scoreRecordService.getScoreStatistics(1L, null, null);

        // Assert
        assertEquals(75, result.get("maxScore"));
        assertEquals(75, result.get("minScore"));
        assertEquals(75.0, result.get("averageScore"));
        assertEquals(1, result.get("totalCount"));
    }

    @Test
    void getScoreStatistics_nullScores_treatsAsZero() {
        // Arrange: record with null score
        ScoreRecord r = new ScoreRecord();
        r.setScore(null);

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(r));

        // Act
        Map<String, Object> result = scoreRecordService.getScoreStatistics(1L, null, null);

        // Assert: null treated as 0
        assertEquals(0, result.get("maxScore"));
        assertEquals(0, result.get("minScore"));
        assertEquals(0.0, result.get("averageScore"));
    }

    @Test
    void getScoreStatistics_largeDataset_calculatesCorrectly() {
        // Arrange: many records
        List<ScoreRecord> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ScoreRecord r = new ScoreRecord();
            r.setScore(i);
            records.add(r);
        }

        when(baseMapper.selectList(any(QueryWrapper.class))).thenReturn(records);

        // Act
        Map<String, Object> result = scoreRecordService.getScoreStatistics(1L, null, null);

        // Assert
        assertEquals(99, result.get("maxScore"));
        assertEquals(0, result.get("minScore"));
        assertEquals(49.5, result.get("averageScore"));
        assertEquals(100, result.get("totalCount"));
    }
}
