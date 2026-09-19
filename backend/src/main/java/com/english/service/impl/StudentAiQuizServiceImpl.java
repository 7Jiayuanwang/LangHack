package com.english.service.impl;

import com.english.entity.StudentAiQuizAttempt;
import com.english.entity.StudentAiQuizRecord;
import com.english.exception.BusinessException;
import com.english.mapper.StudentAiQuizAttemptMapper;
import com.english.mapper.StudentAiQuizRecordMapper;
import com.english.service.StudentAiQuizService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentAiQuizServiceImpl implements StudentAiQuizService {

    private static final Logger log = LoggerFactory.getLogger(StudentAiQuizServiceImpl.class);

    @Autowired
    private StudentAiQuizAttemptMapper attemptMapper;

    @Autowired
    private StudentAiQuizRecordMapper recordMapper;

    @Autowired
    private AgentReviewPlanService reviewPlanService;

    @Override
    @Transactional
    public StudentAiQuizAttempt saveAttempt(StudentAiQuizAttempt attempt, List<StudentAiQuizRecord> records) {
        if (attempt == null) {
            throw new BusinessException("attempt 不能为空");
        }
        if (attempt.getStudentId() == null) {
            throw new BusinessException("studentId 不能为空");
        }
        if (attempt.getTextId() == null) {
            throw new BusinessException("textId 不能为空");
        }
        if (records == null || records.isEmpty()) {
            throw new BusinessException("records 不能为空");
        }

        int total = records.size();
        int correct = 0;
        int totalTime = 0;
        for (StudentAiQuizRecord r : records) {
            if (Boolean.TRUE.equals(r.getIsCorrect())) correct++;
            if (r.getTimeSpentSeconds() != null) totalTime += r.getTimeSpentSeconds();
        }

        attempt.setTotalQuestions(total);
        attempt.setCorrectCount(correct);
        attempt.setTotalTimeSeconds(totalTime);
        if (attempt.getScore() == null) {
            BigDecimal score = total > 0
                    ? BigDecimal.valueOf(correct * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            attempt.setScore(score);
        }
        if (attempt.getSubmittedTime() == null) {
            attempt.setSubmittedTime(LocalDateTime.now());
        }

        attemptMapper.insert(attempt);
        Long attemptId = attempt.getId();

        int order = 1;
        for (StudentAiQuizRecord r : records) {
            r.setAttemptId(attemptId);
            // 冗余字段统一以 attempt 为准，避免调用方漏填
            r.setStudentId(attempt.getStudentId());
            r.setTextId(attempt.getTextId());
            if (r.getQuestionOrder() == null) {
                r.setQuestionOrder(order);
            }
            order++;
            recordMapper.insert(r);
        }

        // Same transaction: a saved attempt always has a matching next-review task.
        reviewPlanService.recordAttempt(attempt);

        return attempt;
    }

    @Override
    public List<StudentAiQuizAttempt> listMyAttempts(Long studentId, Long textId) {
        if (studentId == null) {
            throw new BusinessException("studentId 不能为空");
        }
        try {
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<StudentAiQuizAttempt> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("student_id", studentId);
            if (textId != null) {
                qw.eq("text_id", textId);
            }
            qw.orderByDesc("submitted_time", "id");
            return attemptMapper.selectList(qw);
        } catch (Exception e) {
            log.error("查询学生 AI 题做题记录失败 studentId={} textId={}", studentId, textId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<StudentAiQuizAttempt> listRecentMyAttempts(Long studentId, int limit) {
        if (studentId == null) throw new BusinessException("studentId 不能为空");
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return attemptMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<StudentAiQuizAttempt>()
                .eq("student_id", studentId)
                .orderByDesc("submitted_time", "id")
                .last("LIMIT " + safeLimit));
    }

    @Override
    public Map<String, Object> getMyAttemptDetail(Long studentId, Long attemptId) {
        if (studentId == null || attemptId == null) {
            throw new BusinessException("studentId 和 attemptId 不能为空");
        }
        StudentAiQuizAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException("做题记录不存在");
        }
        if (!studentId.equals(attempt.getStudentId())) {
            throw new BusinessException("无权访问他人的做题记录");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("attempt", attempt);
        result.put("records", recordMapper.listByAttemptId(attemptId));
        return result;
    }

    @Override
    public Map<String, Object> queryAttemptsForAdmin(Long studentId, Long textId,
                                                     LocalDateTime startTime, LocalDateTime endTime,
                                                     int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 20;
        if (pageSize > 200) pageSize = 200;
        int offset = (page - 1) * pageSize;

        List<Map<String, Object>> list = attemptMapper.queryAttemptsForAdmin(
                studentId, textId, startTime, endTime, offset, pageSize);
        if (list == null) list = new ArrayList<>();
        long total = attemptMapper.countAttemptsForAdmin(studentId, textId, startTime, endTime);

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    @Override
    public Map<String, Object> getAttemptDetailForAdmin(Long attemptId) {
        if (attemptId == null) {
            throw new BusinessException("attemptId 不能为空");
        }
        StudentAiQuizAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BusinessException("做题记录不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("attempt", attempt);
        result.put("records", recordMapper.listByAttemptId(attemptId));
        return result;
    }
}
