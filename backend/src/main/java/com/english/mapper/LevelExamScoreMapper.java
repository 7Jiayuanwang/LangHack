package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.LevelExamScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 等级考试成绩Mapper接口
 */
@Mapper
public interface LevelExamScoreMapper extends BaseMapper<LevelExamScore> {

    /**
     * 按student_id+exam_type+exam_date唯一键UPSERT
     */
    void upsertByStudentExam(LevelExamScore entity);

    /**
     * CET 对比：平台用户 vs 非平台用户的通过情况
     * 平台用户 = users 中有 learning_records 或 user_text_reads 记录的学生
     */
    @Select("SELECT " +
            "  COUNT(DISTINCT les.student_id) AS totalStudents, " +
            "  COALESCE(SUM(CASE WHEN pu.username IS NOT NULL THEN 1 ELSE 0 END), 0) AS usedTotal, " +
            "  COALESCE(SUM(CASE WHEN pu.username IS NOT NULL AND les.is_passed = 1 THEN 1 ELSE 0 END), 0) AS usedPassed, " +
            "  COALESCE(SUM(CASE WHEN pu.username IS NULL THEN 1 ELSE 0 END), 0) AS unusedTotal, " +
            "  COALESCE(SUM(CASE WHEN pu.username IS NULL AND les.is_passed = 1 THEN 1 ELSE 0 END), 0) AS unusedPassed " +
            "FROM (SELECT DISTINCT les2.student_id, MAX(les2.is_passed) AS is_passed FROM level_exam_scores les2 GROUP BY les2.student_id) les " +
            "INNER JOIN users u ON u.username COLLATE utf8mb4_unicode_ci = les.student_id AND u.deleted = 0 " +
            "LEFT JOIN (SELECT DISTINCT u2.username FROM users u2 WHERE u2.deleted = 0 AND (EXISTS (SELECT 1 FROM learning_records lr WHERE lr.student_id = u2.id) OR EXISTS (SELECT 1 FROM user_text_reads utr WHERE utr.user_id = u2.id))) pu ON pu.username COLLATE utf8mb4_unicode_ci = les.student_id")
    Map<String, Object> selectCetComparison();

    /**
     * CET 详细分析：按考试类型统计
     */
    @Select("SELECT les.exam_type AS examType, " +
            "  COUNT(DISTINCT les.student_id) AS total, " +
            "  COALESCE(SUM(les.is_passed), 0) AS passed, " +
            "  ROUND(COALESCE(SUM(les.is_passed), 0) * 100.0 / NULLIF(COUNT(DISTINCT les.student_id), 0), 1) AS passRate " +
            "FROM level_exam_scores les " +
            "INNER JOIN users u ON u.username COLLATE utf8mb4_unicode_ci = les.student_id AND u.deleted = 0 " +
            "GROUP BY les.exam_type ORDER BY les.exam_type")
    List<Map<String, Object>> selectCetByExamType();

    /**
     * CET 详细分析：平台用户的分数分布（按区间）
     */
    @Select("SELECT " +
            "  CASE " +
            "    WHEN les.score < 300 THEN '300以下' " +
            "    WHEN les.score < 350 THEN '300-349' " +
            "    WHEN les.score < 400 THEN '350-399' " +
            "    WHEN les.score < 425 THEN '400-424' " +
            "    WHEN les.score < 500 THEN '425-499' " +
            "    WHEN les.score < 550 THEN '500-549' " +
            "    ELSE '550以上' END AS scoreRange, " +
            "  COUNT(DISTINCT les.student_id) AS cnt " +
            "FROM level_exam_scores les " +
            "INNER JOIN users u ON u.username COLLATE utf8mb4_unicode_ci = les.student_id AND u.deleted = 0 " +
            "WHERE EXISTS (SELECT 1 FROM learning_records lr WHERE lr.student_id = u.id) " +
            "   OR EXISTS (SELECT 1 FROM user_text_reads utr WHERE utr.user_id = u.id) " +
            "GROUP BY scoreRange ORDER BY MIN(les.score)")
    List<Map<String, Object>> selectCetScoreDistUsed();

    /**
     * CET 详细分析：非平台用户的分数分布（按区间）
     */
    @Select("SELECT " +
            "  CASE " +
            "    WHEN les.score < 300 THEN '300以下' " +
            "    WHEN les.score < 350 THEN '300-349' " +
            "    WHEN les.score < 400 THEN '350-399' " +
            "    WHEN les.score < 425 THEN '400-424' " +
            "    WHEN les.score < 500 THEN '425-499' " +
            "    WHEN les.score < 550 THEN '500-549' " +
            "    ELSE '550以上' END AS scoreRange, " +
            "  COUNT(DISTINCT les.student_id) AS cnt " +
            "FROM level_exam_scores les " +
            "INNER JOIN users u ON u.username COLLATE utf8mb4_unicode_ci = les.student_id AND u.deleted = 0 " +
            "WHERE NOT EXISTS (SELECT 1 FROM learning_records lr WHERE lr.student_id = u.id) " +
            "  AND NOT EXISTS (SELECT 1 FROM user_text_reads utr WHERE utr.user_id = u.id) " +
            "GROUP BY scoreRange ORDER BY MIN(les.score)")
    List<Map<String, Object>> selectCetScoreDistUnused();

    /**
     * CET 详细分析：历年通过率趋势
     */
    @Select("SELECT " +
            "  YEAR(les.exam_date) AS examYear, " +
            "  les.exam_type AS examType, " +
            "  ROUND(COALESCE(SUM(les.is_passed), 0) * 100.0 / NULLIF(COUNT(DISTINCT les.student_id), 0), 1) AS passRate " +
            "FROM level_exam_scores les " +
            "INNER JOIN users u ON u.username COLLATE utf8mb4_unicode_ci = les.student_id AND u.deleted = 0 " +
            "GROUP BY YEAR(les.exam_date), les.exam_type ORDER BY examYear, examType")
    List<Map<String, Object>> selectCetTrend();
}
