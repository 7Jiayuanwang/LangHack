package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.datasource.UseMaster;
import com.english.entity.LearnerReviewTask;
import com.english.entity.StudentAiQuizAttempt;
import com.english.exception.BusinessException;
import com.english.mapper.LearnerReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Persists the next article review as part of the quiz submission transaction. */
@Service
@UseMaster
@RequiredArgsConstructor
public class AgentReviewPlanService {
    private final LearnerReviewTaskMapper taskMapper;

    @Transactional
    public void recordAttempt(StudentAiQuizAttempt attempt) {
        if (attempt == null || attempt.getId() == null || attempt.getStudentId() == null
                || attempt.getTextId() == null || attempt.getSubmittedTime() == null
                || attempt.getTotalQuestions() == null || attempt.getTotalQuestions() <= 0) {
            throw new BusinessException("无法根据不完整的答题记录安排复习");
        }
        int total = attempt.getTotalQuestions();
        int correct = attempt.getCorrectCount() == null ? 0 : attempt.getCorrectCount();
        boolean weak = correct * 100 < total * 80;
        LocalDateTime submitted = attempt.getSubmittedTime();
        taskMapper.ensureTask(attempt.getStudentId(), attempt.getTextId(), attempt.getId(),
                correct, total, submitted, dueAt(submitted, weak ? 1 : 3));

        LearnerReviewTask task = taskMapper.selectOne(new QueryWrapper<LearnerReviewTask>()
                .eq("user_id", attempt.getStudentId())
                .eq("text_id", attempt.getTextId())
                .eq("deleted", 0)
                .last("FOR UPDATE"));
        if (task == null) throw new BusinessException("复习计划保存失败");
        if (Objects.equals(task.getLastAttemptId(), attempt.getId())) return;

        task.setLastAttemptId(attempt.getId());
        task.setRepeatCount((task.getRepeatCount() == null ? 0 : task.getRepeatCount()) + 1);
        task.setLastCorrect(correct);
        task.setLastTotal(total);
        task.setLastAttemptTime(submitted);
        task.setDueAt(dueAt(submitted, weak ? 1 : 7));
        task.setUpdatedTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public List<LearnerReviewTask> listDue(Long userId, LocalDateTime now, int limit) {
        if (userId == null) throw new BusinessException("userId 不能为空");
        return taskMapper.listDue(userId, now, Math.min(Math.max(limit, 1), 20));
    }

    public long countDue(Long userId, LocalDateTime now) {
        if (userId == null) throw new BusinessException("userId 不能为空");
        return taskMapper.countDue(userId, now);
    }

    private LocalDateTime dueAt(LocalDateTime submitted, int days) {
        return submitted.toLocalDate().plusDays(days).atTime(7, 0);
    }
}
