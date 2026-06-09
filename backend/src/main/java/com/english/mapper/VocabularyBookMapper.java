package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.VocabularyBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface VocabularyBookMapper extends BaseMapper<VocabularyBook> {

    @Select("SELECT student_id, COUNT(*) as mastered_count FROM vocabulary_books WHERE mastery_level = 'MASTERED' GROUP BY student_id ORDER BY mastered_count DESC")
    List<Map<String, Object>> selectAllMasteredWordRanking();

    @Select("<script>SELECT student_id, COUNT(*) as mastered_count FROM vocabulary_books WHERE mastery_level = 'MASTERED' AND student_id IN <foreach collection='studentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> GROUP BY student_id ORDER BY mastered_count DESC</script>")
    List<Map<String, Object>> selectMasteredWordRankingByStudentIds(@Param("studentIds") List<Long> studentIds);

    @Select("SELECT COUNT(*) FROM vocabulary_books " +
            "WHERE student_id = #{studentId} AND next_review_time IS NOT NULL AND next_review_time < NOW()")
    int countOverdueReviews(@Param("studentId") Long studentId);

    @Select("SELECT COUNT(*) FROM vocabulary_books " +
            "WHERE student_id = #{studentId} AND next_review_time IS NOT NULL")
    int countDueReviews(@Param("studentId") Long studentId);

    // ─── Admin SM-2 统计查询 ───

    /** 全平台各掌握度档位的词条数 */
    @Select("SELECT mastery_level, COUNT(*) AS cnt FROM vocabulary_books GROUP BY mastery_level")
    List<Map<String, Object>> selectMasteryLevelDistribution();

    /** 全平台 EF 分布（按 0.5 步长分桶） */
    @Select("SELECT CONCAT(FLOOR(easiness_factor * 2) / 2, '-', FLOOR(easiness_factor * 2) / 2 + 0.5) AS ef_range, " +
            "COUNT(*) AS cnt FROM vocabulary_books WHERE easiness_factor IS NOT NULL " +
            "GROUP BY ef_range ORDER BY MIN(easiness_factor)")
    List<Map<String, Object>> selectEFDistribution();

    /** 全平台间隔分布（分桶） */
    @Select("SELECT " +
            "CASE " +
            "  WHEN interval_days = 0 THEN '0（新词）' " +
            "  WHEN interval_days <= 1 THEN '1天' " +
            "  WHEN interval_days <= 7 THEN '2-7天' " +
            "  WHEN interval_days <= 30 THEN '8-30天' " +
            "  WHEN interval_days <= 90 THEN '31-90天' " +
            "  ELSE '90天+' " +
            "END AS bucket, " +
            "COUNT(*) AS cnt " +
            "FROM vocabulary_books " +
            "GROUP BY bucket")
    List<Map<String, Object>> selectIntervalDistribution();

    /** 每个学生的汇总数据（用于排行和分布） */
    @Select("SELECT vb.student_id, u.username, u.nickname, " +
            "COUNT(*) AS total_words, " +
            "SUM(CASE WHEN vb.mastery_level = 'NEW' THEN 1 ELSE 0 END) AS new_count, " +
            "SUM(CASE WHEN vb.mastery_level IN ('LEARNING','FAMILIAR') THEN 1 ELSE 0 END) AS learning_count, " +
            "SUM(CASE WHEN vb.mastery_level = 'MASTERED' THEN 1 ELSE 0 END) AS mastered_count, " +
            "ROUND(AVG(vb.easiness_factor), 2) AS avg_ef, " +
            "ROUND(AVG(vb.interval_days), 1) AS avg_interval, " +
            "SUM(vb.review_count) AS total_reviews, " +
            "SUM(CASE WHEN vb.next_review_time IS NOT NULL AND vb.next_review_time < NOW() THEN 1 ELSE 0 END) AS overdue_count, " +
            "MAX(vb.last_reviewed) AS last_active " +
            "FROM vocabulary_books vb " +
            "LEFT JOIN users u ON vb.student_id = u.id " +
            "GROUP BY vb.student_id, u.username, u.nickname " +
            "ORDER BY total_words DESC")
    List<Map<String, Object>> selectPerStudentSummary();

    /** 来源类型分布 */
    @Select("SELECT source_type, COUNT(*) AS cnt FROM vocabulary_books GROUP BY source_type")
    List<Map<String, Object>> selectSourceTypeDistribution();

    /** 近 N 天每日新增词数趋势 */
    @Select("SELECT DATE(added_time) AS day, COUNT(*) AS new_words " +
            "FROM vocabulary_books " +
            "WHERE added_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(added_time) ORDER BY day")
    List<Map<String, Object>> selectDailyNewTrend(@Param("days") int days);

    /** 近 N 天每日复习人次趋势 */
    @Select("SELECT DATE(last_reviewed) AS day, COUNT(*) AS review_count " +
            "FROM vocabulary_books " +
            "WHERE last_reviewed >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(last_reviewed) ORDER BY day")
    List<Map<String, Object>> selectDailyReviewTrend(@Param("days") int days);

    /** 全平台汇总 */
    @Select("SELECT COUNT(DISTINCT student_id) AS active_students, " +
            "COUNT(*) AS total_entries, " +
            "SUM(CASE WHEN mastery_level = 'NEW' THEN 1 ELSE 0 END) AS new_count, " +
            "SUM(CASE WHEN mastery_level IN ('LEARNING','FAMILIAR') THEN 1 ELSE 0 END) AS learning_count, " +
            "SUM(CASE WHEN mastery_level = 'MASTERED' THEN 1 ELSE 0 END) AS mastered_count, " +
            "ROUND(AVG(easiness_factor), 2) AS avg_ef, " +
            "ROUND(AVG(interval_days), 1) AS avg_interval, " +
            "SUM(review_count) AS total_reviews, " +
            "SUM(CASE WHEN next_review_time IS NOT NULL AND next_review_time < NOW() THEN 1 ELSE 0 END) AS overdue_count " +
            "FROM vocabulary_books")
    Map<String, Object> selectPlatformSummary();
}