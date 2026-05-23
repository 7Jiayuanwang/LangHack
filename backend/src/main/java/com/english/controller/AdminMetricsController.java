package com.english.controller;

import com.english.dto.Result;
import com.english.mapper.LevelExamScoreMapper;
import com.english.mapper.LearningRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/metrics")
@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")
public class AdminMetricsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminMetricsController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 四六级成绩对比摘要：使用平台 vs 未使用平台的通过率
     * 仅返回汇总数据，严禁返回个人成绩
     */
    @GetMapping("/cet-comparison")
    public Result<?> getCetComparison() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            // 使用平台的学生（有学习记录）
            String sqlUsed = """
                SELECT
                  COUNT(*) AS total_students,
                  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) AS passed,
                  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 2) AS pass_rate
                FROM level_exam_scores les
                WHERE EXISTS (
                  SELECT 1 FROM learning_records lr
                  WHERE lr.student_id = les.student_id
                )
                """;

            // 未使用平台的学生（无学习记录）
            String sqlUnused = """
                SELECT
                  COUNT(*) AS total_students,
                  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) AS passed,
                  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 2) AS pass_rate
                FROM level_exam_scores les
                WHERE NOT EXISTS (
                  SELECT 1 FROM learning_records lr
                  WHERE lr.student_id = les.student_id
                )
                """;

            Map<String, Object> usedRow = jdbcTemplate.queryForMap(sqlUsed);
            Map<String, Object> unusedRow = jdbcTemplate.queryForMap(sqlUnused);

            double usedRate = usedRow.get("pass_rate") != null
                    ? ((Number) usedRow.get("pass_rate")).doubleValue() : 0.0;
            double unusedRate = unusedRow.get("pass_rate") != null
                    ? ((Number) unusedRow.get("pass_rate")).doubleValue() : 0.0;

            result.put("used", Map.of(
                    "totalStudents", toLong(usedRow.get("total_students")),
                    "passed", toLong(usedRow.get("passed")),
                    "passRate", usedRate
            ));
            result.put("unused", Map.of(
                    "totalStudents", toLong(unusedRow.get("total_students")),
                    "passed", toLong(unusedRow.get("passed")),
                    "passRate", unusedRate
            ));
            result.put("improvement", Math.round((usedRate - unusedRate) * 100.0) / 100.0);
            result.put("totalScoreRecords", toLong(usedRow.get("total_students")) + toLong(unusedRow.get("total_students")));

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取四六级对比数据失败", e);
            return Result.error("获取四六级对比数据失败");
        }
    }

    /**
     * 四六级成绩详细分析：按考试类型、分数段分布、时间趋势
     * 仅返回汇总数据，严禁返回个人成绩
     */
    @GetMapping("/cet-detailed")
    public Result<?> getCetDetailed() {
        try {
            Map<String, Object> result = new LinkedHashMap<>();

            // 按考试类型分组的通过率
            String sqlByType = """
                SELECT
                  exam_type,
                  COUNT(*) AS total,
                  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) AS passed,
                  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 2) AS pass_rate,
                  ROUND(AVG(score), 2) AS avg_score,
                  MIN(score) AS min_score,
                  MAX(score) AS max_score
                FROM level_exam_scores
                WHERE exam_type IS NOT NULL
                GROUP BY exam_type
                ORDER BY exam_type
                """;
            List<Map<String, Object>> byType = jdbcTemplate.queryForList(sqlByType);
            result.put("byExamType", byType);

            // 分数段分布（使用平台 vs 未使用平台）
            String sqlDistUsed = """
                SELECT
                  CASE
                    WHEN score < 300 THEN '300以下'
                    WHEN score < 350 THEN '300-349'
                    WHEN score < 400 THEN '350-399'
                    WHEN score < 425 THEN '400-424'
                    WHEN score < 500 THEN '425-499'
                    WHEN score < 550 THEN '500-549'
                    ELSE '550以上'
                  END AS score_range,
                  COUNT(*) AS count
                FROM level_exam_scores les
                WHERE EXISTS (SELECT 1 FROM learning_records lr WHERE lr.student_id = les.student_id)
                GROUP BY score_range
                ORDER BY MIN(score)
                """;
            String sqlDistUnused = """
                SELECT
                  CASE
                    WHEN score < 300 THEN '300以下'
                    WHEN score < 350 THEN '300-349'
                    WHEN score < 400 THEN '350-399'
                    WHEN score < 425 THEN '400-424'
                    WHEN score < 500 THEN '425-499'
                    WHEN score < 550 THEN '500-549'
                    ELSE '550以上'
                  END AS score_range,
                  COUNT(*) AS count
                FROM level_exam_scores les
                WHERE NOT EXISTS (SELECT 1 FROM learning_records lr WHERE lr.student_id = les.student_id)
                GROUP BY score_range
                ORDER BY MIN(score)
                """;
            result.put("scoreDistUsed", jdbcTemplate.queryForList(sqlDistUsed));
            result.put("scoreDistUnused", jdbcTemplate.queryForList(sqlDistUnused));

            // 按年份的通过率趋势
            String sqlTrend = """
                SELECT
                  YEAR(exam_date) AS exam_year,
                  exam_type,
                  COUNT(*) AS total,
                  SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) AS passed,
                  ROUND(SUM(CASE WHEN score >= 425 THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 2) AS pass_rate
                FROM level_exam_scores
                WHERE exam_date IS NOT NULL
                GROUP BY exam_year, exam_type
                ORDER BY exam_year, exam_type
                """;
            result.put("trend", jdbcTemplate.queryForList(sqlTrend));

            return Result.success(result);
        } catch (Exception e) {
            logger.error("获取四六级详细分析失败", e);
            return Result.error("获取四六级详细分析失败");
        }
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        return ((Number) val).longValue();
    }
}
