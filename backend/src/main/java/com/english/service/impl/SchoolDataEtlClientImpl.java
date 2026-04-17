package com.english.service.impl;

import com.english.config.SchoolDataProperties;
import com.english.service.SchoolDataApiClient;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * 学校数据ETL客户端实现 - 通过JDBC直连学校数据库获取数据
 * <p>
 * 仅在 school.data.source=etl 时激活。
 * 使用独立的 HikariCP 连接池，不影响主库的读写分离路由。
 * <p>
 * 查询使用配置的视图/表映射（SchoolDataProperties.EtlProperties.queries），
 * 支持增量查询（通过 updatedSince 参数）。
 */
@Service("schoolDataApiClientEtl")
public class SchoolDataEtlClientImpl implements SchoolDataApiClient {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataEtlClientImpl.class);

    private final SchoolDataProperties properties;
    private volatile DataSource schoolDataSource;
    private final Object dataSourceLock = new Object();

    public SchoolDataEtlClientImpl(SchoolDataProperties properties) {
        this.properties = properties;
        // Lazy init: DataSource created on first use or explicit recreateDataSource() call
        logger.info("SchoolDataEtlClient initialized (lazy) - jdbcUrl: {}, queryTimeout: {}ms",
                properties.getEtl().getJdbcUrl(),
                properties.getEtl().getQueryTimeout());
    }

    /**
     * 获取或创建 DataSource（双重检查锁）
     */
    private DataSource getOrCreateDataSource() {
        DataSource ds = schoolDataSource;
        if (ds == null) {
            synchronized (dataSourceLock) {
                ds = schoolDataSource;
                if (ds == null) {
                    ds = createSchoolDataSource();
                    schoolDataSource = ds;
                }
            }
        }
        return ds;
    }

    /**
     * 重建 DataSource（配置变更后调用）
     */
    public void recreateDataSource() {
        synchronized (dataSourceLock) {
            DataSource old = schoolDataSource;
            schoolDataSource = createSchoolDataSource();
            if (old instanceof HikariDataSource) {
                ((HikariDataSource) old).close();
                logger.info("Old School ETL DataSource closed after recreation");
            }
        }
    }

    @Override
    public List<Map<String, Object>> fetchAdministrativeClasses(Map<String, String> params) {
        return fetchByDataSource("administrative_class", params);
    }

    @Override
    public List<Map<String, Object>> fetchSchoolCourses(Map<String, String> params) {
        return fetchByDataSource("school_course", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingClasses(Map<String, String> params) {
        return fetchByDataSource("teaching_class", params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSchedules(Map<String, String> params) {
        return fetchByDataSource("course_schedule", params);
    }

    @Override
    public List<Map<String, Object>> fetchCourseSelections(Map<String, String> params) {
        return fetchByDataSource("course_selection", params);
    }

    @Override
    public List<Map<String, Object>> fetchLevelExamScores(Map<String, String> params) {
        return fetchByDataSource("level_exam_scores", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlans(Map<String, String> params) {
        return fetchByDataSource("teaching_plans", params);
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlanCourses(String planId) {
        // ETL模式下不支持按计划ID查询课程明细，返回空列表
        // 教学计划课程数据应通过配置专门的查询SQL来获取
        logger.warn("ETL mode does not support fetchTeachingPlanCourses by planId. " +
                "Configure a dedicated query in school.data.etl.queries if needed.");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchStudentSchedules(String studentId, String academicYear, Integer semester) {
        // ETL模式下不支持按学生ID查询课表，返回空列表
        logger.warn("ETL mode does not support fetchStudentSchedules by student parameters. " +
                "Configure a dedicated query in school.data.etl.queries if needed.");
        return Collections.emptyList();
    }

    @Override
    public List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params) {
        return fetchByDataSource("teacher_courses", params);
    }

    @Override
    public boolean testConnection() {
        String jdbcUrl = properties.getEtl().getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            logger.warn("School ETL JDBC URL is not configured");
            return false;
        }

        try (Connection conn = getOrCreateDataSource().getConnection()) {
            return conn.isValid(properties.getEtl().getConnectionTimeout() / 1000);
        } catch (SQLException e) {
            logger.error("School ETL database connection test failed", e);
            return false;
        }
    }

    @PreDestroy
    public void destroy() {
        synchronized (dataSourceLock) {
            if (schoolDataSource instanceof HikariDataSource) {
                ((HikariDataSource) schoolDataSource).close();
                logger.info("School ETL DataSource closed");
            }
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * 通过数据源名称获取数据（使用配置的查询映射）
     */
    private List<Map<String, Object>> fetchByDataSource(String dataSource, Map<String, String> params) {
        String sql = properties.getEtl().getQuerySql(dataSource);
        if (sql == null) {
            logger.error("No ETL query mapping configured for data source: {}", dataSource);
            return Collections.emptyList();
        }

        // If params contain updatedSince, inject it as the first parameter
        String updatedSince = params != null ? params.get("updatedSince") : null;

        try (Connection conn = getOrCreateDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            stmt.setQueryTimeout(properties.getEtl().getQueryTimeout() / 1000);

            if (updatedSince != null && !updatedSince.isEmpty()) {
                stmt.setString(1, updatedSince);
            }

            logger.info("Executing ETL query for {}: {} (updatedSince={})", dataSource, sql, updatedSince);

            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        } catch (SQLException e) {
            logger.error("Failed to execute ETL query for {}: {}", dataSource, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Convert ResultSet to List of Maps
     */
    private List<Map<String, Object>> resultSetToList(ResultSet rs) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Map<String, Object>> rows = new ArrayList<>();

        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnLabel = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                // Convert SQL types to safe Java types
                row.put(columnLabel, convertSqlValue(value));
            }
            rows.add(row);
        }

        logger.debug("ETL query returned {} rows", rows.size());
        return rows;
    }

    /**
     * Convert SQL types to safe Java types for JSON serialization
     */
    private Object convertSqlValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate().toString();
        }
        if (value instanceof java.sql.Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toString();
        }
        if (value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof java.math.BigDecimal) {
            return value; // Keep as BigDecimal for precision
        }
        if (value instanceof java.sql.Clob) {
            try {
                return ((java.sql.Clob) value).getSubString(1, (int) ((java.sql.Clob) value).length());
            } catch (Exception e) {
                return value.toString();
            }
        }
        if (value instanceof java.sql.Blob) {
            // Don't convert blobs to strings - they could be large binary data
            return "[BLOB]";
        }
        return value;
    }

    /**
     * Create a dedicated HikariCP DataSource for school database
     * (separate from the main app's master/slave DataSource)
     */
    private DataSource createSchoolDataSource() {
        SchoolDataProperties.EtlProperties etl = properties.getEtl();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(etl.getJdbcUrl());
        config.setUsername(etl.getUsername());
        config.setPassword(etl.getPassword());
        config.setDriverClassName(etl.getDriverClassName());

        // Connection pool settings
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(etl.getConnectionTimeout());
        config.setIdleTimeout(300000); // 5 minutes
        config.setMaxLifetime(600000); // 10 minutes
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("SchoolETL-Pool");

        return new HikariDataSource(config);
    }
}
