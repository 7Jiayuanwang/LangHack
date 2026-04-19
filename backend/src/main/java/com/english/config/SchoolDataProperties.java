package com.english.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
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

    /** API Base URL 白名单域名 */
    private static final List<String> API_ALLOWED_DOMAINS = Arrays.asList(
            "cdsp.imau.edu.cn",
            "api.imau.edu.cn",
            "imau.edu.cn"
    );

    /** ETL JDBC URL 白名单主机 */
    private static final List<String> ETL_ALLOWED_HOSTS = Arrays.asList(
            "school-db.imau.edu.cn",
            "localhost"
    );

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
        /** API 基础URL（已废弃，每个接口使用完整独立 URL） */
        @Deprecated
        private String baseUrl = "https://cdsp.imau.edu.cn";

        /** 应用 ID（由学校数据平台分配） */
        private String appId;

        /** 应用 Secret（由学校数据平台分配，用于签名计算） */
        private String appSecret;

        /** 连接超时（毫秒） */
        private int connectTimeout = 5000;

        /** 读取超时（毫秒） */
        private int readTimeout = 30000;

        /** 重试次数 */
        private int retryAttempts = 3;

        /** API 端点完整 URL 映射（基于官方接口文档） */
        private List<EndpointMapping> endpoints = Arrays.asList(
                new EndpointMapping("administrative_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337"),
                new EndpointMapping("school_course", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529094506435"),
                new EndpointMapping("teaching_class", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_172180403086015"),
                new EndpointMapping("course_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529037780151"),
                new EndpointMapping("course_selection", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529480287192"),
                new EndpointMapping("level_exam_scores", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0076"),
                new EndpointMapping("teaching_plans", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171528965502646"),
                new EndpointMapping("teacher_courses", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529464567780"),
                new EndpointMapping("student_schedule", "https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171529439727434")
        );

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            validateApiBaseUrl(baseUrl);
            this.baseUrl = baseUrl;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        /**
         * 计算签名（官方要求的认证方式）
         * <p>
         * 官方文档定义（行政班基本信息接口文档.pdf 第 1 页 2.1 节）：
         *   "签名，通过 appId + appSecret + timestamp 拼接成字符串，
         *    对拼接后的字符串做 md5 变换，然后再对变换后的字符串做 base64 变换"
         * <p>
         * 注意"变换后的字符串"指 MD5 的 <b>十六进制字符串表示</b>（32 字符小写），
         * 而非 MD5 的 16 字节原始摘要。Base64 编码的是这个 hex 字符串的 UTF-8 字节。
         * <p>
         * 公式: sign = Base64( hex(MD5(appId + appSecret + timestamp)).getBytes(UTF-8) )
         *
         * @param timestamp 当前时间戳（毫秒）
         * @return Base64 编码的签名，如果 appId/appSecret 为空返回 null
         */
        public String sign(long timestamp) {
            if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
                return null;
            }
            try {
                String raw = appId + appSecret + timestamp;
                MessageDigest md5 = MessageDigest.getInstance("MD5");
                byte[] digest = md5.digest(raw.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(digest.length * 2);
                for (byte b : digest) {
                    hex.append(String.format("%02x", b));
                }
                return Base64.getEncoder().encodeToString(hex.toString().getBytes(StandardCharsets.UTF_8));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("MD5 algorithm not available in JRE", e);
            }
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

        /** API 完整 URL（如 https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_171094991198337） */
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
            validateEtlJdbcUrl(jdbcUrl);
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

    // =========================================================================
    // URL 安全校验
    // =========================================================================

    /**
     * 校验 API Base URL 是否在白名单域名内
     * 防止SSRF攻击：管理员配置 api_base_url 指向内网元数据服务等
     */
    private static void validateApiBaseUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("API Base URL 缺少主机名: " + url);
            }
            boolean allowed = API_ALLOWED_DOMAINS.stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
            if (!allowed) {
                throw new IllegalArgumentException(
                        "API Base URL 域名不在白名单中: " + host + "，允许的域名: " + API_ALLOWED_DOMAINS);
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("API Base URL 格式无效: " + url);
        }
    }

    /**
     * 校验 ETL JDBC URL 是否在白名单主机内
     * 防止SSRF/数据库直连攻击：管理员配置 etl_jdbc_url 指向任意数据库
     */
    private static void validateEtlJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) return;
        if (!jdbcUrl.startsWith("jdbc:mysql://")) {
            throw new IllegalArgumentException("ETL JDBC URL 必须以 jdbc:mysql:// 开头: " + jdbcUrl);
        }
        // 提取主机名: jdbc:mysql://host:port/db → host
        String afterProtocol = jdbcUrl.substring("jdbc:mysql://".length());
        String hostPort = afterProtocol.split("/", 2)[0].split("\\?")[0];
        String host = hostPort.split(":")[0];
        boolean allowed = ETL_ALLOWED_HOSTS.stream()
                .anyMatch(allowedHost -> host.equals(allowedHost));
        if (!allowed) {
            throw new IllegalArgumentException(
                    "ETL JDBC URL 主机不在白名单中: " + host + "，允许的主机: " + ETL_ALLOWED_HOSTS);
        }
    }
}
