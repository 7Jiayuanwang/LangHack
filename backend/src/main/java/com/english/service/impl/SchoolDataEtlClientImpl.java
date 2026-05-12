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
        List<Map<String, Object>> rows = fetchByDataSource("administrative_class", params);
        String collegeCode = firstParam(params, "collegeCode", "XSH", "xsh", "ssxybm");
        String grade = firstParam(params, "grade", "NJ", "nj", "ssnj");
        return filterRows(rows, row -> matchesAny(row, collegeCode, "college_code", "collegeCode", "XSH", "xsh", "ssxybm", "所属学院编码")
                && matchesAny(row, grade, "grade", "NJ", "nj", "ssnj", "所属年级"));
    }

    @Override
    public List<Map<String, Object>> fetchSchoolCourses(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("school_course", params);
        String collegeName = firstParam(params, "collegeName", "kkyx");
        String courseCode = firstParam(params, "courseCode", "kcdm");
        return filterRows(rows, row -> matchesAny(row, collegeName, "college_name", "collegeName", "kkyx", "开课院系")
                && matchesAny(row, courseCode, "course_code", "courseCode", "kcdm", "课程代码"));
    }

    @Override
    public List<Map<String, Object>> fetchTeachingClasses(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("teaching_class", params);
        String courseCode = firstParam(params, "courseCode", "kcdm", "KCH", "kch");
        return filterRows(rows, row -> matchesAny(row, courseCode, "course_code", "courseCode", "kcdm", "KCH", "kch", "课程号", "课程代码"));
    }

    @Override
    public List<Map<String, Object>> fetchCourseSchedules(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("course_schedule", params);
        String classId = firstParam(params, "classId", "xkkch", "XKKCH");
        String dayOfWeek = firstParam(params, "dayOfWeek", "skxqj");
        String period = firstParam(params, "period", "skjc");
        return filterRows(rows, row -> matchesAny(row, classId, "class_id", "classId", "xkkch", "XKKCH", "选课课程号")
                && matchesAny(row, dayOfWeek, "day_of_week", "dayOfWeek", "skxqj", "上课星期几")
                && matchesAny(row, period, "period", "skjc", "上课节次"));
    }

    @Override
    public List<Map<String, Object>> fetchCourseSelections(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("course_selection", params);
        String studentId = firstParam(params, "studentId", "student_id", "XH", "xh");
        String classId = firstParam(params, "classId", "courseClassCode", "KCBH", "kcbh");
        return filterRows(rows, row -> matchesAny(row, studentId, "student_id", "studentId", "XH", "xh", "学号")
                && matchesAny(row, classId, "class_id", "classId", "courseClassCode", "KCBH", "kcbh", "课程班号"));
    }

    @Override
    public List<Map<String, Object>> fetchLevelExamScores(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("level_exam_scores", params);
        String studentId = firstParam(params, "studentId", "student_id", "xh", "XH");
        return filterRows(rows, row -> matchesAny(row, studentId, "student_id", "studentId", "xh", "XH", "学号"));
    }

    @Override
    public List<Map<String, Object>> fetchTeachingPlans(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("teaching_plans", params);
        String grade = firstParam(params, "grade", "nj");
        return filterRows(rows, row -> matchesAny(row, grade, "grade", "nj", "年级"));
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
        Map<String, String> params = new LinkedHashMap<>();
        if (hasText(studentId)) {
            params.put("studentId", studentId);
        }
        if (hasText(academicYear)) {
            params.put("academicYear", academicYear);
        }
        if (semester != null) {
            params.put("semester", String.valueOf(semester));
        }
        List<Map<String, Object>> rows = fetchByDataSource("student_schedule", params);
        if (!hasText(studentId) && !hasText(academicYear) && semester == null) {
            return rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (matchesAny(row, studentId, "student_id", "studentId", "xh", "XH")
                    && matchesAny(row, academicYear, "academic_year", "academicYear", "xn", "XN")
                    && matchesSemester(row, semester)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    @Override
    public List<Map<String, Object>> fetchTeacherCourses(Map<String, String> params) {
        List<Map<String, Object>> rows = fetchByDataSource("teacher_courses", params);
        String teacherId = firstParam(params, "teacherId", "rkjsh");
        String courseCode = firstParam(params, "courseCode", "kcdm");
        String academicYear = firstParam(params, "academicYear", "xn");
        Integer semester = parseSemesterParam(firstParam(params, "semester", "xq"));
        return filterRows(rows, row -> matchesAny(row, teacherId, "teacher_id", "teacherId", "rkjsh", "任课教师号")
                && matchesAny(row, courseCode, "course_code", "courseCode", "kcdm", "课程代码")
                && matchesAny(row, academicYear, "academic_year", "academicYear", "xn", "学年")
                && matchesSemester(row, semester));
    }

    @Override
    public boolean testConnection() {
        String jdbcUrl = properties.getEtl().getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            logger.warn("学校 ETL JDBC URL 未配置，跳过连接测试");
            return false;
        }

        try (Connection conn = getOrCreateDataSource().getConnection()) {
            boolean valid = conn.isValid(properties.getEtl().getConnectionTimeout() / 1000);
            if (!valid) {
                logger.error("学校 ETL 数据库连接无效 jdbcUrl={} timeout={}ms",
                        jdbcUrl, properties.getEtl().getConnectionTimeout());
            }
            return valid;
        } catch (SQLException e) {
            logger.error("学校 ETL 数据库连接测试失败 jdbcUrl={} username={} err={}",
                    jdbcUrl, properties.getEtl().getUsername(), e.getMessage(), e);
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
            logger.error("学校 ETL 未配置数据源 SQL 映射 dataSource={}", dataSource);
            return Collections.emptyList();
        }

        // If params contain updatedSince, inject it as the first parameter
        String updatedSince = params != null ? params.get("updatedSince") : null;

        try (Connection conn = getOrCreateDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql,
                     ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            stmt.setQueryTimeout(properties.getEtl().getQueryTimeout() / 1000);

            if (sql.contains("?")) {
                stmt.setString(1, hasText(updatedSince) ? updatedSince : "1970-01-01 00:00:00");
            }

            logger.info("学校 ETL SQL 执行中 dataSource={} updatedSince={} sql={}",
                    dataSource, updatedSince, sql);

            try (ResultSet rs = stmt.executeQuery()) {
                return resultSetToList(rs);
            }
        } catch (SQLException e) {
            logger.error("学校 ETL SQL 执行失败 dataSource={} jdbcUrl={} sqlState={} errorCode={} err={}",
                    dataSource, properties.getEtl().getJdbcUrl(),
                    e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private List<Map<String, Object>> filterRows(List<Map<String, Object>> rows,
                                                 java.util.function.Predicate<Map<String, Object>> predicate) {
        if (rows == null || rows.isEmpty()) {
            return rows == null ? Collections.emptyList() : rows;
        }
        if (predicate == null) {
            return rows;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (predicate.test(row)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private String firstParam(Map<String, String> params, String... keys) {
        if (params == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            String value = params.get(key);
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer parseSemesterParam(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if ("1".equals(normalized) || "\u79cb".equals(normalized)) {
            return 1;
        }
        if ("2".equals(normalized) || "\u6625".equals(normalized)) {
            return 2;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean matchesAny(Map<String, Object> row, String expected, String... keys) {
        if (!hasText(expected)) {
            return true;
        }
        String normalizedExpected = expected.trim();
        for (String key : keys) {
            Object value = getIgnoreCase(row, key);
            if (value != null && normalizedExpected.equals(String.valueOf(value).trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSemester(Map<String, Object> row, Integer expected) {
        if (expected == null) {
            return true;
        }
        Object value = firstValue(row, "semester", "xq", "XQ");
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim();
        if (String.valueOf(expected).equals(normalized)) {
            return true;
        }
        return (expected == 1 && "\u79cb".equals(normalized))
                || (expected == 2 && "\u6625".equals(normalized));
    }

    private Object firstValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = getIgnoreCase(row, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object getIgnoreCase(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return null;
        }
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Create a dedicated HikariCP DataSource for school database
     * (separate from the main app's master/slave DataSource)
     */
    private DataSource createSchoolDataSource() {
        SchoolDataProperties.EtlProperties etl = properties.getEtl();

        // 校验必填配置
        String jdbcUrl = etl.getJdbcUrl();
        if (jdbcUrl == null || jdbcUrl.isEmpty() || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("ETL jdbcUrl is required but not configured");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
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
