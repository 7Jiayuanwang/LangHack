package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.entity.LearnerReviewTask;
import com.english.entity.StudentAiQuizAttempt;
import com.english.mapper.LearnerReviewTaskMapper;
import com.english.service.impl.AgentReviewPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentReviewPlanServiceTest {
    @Mock private LearnerReviewTaskMapper mapper;
    @InjectMocks private AgentReviewPlanService service;

    @Test
    void firstStrongAttemptCreatesThreeDayTaskOnlyOnce() {
        LocalDateTime submitted = LocalDateTime.of(2026, 9, 19, 16, 30);
        StudentAiQuizAttempt attempt = attempt(11L, submitted, 4, 5);
        LearnerReviewTask existing = new LearnerReviewTask();
        existing.setLastAttemptId(11L);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        service.recordAttempt(attempt);

        verify(mapper).ensureTask(7L, 20L, 11L, 4, 5, submitted,
                LocalDateTime.of(2026, 9, 22, 7, 0));
        verify(mapper, never()).updateById(any(LearnerReviewTask.class));
    }

    @Test
    void weakRepeatReschedulesForNextMorning() {
        LocalDateTime submitted = LocalDateTime.of(2026, 9, 19, 16, 30);
        StudentAiQuizAttempt attempt = attempt(12L, submitted, 2, 5);
        LearnerReviewTask existing = new LearnerReviewTask();
        existing.setId(50L);
        existing.setLastAttemptId(11L);
        existing.setRepeatCount(1);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        service.recordAttempt(attempt);

        assertEquals(2, existing.getRepeatCount());
        assertEquals(12L, existing.getLastAttemptId());
        assertEquals(LocalDateTime.of(2026, 9, 20, 7, 0), existing.getDueAt());
        verify(mapper).updateById(existing);
    }

    private StudentAiQuizAttempt attempt(Long id, LocalDateTime submitted, int correct, int total) {
        StudentAiQuizAttempt attempt = new StudentAiQuizAttempt();
        attempt.setId(id);
        attempt.setStudentId(7L);
        attempt.setTextId(20L);
        attempt.setSubmittedTime(submitted);
        attempt.setCorrectCount(correct);
        attempt.setTotalQuestions(total);
        return attempt;
    }
}
