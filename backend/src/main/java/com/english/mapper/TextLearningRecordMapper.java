package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TextLearningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文章学习记录Mapper接口
 */
@Mapper
public interface TextLearningRecordMapper extends BaseMapper<TextLearningRecord> {

    /**
     * 统计学生阅读过的不重复文章数（阅读篇数）
     */
    @Select("SELECT COUNT(DISTINCT text_id) FROM text_learning_records WHERE student_id = #{studentId}")
    long countDistinctTexts(@Param("studentId") Long studentId);

    /**
     * 统计学生已完成的不重复文章数（完成篇数）
     */
    @Select("SELECT COUNT(DISTINCT text_id) FROM text_learning_records WHERE student_id = #{studentId} AND is_completed = 1")
    long countDistinctCompletedTexts(@Param("studentId") Long studentId);
}
