package com.english.service;

import com.english.entity.StudentAiQuizAttempt;
import com.english.entity.StudentAiQuizRecord;
import com.english.mapper.StudentAiQuizAttemptMapper;
import com.english.mapper.StudentAiQuizRecordMapper;
import com.english.service.impl.AgentReviewPlanService;
import com.english.service.impl.StudentAiQuizServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentAiQuizReviewIntegrationTest {
    @Mock private StudentAiQuizAttemptMapper attemptMapper;
    @Mock private StudentAiQuizRecordMapper recordMapper;
    @Mock private AgentReviewPlanService reviewPlanService;
    @InjectMocks private StudentAiQuizServiceImpl service;

    @Test
    void savedQuizSchedulesTheNextReview() {
        StudentAiQuizAttempt attempt = new StudentAiQuizAttempt();
        attempt.setStudentId(7L);
        attempt.setTextId(20L);
        StudentAiQuizRecord record = new StudentAiQuizRecord();
        record.setIsCorrect(true);
        doAnswer(invocation -> {
            ((StudentAiQuizAttempt) invocation.getArgument(0)).setId(10L);
            return 1;
        }).when(attemptMapper).insert(any(StudentAiQuizAttempt.class));

        StudentAiQuizAttempt saved = service.saveAttempt(attempt, List.of(record));

        assertEquals(10L, saved.getId());
        assertEquals(10L, record.getAttemptId());
        verify(recordMapper).insert(record);
        verify(reviewPlanService).recordAttempt(saved);
    }
}
