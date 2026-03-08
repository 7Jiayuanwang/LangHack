package com.english.service;

import com.english.entity.StudentExerciseAnswer;

import java.util.List;
import java.util.Map;

/**
 * 学生习题服务接口
 */
public interface StudentExerciseService {

    /**
     * 提交习题答案
     * @param studentExerciseAnswer 答题记录
     * @return 提交结果（包含是否正确）
     */
    Map<String, Object> submitAnswer(StudentExerciseAnswer studentExerciseAnswer);

    /**
     * 获取学生在课文中的所有答题记录
     * @param studentId 学生ID
     * @param textId 课文ID
     * @param assignmentId 任务ID（可选）
     * @return 答题记录列表
     */
    List<StudentExerciseAnswer> getStudentAnswersByText(Long studentId, Long textId, Long assignmentId);

    /**
     * 获取学生在任务中的所有答题记录
     * @param studentId 学生ID
     * @param assignmentId 任务ID
     * @return 答题记录列表
     */
    List<StudentExerciseAnswer> getStudentAnswersByAssignment(Long studentId, Long assignmentId);

    /**
     * 获取学生在任务中的答题统计
     * @param studentId 学生ID
     * @param assignmentId 任务ID
     * @return 统计信息
     */
    Map<String, Object> getStudentAnswerStats(Long studentId, Long assignmentId);

    /**
     * 检查学生是否已完成课文习题
     * @param studentId 学生ID
     * @param textId 课文ID
     * @param assignmentId 任务ID（可选）
     * @return 是否完成
     */
    boolean hasCompletedTextExercises(Long studentId, Long textId, Long assignmentId);

    /**
     * 获取学生课文的完成进度
     * @param studentId 学生ID
     * @param textId 课文ID
     * @param assignmentId 任务ID（可选）
     * @return 进度信息
     */
    Map<String, Object> getTextExerciseProgress(Long studentId, Long textId, Long assignmentId);
}