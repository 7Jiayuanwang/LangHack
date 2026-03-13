package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 学习记录Mapper接口
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

    /**
     * 原子级的学习时间累加（防竞态条件）
     * @param studentId 学生ID
     * @param textId 课文ID
     * @param assignmentId 任务ID（可为null）
     * @param additionalSeconds 增加的秒数
     * @return 更新的记录数
     */
    @Update("<script>" +
            "UPDATE learning_records SET duration_seconds = duration_seconds + #{additionalSeconds} " +
            "WHERE student_id = #{studentId} AND text_id = #{textId} " +
            "AND record_type = 'TEXT_VIEW' " +
            "<if test='assignmentId != null'>AND assignment_id = #{assignmentId}</if>" +
            "<if test='assignmentId == null'>AND (assignment_id IS NULL OR assignment_id = 0)</if>" +
            " ORDER BY created_time DESC LIMIT 1" +
            "</script>")
    int incrementTextViewDuration(@Param("studentId") Long studentId,
                                  @Param("textId") Long textId,
                                  @Param("assignmentId") Long assignmentId,
                                  @Param("additionalSeconds") Integer additionalSeconds);
}
