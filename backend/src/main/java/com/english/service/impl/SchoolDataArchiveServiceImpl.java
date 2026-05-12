package com.english.service.impl;

import com.english.config.SchoolDataArchiveProperties;
import com.english.service.SchoolDataArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 学校数据归档服务实现
 */
@Service
public class SchoolDataArchiveServiceImpl implements SchoolDataArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataArchiveServiceImpl.class);

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    private static final Set<String> ALLOWED_DATA_SOURCES = Set.of(
        "school_student", "school_teacher", "school_course",
        "teaching_class", "course_schedule", "course_selection",
        "level_exam_scores", "teacher_student_binding", "administrative_class",
        "teaching_plan", "teaching_plan_courses"
    );

    private void validateIdentifier(String name, String label) {
        if (name == null || !SAFE_IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("非法标识符: " + label + "=" + name);
        }
    }

    @Autowired
    private SchoolDataArchiveProperties archiveProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 自注入以确保 @Transactional 通过 Spring 代理生效（同类内部调用绕过代理）
    @Autowired
    @Lazy
    private SchoolDataArchiveServiceImpl self;

    @Override
    public Map<String, Object> archiveDataSource(String dataSource) {
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", dataSource);

        if (!archiveProperties.isEnabled()) {
            result.put("status", "SKIPPED");
            result.put("reason", "归档功能未启用");
            return result;
        }

        if (!ALLOWED_DATA_SOURCES.contains(dataSource)) {
            result.put("status", "FAILED");
            result.put("reason", "不支持的数据源: " + dataSource);
            logger.warn("拒绝归档不支持的数据源: {}", dataSource);
            return result;
        }

        SchoolDataArchiveProperties.DataSourceArchiveConfig config =
            archiveProperties.getDataSources().get(dataSource);

        if (config == null || !config.isEnabled()) {
            result.put("status", "SKIPPED");
            result.put("reason", "数据源未配置归档或已禁用");
            return result;
        }

        try {
            String tableName = dataSource;
            String archiveTable = config.getArchiveTable() != null ?
                config.getArchiveTable() : tableName + "_archive";
            String timeField = config.getTimeField();

            validateIdentifier(tableName, "tableName");
            validateIdentifier(archiveTable, "archiveTable");
            validateIdentifier(timeField, "timeField");

            int thresholdYears = archiveProperties.getThresholdYears();
            String thresholdYear = String.valueOf(LocalDate.now().getYear() - thresholdYears);

            // DDL 在事务外执行（MySQL DDL 会隐式提交，不能放在事务内）
            String createArchiveTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS `%s` LIKE `%s`", archiveTable, tableName);
            jdbcTemplate.execute(createArchiveTableSql);

            // INSERT + DELETE 通过代理调用以确保 @Transactional 生效
            int[] counts = self.doArchiveTransactional(tableName, archiveTable, timeField, thresholdYear);

            result.put("status", "SUCCESS");
            result.put("archived", counts[0]);
            result.put("deleted", counts[1]);
            result.put("archiveTable", archiveTable);
            logger.info("数据源 {} 归档完成: 归档 {} 条, 删除 {} 条", dataSource, counts[0], counts[1]);

        } catch (IllegalArgumentException e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            logger.error("数据源 {} 归档参数非法: {}", dataSource, e.getMessage());
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            logger.error("数据源 {} 归档失败", dataSource, e);
        }

        return result;
    }

    @Transactional
    public int[] doArchiveTransactional(String tableName, String archiveTable,
                                        String timeField, String thresholdYear) {
        String insertSql = String.format(
            "INSERT INTO `%s` SELECT * FROM `%s` WHERE `%s` < ? AND id NOT IN (SELECT id FROM `%s`)",
            archiveTable, tableName, timeField, archiveTable);
        int archived = jdbcTemplate.update(insertSql, thresholdYear);

        // 只删除已确认归档的记录，防止归档不完整时数据丢失
        String deleteSql = String.format(
            "DELETE FROM `%s` WHERE `%s` < ? AND id IN (SELECT id FROM `%s`)",
            tableName, timeField, archiveTable);
        int deleted = jdbcTemplate.update(deleteSql, thresholdYear);

        return new int[]{archived, deleted};
    }

    @Override
    public Map<String, Map<String, Object>> archiveAll() {
        Map<String, Map<String, Object>> results = new HashMap<>();

        if (!archiveProperties.isEnabled()) {
            logger.info("归档功能未启用，跳过");
            return results;
        }

        for (String dataSource : archiveProperties.getDataSources().keySet()) {
            results.put(dataSource, archiveDataSource(dataSource));
        }

        return results;
    }
}
