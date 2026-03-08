package com.english.service;

import com.english.entity.TextLearningRecord;
import com.english.mapper.TextLearningRecordMapper;
import com.english.service.impl.LearningRecordsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LearningRecordsServiceTest {

    @Mock
    private TextLearningRecordMapper textLearningRecordMapper;

    @InjectMocks
    private LearningRecordsServiceImpl learningRecordsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testStartReadingSession() {
        Long studentId = 1L;
        Long textId = 100L;
        Long assignmentId = 50L;

        // Mock the mapper insert to simulate setting the ID
        doAnswer(invocation -> {
            TextLearningRecord record = invocation.getArgument(0);
            record.setId(1L);
            return 1;
        }).when(textLearningRecordMapper).insert(any(TextLearningRecord.class));

        Long recordId = learningRecordsService.startReadingSession(studentId, textId, assignmentId);

        assertEquals(1L, recordId);
        verify(textLearningRecordMapper, times(1)).insert(any(TextLearningRecord.class));
    }

    @Test
    public void testCompleteReadingSession() {
        Long recordId = 1L;
        TextLearningRecord record = new TextLearningRecord();
        record.setId(recordId);
        record.setStudentId(1L);
        record.setTextId(100L);
        record.setStartTime(LocalDateTime.now().minusMinutes(5));
        record.setIsCompleted(0);

        when(textLearningRecordMapper.selectById(recordId)).thenReturn(record);
        when(textLearningRecordMapper.updateById(any(TextLearningRecord.class))).thenReturn(1);

        learningRecordsService.completeReadingSession(recordId);

        assertEquals(1, record.getIsCompleted());
        assertNotNull(record.getEndTime());
        assertTrue(record.getDurationSeconds() >= 300); // 5 minutes = 300 seconds
        verify(textLearningRecordMapper, times(1)).selectById(recordId);
        verify(textLearningRecordMapper, times(1)).updateById(any(TextLearningRecord.class));
    }

    @Test
    public void testCompleteReadingSessionAlreadyCompleted() {
        Long recordId = 1L;
        TextLearningRecord record = new TextLearningRecord();
        record.setId(recordId);
        record.setIsCompleted(1); // Already completed

        when(textLearningRecordMapper.selectById(recordId)).thenReturn(record);

        learningRecordsService.completeReadingSession(recordId);

        verify(textLearningRecordMapper, times(1)).selectById(recordId);
        verify(textLearningRecordMapper, never()).updateById(any(TextLearningRecord.class));
    }
}
