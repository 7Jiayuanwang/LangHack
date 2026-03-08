package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.StudentExerciseAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 学生习题答题记录Mapper接口
 */
@Mapper
public interface StudentExerciseAnswerMapper extends BaseMapper<StudentExerciseAnswer> {

    /**
     * 根据学生ID和习题ID获取答题记录
     * @param studentId 学生ID
     * @param exerciseId 习题ID
     * @param assignmentId 任务ID（可选）
     * @return 答题记录
     */
    StudentExerciseAnswer getAnswer(@Param("studentId") Long studentId,
                                  @Param("exerciseId") Long exerciseId,
                                  @Param("assignmentId") Long assignmentId);

    /**
     * 根据学生ID获取课文的所有答题记录
     * @param studentId 学生ID
     * @param textId 课文ID
     * @param assignmentId 任务ID（可选）
     * @return 答题记录列表
     */
    List<StudentExerciseAnswer> getAnswersByTextId(@Param("studentId") Long studentId,
                                                  @Param("textId") Long textId,
                                                  @Param("assignmentId") Long assignmentId);

    /**
     * 根据任务ID获取学生的所有答题记录
     * @param assignmentId 任务ID
     * @param studentId 学生ID
     * @return 答题记录列表
     */
    List<StudentExerciseAnswer> getAnswersByAssignmentId(@Param("assignmentId") Long assignmentId,
                                                        @Param("studentId") Long studentId);

    /**
     * 统计学生在任务中的答题情况
     * @param assignmentId 任务ID
     * @param studentId 学生ID
     * @return 统计信息 {total, correct, incorrect}
     */
    java.util.Map<String, Object> getAnswerStats(@Param("assignmentId") Long assignmentId,
                        @Param("studentId") Long studentId);
}