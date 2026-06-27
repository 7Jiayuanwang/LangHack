package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearningRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习记录Mapper接口
 */
@Mapper
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {

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

    @Select("SELECT COUNT(DISTINCT DATE(created_time)) FROM learning_records " +
            "WHERE student_id = #{studentId} AND created_time >= #{since}")
    int countActiveDaysSince(@Param("studentId") Long studentId, @Param("since") LocalDateTime since);

    @Select("SELECT DISTINCT DATE(created_time) FROM learning_records " +
            "WHERE student_id = #{studentId} ORDER BY DATE(created_time) DESC")
    List<LocalDate> selectDistinctStudyDates(@Param("studentId") Long studentId);

    @Select("SELECT COALESCE(SUM(LEAST(duration_seconds, 7200)), 0) FROM learning_records " +
            "WHERE student_id = #{studentId} AND created_time >= #{since} AND created_time < #{until}")
    long sumDurationSecondsBetween(@Param("studentId") Long studentId,
                                   @Param("since") LocalDateTime since,
                                   @Param("until") LocalDateTime until);

    @Select("SELECT COALESCE(SUM(LEAST(duration_seconds, 7200)), 0) FROM learning_records " +
            "WHERE student_id = #{studentId} AND record_type = 'TEXT_VIEW' " +
            "AND created_time >= #{since} AND created_time < #{until}")
    long sumReadingSecondsBetween(@Param("studentId") Long studentId,
                                  @Param("since") LocalDateTime since,
                                  @Param("until") LocalDateTime until);

    @Select("SELECT DISTINCT student_id FROM learning_records " +
            "WHERE created_time >= DATE_SUB(NOW(), INTERVAL #{lastNDays} DAY)")
    List<Long> selectActiveUserIds(@Param("lastNDays") int lastNDays);
}
