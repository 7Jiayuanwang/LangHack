package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LearnerProfileSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface LearnerProfileSnapshotMapper extends BaseMapper<LearnerProfileSnapshot> {

    @Select("SELECT * FROM learner_profile_snapshots " +
            "WHERE user_id = #{userId} AND snapshot_date >= #{since} " +
            "ORDER BY snapshot_date DESC")
    List<LearnerProfileSnapshot> selectByUserSince(@Param("userId") Long userId,
                                                    @Param("since") LocalDate since);

    @Select("SELECT risk_level, COUNT(*) AS cnt " +
            "FROM learner_profile_snapshots " +
            "WHERE snapshot_date = #{date} AND user_id IN " +
            "(SELECT student_id FROM student_classes WHERE class_id = #{classId}) " +
            "GROUP BY risk_level")
    List<Map<String, Object>> selectRiskDistributionByClass(@Param("classId") Long classId,
                                                            @Param("date") LocalDate date);

    @Select("SELECT * FROM learner_profile_snapshots " +
            "WHERE user_id = #{userId} AND snapshot_date = #{date}")
    LearnerProfileSnapshot selectByUserAndDate(@Param("userId") Long userId,
                                                @Param("date") LocalDate date);

    @Select("SELECT snapshot_date AS snapshotDate, " +
            "COUNT(*) AS totalUsers, " +
            "SUM(CASE WHEN risk_level = 'high' THEN 1 ELSE 0 END) AS riskHigh, " +
            "SUM(CASE WHEN risk_level = 'medium' THEN 1 ELSE 0 END) AS riskMedium, " +
            "SUM(CASE WHEN risk_level = 'low' THEN 1 ELSE 0 END) AS riskLow, " +
            "ROUND(AVG(active_days_7d), 1) AS avgActiveDays7d, " +
            "ROUND(AVG(study_minutes_14d), 0) AS avgStudyMinutes14d, " +
            "ROUND(AVG(COALESCE(overdue_review_ratio, 0)), 3) AS avgOverdueReviewRatio " +
            "FROM learner_profile_snapshots " +
            "WHERE snapshot_date >= #{since} " +
            "GROUP BY snapshot_date " +
            "ORDER BY snapshot_date")
    List<Map<String, Object>> selectDailyAggregation(@Param("since") LocalDate since);

    @Select("SELECT matched_tag_codes FROM learner_profile_snapshots " +
            "WHERE snapshot_date >= #{since} AND matched_tag_codes IS NOT NULL " +
            "AND matched_tag_codes != ''")
    List<String> selectTagCodesSince(@Param("since") LocalDate since);
}
