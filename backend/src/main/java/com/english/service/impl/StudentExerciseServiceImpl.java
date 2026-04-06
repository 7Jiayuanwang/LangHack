package com.english.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.StudentExerciseAnswer;
import com.english.entity.ExerciseOption;
import com.english.mapper.StudentExerciseAnswerMapper;
import com.english.mapper.ExerciseOptionMapper;
import com.english.service.StudentExerciseService;
import com.english.service.TextExerciseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 学生习题服务实现类
 */
@Service
public class StudentExerciseServiceImpl extends ServiceImpl<StudentExerciseAnswerMapper, StudentExerciseAnswer> implements StudentExerciseService {

    private static final Logger logger = LoggerFactory.getLogger(StudentExerciseServiceImpl.class);

    @Autowired
    private StudentExerciseAnswerMapper studentExerciseAnswerMapper;

    @Autowired
    private ExerciseOptionMapper exerciseOptionMapper;

    @Autowired
    private TextExerciseService textExerciseService;

    @Override
    @Transactional
    public Map<String, Object> submitAnswer(StudentExerciseAnswer studentAnswer) {
        // 参数验证
        if (studentAnswer == null) {
            throw new com.english.exception.BusinessException("答案对象不能为空");
        }
        if (studentAnswer.getStudentId() == null) {
            throw new com.english.exception.BusinessException("学生ID不能为空");
        }
        if (studentAnswer.getExerciseId() == null) {
            throw new com.english.exception.BusinessException("习题ID不能为空");
        }
        if (studentAnswer.getAnswerContent() == null || studentAnswer.getAnswerContent().trim().isEmpty()) {
            throw new com.english.exception.BusinessException("答案内容不能为空");
        }

        Map<String, Object> result = new HashMap<>();

        // 检查答案是否正确
        boolean isCorrect = textExerciseService.checkAnswer(studentAnswer.getExerciseId(), studentAnswer.getAnswerContent());
        studentAnswer.setIsCorrect(isCorrect);

        // BUG FIX #1: 设置答题时间（如未提供）
        if (studentAnswer.getAnswerTime() == null) {
            studentAnswer.setAnswerTime(LocalDateTime.now());
        }
        // 检查是否已经存在答案记录
        StudentExerciseAnswer existingAnswer = studentExerciseAnswerMapper.getAnswer(
            studentAnswer.getStudentId(),
            studentAnswer.getExerciseId(),
            studentAnswer.getAssignmentId()
        );

        if (existingAnswer != null) {
            // 更新现有答案
            studentAnswer.setId(existingAnswer.getId());
            // BUG FIX #6: 更新时重新设置答题时间，以反映最后答题时间
            studentAnswer.setAnswerTime(LocalDateTime.now());
            int updateResult = studentExerciseAnswerMapper.updateById(studentAnswer);
            result.put("success", updateResult > 0);
        } else {
            // 插入新答案
            int insertResult = studentExerciseAnswerMapper.insert(studentAnswer);
            result.put("success", insertResult > 0);
        }

        result.put("isCorrect", isCorrect);
        result.put("message", isCorrect ? "回答正确！" : "回答错误，请继续努力！");

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentExerciseAnswer> getStudentAnswersByText(Long studentId, Long textId, Long assignmentId) {
        try {
            return studentExerciseAnswerMapper.getAnswersByTextId(studentId, textId, assignmentId);
        } catch (Exception e) {
            logger.error("获取学生课文答题记录失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentExerciseAnswer> getStudentAnswersByAssignment(Long studentId, Long assignmentId) {
        try {
            return studentExerciseAnswerMapper.getAnswersByAssignmentId(assignmentId, studentId);
        } catch (Exception e) {
            logger.error("获取学生任务答题记录失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getStudentAnswerStats(Long studentId, Long assignmentId) {
        try {
            Map<String, Object> stats = studentExerciseAnswerMapper.getAnswerStats(assignmentId, studentId);

            if (stats == null || stats.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("totalAnswers", 0);
                empty.put("correctAnswers", 0);
                empty.put("incorrectAnswers", 0);
                empty.put("accuracy", 0.0);
                return empty;
            }

            int total = stats.get("total") instanceof Number ? ((Number) stats.get("total")).intValue() : 0;
            int correct = stats.get("correct") instanceof Number ? ((Number) stats.get("correct")).intValue() : 0;
            // BUG FIX #2: incorrect 应该是 total - correct（包括 NULL 值），而非 SQL 返回的 FALSE 数量
            int incorrect = total - correct;

            Map<String, Object> result = new HashMap<>();
            result.put("totalAnswers", total);
            result.put("correctAnswers", correct);
            result.put("incorrectAnswers", incorrect);

            if (total > 0) {
                double accuracy = (double) correct / total * 100;
                result.put("accuracy", Math.round(accuracy * 100.0) / 100.0); // 保留两位小数
            } else {
                result.put("accuracy", 0.0);
            }

            return result;
        } catch (Exception e) {
            logger.error("获取学生答题统计失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("totalAnswers", 0);
            result.put("correctAnswers", 0);
            result.put("incorrectAnswers", 0);
            result.put("accuracy", 0.0);
            return result;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasCompletedTextExercises(Long studentId, Long textId, Long assignmentId) {
        try {
            // 获取课文的习题总数
            int totalExercises = textExerciseService.getExerciseCount(textId);
            if (totalExercises == 0) {
                return true; // 没有习题视为已完成
            }

            // 获取学生的答题记录数（按 exerciseId 去重，防止重复提交导致误判）
            List<StudentExerciseAnswer> answers = studentExerciseAnswerMapper.getAnswersByTextId(studentId, textId, assignmentId);
            long distinctExerciseCount = answers != null
                    ? answers.stream().map(StudentExerciseAnswer::getExerciseId).distinct().count()
                    : 0;

            return distinctExerciseCount >= totalExercises;
        } catch (Exception e) {
            logger.error("检查课文习题完成状态失败", e);
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTextExerciseProgress(Long studentId, Long textId, Long assignmentId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取课文的习题总数
            int totalExercises = textExerciseService.getExerciseCount(textId);

            result.put("totalExercises", totalExercises);

            if (totalExercises == 0) {
                result.put("completedExercises", 0);
                result.put("progress", 100);
                result.put("isCompleted", true);
                return result;
            }

            // 获取学生的答题记录（按 exerciseId 去重，与 hasCompletedTextExercises 保持一致）
            List<StudentExerciseAnswer> answers = studentExerciseAnswerMapper.getAnswersByTextId(studentId, textId, assignmentId);
            // 按 exerciseId 去重，取每个 exercise 的最新答案
            Map<Long, StudentExerciseAnswer> latestByExercise = new java.util.LinkedHashMap<>();
            if (answers != null) {
                for (StudentExerciseAnswer answer : answers) {
                    latestByExercise.put(answer.getExerciseId(), answer);
                }
            }
            int completedExercises = latestByExercise.size();

            // 统计正确答案数（基于去重后的答案）
            int correctAnswers = 0;
            for (StudentExerciseAnswer answer : latestByExercise.values()) {
                if (Boolean.TRUE.equals(answer.getIsCorrect())) {
                    correctAnswers++;
                }
            }

            result.put("completedExercises", completedExercises);
            result.put("correctAnswers", correctAnswers);
            result.put("progress", totalExercises > 0 ? Math.round(completedExercises * 100.0 / totalExercises * 100.0) / 100.0 : 0);
            result.put("isCompleted", completedExercises >= totalExercises);

            if (completedExercises > 0) {
                double accuracy = (double) correctAnswers / completedExercises * 100;
                result.put("accuracy", Math.round(accuracy * 100.0) / 100.0);
            } else {
                result.put("accuracy", 0.0);
            }

            return result;
        } catch (Exception e) {
            logger.error("获取课文习题进度失败", e);
            // 异常情况下返回未完成状态，而非完成状态
            result.put("totalExercises", 0);
            result.put("completedExercises", 0);
            result.put("progress", 0);
            result.put("isCompleted", false);
            result.put("accuracy", 0.0);
            return result;
        }
    }
}