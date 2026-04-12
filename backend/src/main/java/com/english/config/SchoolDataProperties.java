package com.english.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 学校数据接入配置属性
 * <p>
 * 支持两种接入模式（由 school.data.source 控制）:
 * - api: 通过学校REST API获取数据（默认）
 * - etl: 通过JDBC直连学校数据库获取数据
 * <p>
 * 配置前缀: school.data
 */
@Configuration
@ConfigurationProperties(prefix = "school.data")
public class SchoolDataProperties {

    /** 数据源类型: api 或 etl */
    private String source = "api";

    private ApiProperties api = new ApiProperties();
    private EtlProperties etl = new EtlProperties();
    private SyncProperties sync = new SyncProperties();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public ApiProperties getApi() {
        return api;
    }

    public void setApi(ApiProperties api) {
        this.api = api;
    }

    public EtlProperties getEtl() {
        return etl;
    }

    public void setEtl(EtlProperties etl) {
        this.etl = etl;
    }

    public SyncProperties getSync() {
        return sync;
    }

    public void setSync(SyncProperties sync) {
        this.sync = sync;
    }

    // =========================================================================
    // API 模式配置
    // =========================================================================

    public static class ApiProperties {
        /** API 基础URL */
        private String baseUrl = "https://api.imau.edu.cn";

        /** 认证 Token */
        private String token;

        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;

        /** 读取超时（毫秒） */
        private int readTimeout = 30000;

        /** 重试次数 */
        private int retryAttempts = 3;

        /** API 端点路径映射 */
        private List<EndpointMapping> endpoints = Arrays.asList(
                new EndpointMapping("administrative_class", "/api/xzxx"),
                new EndpointMapping("school_course", "/api/kcxx"),
                new EndpointMapping("teaching_class", "/api/bzkcxx"),
                new EndpointMapping("course_schedule", "/api/kcap"),
                new EndpointMapping("course_selection", "/api/xsxk"),
                new EndpointMapping("level_exam_scores", "/api/djcj"),
                new EndpointMapping("teaching_plans", "/api/jxjh"),
                new EndpointMapping("teacher_courses", "/api/jssk"),
                new EndpointMapping("student_schedule", "/api/xskb")
        );

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public List<EndpointMapping> getEndpoints() {
            return endpoints;
        }

        public void setEndpoints(List<EndpointMapping> endpoints) {
            this.endpoints = endpoints;
        }

        /**
         * 根据数据源名称获取端点路径
         */
        public String getEndpointPath(String dataSource) {
            return endpoints.stream()
                    .filter(e -> e.getDataSource().equals(dataSource))
                    .map(EndpointMapping::getPath)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * API 端点映射
     */
    public static class EndpointMapping {
        /** 数据源名称（如 administrative_class） */
        private String dataSource;

        /** API 端点路径（如 /api/xzxx） */
        private String path;

        public EndpointMapping() {
        }

        public EndpointMapping(String dataSource, String path) {
            this.dataSource = dataSource;
            this.path = path;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    // =========================================================================
    // ETL 模式配置
    // =========================================================================

    public static class EtlProperties {
        /** JDBC 连接URL */
        private String jdbcUrl = "jdbc:mysql://school-db.imau.edu.cn:3306/school_db";

        /** 数据库用户名 */
        private String username;

        /** 数据库密码 */
        private String password;

        /** 驱动类名 */
        private String driverClassName = "com.mysql.cj.jdbc.Driver";

        /** 连接超时（毫秒） */
        private int connectionTimeout = 30000;

        /** 查询超时（毫秒） */
        private int queryTimeout = 60000;

        /** ETL 查询视图/表映射 */
        private List<QueryMapping> queries = Arrays.asList(
                new QueryMapping("administrative_class", "SELECT * FROM v_administrative_class WHERE updated_at >= ?"),
                new QueryMapping("school_course", "SELECT * FROM v_school_course WHERE updated_at >= ?"),
                new QueryMapping("teaching_class", "SELECT * FROM v_teaching_class WHERE updated_at >= ?"),
                new QueryMapping("course_schedule", "SELECT * FROM v_course_schedule WHERE updated_at >= ?"),
                new QueryMapping("course_selection", "SELECT * FROM v_course_selection WHERE updated_at >= ?"),
                new QueryMapping("level_exam_scores", "SELECT * FROM v_level_exam_scores WHERE updated_at >= ?"),
                new QueryMapping("teaching_plans", "SELECT * FROM v_teaching_plans WHERE updated_at >= ?")
        );

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getQueryTimeout() {
            return queryTimeout;
        }

        public void setQueryTimeout(int queryTimeout) {
            this.queryTimeout = queryTimeout;
        }

        public List<QueryMapping> getQueries() {
            return queries;
        }

        public void setQueries(List<QueryMapping> queries) {
            this.queries = queries;
        }

        /**
         * 根据数据源名称获取查询SQL
         */
        public String getQuerySql(String dataSource) {
            return queries.stream()
                    .filter(q -> q.getDataSource().equals(dataSource))
                    .map(QueryMapping::getSql)
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * ETL 查询映射
     */
    public static class QueryMapping {
        /** 数据源名称 */
        private String dataSource;

        /** 查询SQL */
        private String sql;

        public QueryMapping() {
        }

        public QueryMapping(String dataSource, String sql) {
            this.dataSource = dataSource;
            this.sql = sql;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
        }

        public String getSql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }
    }

    // =========================================================================
    // 同步调度配置
    // =========================================================================

    public static class SyncProperties {
        /** 是否启用同步调度 */
        private boolean enabled = false;

        /** 全量同步 cron */
        private String fullSyncCron = "0 0 2 * * ?";

        /** 增量同步 cron */
        private String incrementalSyncCron = "0 0 * * * ?";

        /** 数据校验 cron */
        private String validationCron = "0 0 4 * * ?";

        /** 批量大小 */
        private int batchSize = 500;

        /** 同步超时（分钟） */
        private int syncTimeoutMinutes = 60;

        /** 启动时是否执行全量同步 */
        private boolean fullSyncOnStartup = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFullSyncCron() {
            return fullSyncCron;
        }

        public void setFullSyncCron(String fullSyncCron) {
            this.fullSyncCron = fullSyncCron;
        }

        public String getIncrementalSyncCron() {
            return incrementalSyncCron;
        }

        public void setIncrementalSyncCron(String incrementalSyncCron) {
            this.incrementalSyncCron = incrementalSyncCron;
        }

        public String getValidationCron() {
            return validationCron;
        }

        public void setValidationCron(String validationCron) {
            this.validationCron = validationCron;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getSyncTimeoutMinutes() {
            return syncTimeoutMinutes;
        }

        public void setSyncTimeoutMinutes(int syncTimeoutMinutes) {
            this.syncTimeoutMinutes = syncTimeoutMinutes;
        }

        public boolean isFullSyncOnStartup() {
            return fullSyncOnStartup;
        }

        public void setFullSyncOnStartup(boolean fullSyncOnStartup) {
            this.fullSyncOnStartup = fullSyncOnStartup;
        }
    }
}
