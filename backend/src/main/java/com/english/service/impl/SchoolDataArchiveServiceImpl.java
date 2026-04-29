package com.english.service.impl;

import com.english.config.SchoolDataArchiveProperties;
import com.english.service.SchoolDataArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 学校数据归档服务实现
 */
@Service
public class SchoolDataArchiveServiceImpl implements SchoolDataArchiveService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataArchiveServiceImpl.class);

    @Autowired
    private SchoolDataArchiveProperties archiveProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Map<String, Object> archiveDataSource(String dataSource) {
        Map<String, Object> result = new HashMap<>();
        result.put("dataSource", dataSource);

        if (!archiveProperties.isEnabled()) {
            result.put("status", "SKIPPED");
            result.put("reason", "归档功能未启用");
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
            int thresholdYears = archiveProperties.getThresholdYears();

            // 计算阈值年份
            String thresholdYear = String.valueOf(LocalDate.now().getYear() - thresholdYears);

            // 创建归档表（如果不存在）
            String createArchiveTableSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s LIKE %s", archiveTable, tableName);
            jdbcTemplate.execute(createArchiveTableSql);

            // 移动旧数据到归档表
            String insertSql = String.format(
                "INSERT INTO %s SELECT * FROM %s WHERE %s < ? AND id NOT IN (SELECT id FROM %s)",
                archiveTable, tableName, timeField, archiveTable);
            int archived = jdbcTemplate.update(insertSql, thresholdYear);

            // 删除已归档的数据
            String deleteSql = String.format(
                "DELETE FROM %s WHERE %s < ?", tableName, timeField);
            int deleted = jdbcTemplate.update(deleteSql, thresholdYear);

            result.put("status", "SUCCESS");
            result.put("archived", archived);
            result.put("deleted", deleted);
            result.put("archiveTable", archiveTable);
            logger.info("数据源 {} 归档完成: 归档 {} 条, 删除 {} 条", dataSource, archived, deleted);

        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            logger.error("数据源 {} 归档失败", dataSource, e);
        }

        return result;
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
