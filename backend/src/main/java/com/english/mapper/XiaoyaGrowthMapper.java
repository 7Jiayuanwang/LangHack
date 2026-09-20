package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.XiaoyaExternalLearning;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface XiaoyaGrowthMapper extends BaseMapper<XiaoyaExternalLearning> {
    @Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockUserForExternalLearning(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT text_id) FROM text_learning_records " +
            "WHERE student_id = #{userId} AND is_completed = 1")
    long countCompletedArticles(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT a.text_id) FROM student_ai_quiz_attempts a " +
            "WHERE a.student_id = #{userId} AND a.total_questions > 0 " +
            "AND EXISTS (SELECT 1 FROM student_ai_quiz_records r " +
            "WHERE r.attempt_id = a.id AND TRIM(r.user_answer) <> '')")
    long countPracticedArticles(@Param("userId") Long userId);

    @Select("SELECT COUNT(DISTINCT learning_day) FROM xiaoya_external_learning WHERE user_id = #{userId}")
    long countExternalDays(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM xiaoya_external_learning " +
            "WHERE user_id = #{userId} AND learning_day = #{day}")
    int countExternalNotesOnDay(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Select("SELECT COUNT(DISTINCT study_day) FROM (" +
            " SELECT DATE(COALESCE(end_time, updated_time)) AS study_day FROM text_learning_records " +
            " WHERE student_id = #{userId} AND is_completed = 1 " +
            " AND COALESCE(end_time, updated_time) >= #{since} " +
            " UNION ALL " +
            " SELECT DATE(a.submitted_time) AS study_day FROM student_ai_quiz_attempts a " +
            " WHERE a.student_id = #{userId} AND a.total_questions > 0 AND a.submitted_time >= #{since} " +
            " AND EXISTS (SELECT 1 FROM student_ai_quiz_records r " +
            " WHERE r.attempt_id = a.id AND TRIM(r.user_answer) <> '') " +
            " UNION ALL " +
            " SELECT learning_day AS study_day FROM xiaoya_external_learning " +
            " WHERE user_id = #{userId} AND learning_day >= DATE(#{since})" +
            ") meaningful_days")
    int countMeaningfulDaysSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
