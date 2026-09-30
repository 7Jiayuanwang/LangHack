package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.TextLearningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

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

    /**
     * 分页获取按文章去重的阅读记录（按最近阅读时间排序）
     * 返回每篇文章的聚合统计：阅读次数、总时长、是否完成、最后阅读时间
     */
    @Select("SELECT text_id AS textId, " +
            "COUNT(*) AS readCount, " +
            "COALESCE(SUM(duration_seconds), 0) AS totalDuration, " +
            "MAX(CASE WHEN is_completed = 1 THEN 1 ELSE 0 END) AS isCompleted, " +
            "MAX(start_time) AS lastReadAt " +
            "FROM text_learning_records " +
            "WHERE student_id = #{studentId} " +
            "GROUP BY text_id " +
            "ORDER BY lastReadAt DESC " +
            "LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> getGroupedHistory(@Param("studentId") Long studentId,
                                                 @Param("size") int size,
                                                 @Param("offset") int offset);

    @Select("SELECT content_version, COUNT(*) as count, " +
            "COALESCE(SUM(duration_seconds), 0) as total_seconds " +
            "FROM text_learning_records " +
            "WHERE student_id = #{studentId} AND content_version IS NOT NULL " +
            "GROUP BY content_version ORDER BY total_seconds DESC")
    List<Map<String, Object>> selectVersionDistribution(@Param("studentId") Long studentId);

    @Select("SELECT content_version FROM text_learning_records " +
            "WHERE student_id = #{studentId} AND content_version IS NOT NULL " +
            "GROUP BY content_version ORDER BY SUM(duration_seconds) DESC LIMIT 1")
    String selectPrimaryVersion(@Param("studentId") Long studentId);

    @Select("SELECT r.text_id FROM text_learning_records r " +
            "WHERE r.student_id = #{studentId} AND r.is_completed = 0 " +
            "AND NOT EXISTS (SELECT 1 FROM text_learning_records done " +
            "WHERE done.student_id = r.student_id AND done.text_id = r.text_id " +
            "AND done.is_completed = 1) " +
            "ORDER BY r.start_time DESC, r.id DESC LIMIT 1")
    Long selectLatestUnfinishedTextId(@Param("studentId") Long studentId);

    @Select("SELECT text_id FROM text_learning_records " +
            "WHERE student_id = #{studentId} AND is_completed = 1 " +
            "GROUP BY text_id ORDER BY MAX(end_time) DESC LIMIT 500")
    List<Long> selectRecentlyCompletedTextIds(@Param("studentId") Long studentId);
}
