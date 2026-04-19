package com.english.service;

import com.english.config.SchoolDataProperties;
import com.english.config.SchoolDataSyncScheduler;
import com.english.entity.SystemConfig;
import com.english.service.impl.SchoolDataApiClientDelegate;
import com.english.service.impl.SchoolDataEtlClientImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 学校数据配置刷新服务
 * <p>
 * 从 DB system_config 读取 school_data 组配置 → 写入 SchoolDataProperties → 触发副作用
 */
@Service
public class SchoolDataConfigRefreshService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataConfigRefreshService.class);

    private final SystemConfigService configService;
    private final SchoolDataProperties properties;
    private final SchoolDataSyncScheduler scheduler;
    private final SchoolDataApiClientDelegate delegate;
    private final SchoolDataEtlClientImpl etlClient;

    /** 最近一次连接测试结果 + 时间（重启失效可接受，内存存储） */
    private final AtomicReference<Map<String, Object>> lastTestResult = new AtomicReference<>();

    /** 需要触发调度器重新调度的配置key */
    private static final Set<String> CRON_KEYS = Set.of(
            "sync_full_cron", "sync_incremental_cron", "sync_validation_cron", "sync_enabled"
    );

    /** 需要触发 ETL DataSource 重建的配置key */
    private static final Set<String> ETL_DATASOURCE_KEYS = Set.of(
            "etl_jdbc_url", "etl_username", "etl_password"
    );

    public SchoolDataConfigRefreshService(SystemConfigService configService,
                                           SchoolDataProperties properties,
                                           SchoolDataSyncScheduler scheduler,
                                           SchoolDataApiClientDelegate delegate,
                                           SchoolDataEtlClientImpl etlClient) {
        this.configService = configService;
        this.properties = properties;
        this.scheduler = scheduler;
        this.delegate = delegate;
        this.etlClient = etlClient;
    }

    /**
     * 从 DB 加载 school_data 配置并应用到 Properties
     */
    public void refreshFromDb() {
        Map<String, String> dbConfig = configService.getByGroup("school_data");
        if (dbConfig.isEmpty()) {
            logger.info("No school_data config found in DB, using defaults");
            return;
        }

        applyConfig(dbConfig);
        logger.info("School data config refreshed from DB ({} keys)", dbConfig.size());
    }

    /**
     * 更新配置 + 刷新 + 触发副作用
     *
     * @param configs   前端提交的配置列表
     * @param updatedBy 操作人
     */
    public void updateAndRefresh(List<SystemConfig> configs, String updatedBy) {
        // 1. 过滤脱敏值（包含 **** 的不覆盖原值）
        List<SystemConfig> filtered = configs.stream()
                .filter(c -> c.getConfigValue() == null || !c.getConfigValue().contains("****"))
                .toList();

        if (filtered.isEmpty()) {
            logger.info("All config values contain mask pattern, skip update");
            return;
        }

        // 2. 记录哪些 key 被修改了
        Set<String> changedKeys = new HashSet<>();
        for (SystemConfig config : filtered) {
            changedKeys.add(config.getConfigKey());
        }

        // 3. 写入 DB
        configService.batchUpdate(filtered, updatedBy);

        // 4. 从 DB 刷新到 Properties
        refreshFromDb();

        // 5. 如果 cron 相关配置变了 → 重新调度
        boolean cronChanged = changedKeys.stream().anyMatch(CRON_KEYS::contains);
        if (cronChanged) {
            logger.info("Cron config changed, rescheduling sync tasks");
            scheduler.rescheduleAll();
        }

        // 6. 如果 ETL 数据源配置变了 → 重建 DataSource（仅当 source=etl 且配置完整时）
        boolean etlDsChanged = changedKeys.stream().anyMatch(ETL_DATASOURCE_KEYS::contains);
        if (etlDsChanged && "etl".equalsIgnoreCase(properties.getSource())) {
            // 检查 ETL 配置是否完整
            String jdbcUrl = properties.getEtl().getJdbcUrl();
            if (jdbcUrl != null && !jdbcUrl.isEmpty() && !jdbcUrl.isBlank()) {
                logger.info("ETL datasource config changed, recreating DataSource");
                etlClient.recreateDataSource();
            } else {
                logger.warn("ETL datasource config changed but jdbcUrl is empty, skip DataSource recreation");
            }
        }
    }

    /**
     * 测试数据源连接（同时记录结果供 /config-status 返回）
     */
    public boolean testConnection() {
        Map<String, Object> result = new HashMap<>();
        result.put("timestamp", Instant.now().toString());
        result.put("source", properties.getSource());
        try {
            boolean ok = delegate.testConnection();
            result.put("success", ok);
            if (!ok) {
                result.put("message", "学校数据源连接测试失败（请检查日志）");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            lastTestResult.set(result);
            throw e;
        }
        lastTestResult.set(result);
        return Boolean.TRUE.equals(result.get("success"));
    }

    /**
     * 获取当前数据源类型
     */
    public String getCurrentSource() {
        return properties.getSource();
    }

    /**
     * 汇总当前学校数据集成配置状态，供 /config-status 端点返回。
     * 敏感值不返回，只返回 "是否已配置" 的 boolean。
     */
    public Map<String, Object> getConfigStatus() {
        SchoolDataProperties.ApiProperties api = properties.getApi();
        SchoolDataProperties.EtlProperties etl = properties.getEtl();
        SchoolDataProperties.SyncProperties sync = properties.getSync();

        Map<String, Object> status = new HashMap<>();
        status.put("source", properties.getSource());
        status.put("apiConfigured", notBlank(api.getBaseUrl()));
        status.put("authConfigured", notBlank(api.getAppId()) && notBlank(api.getAppSecret()));
        status.put("etlConfigured", notBlank(etl.getJdbcUrl()) && notBlank(etl.getUsername()));
        status.put("syncEnabled", sync.isEnabled());
        status.put("missingKeys", collectMissingKeys());
        status.put("lastTestResult", lastTestResult.get());
        return status;
    }

    private List<String> collectMissingKeys() {
        List<String> missing = new ArrayList<>();
        String source = properties.getSource();
        if ("api".equalsIgnoreCase(source)) {
            SchoolDataProperties.ApiProperties api = properties.getApi();
            if (!notBlank(api.getAppId())) missing.add("api_app_id");
            if (!notBlank(api.getAppSecret())) missing.add("api_app_secret");
        } else if ("etl".equalsIgnoreCase(source)) {
            SchoolDataProperties.EtlProperties etl = properties.getEtl();
            if (!notBlank(etl.getJdbcUrl())) missing.add("etl_jdbc_url");
            if (!notBlank(etl.getUsername())) missing.add("etl_username");
            if (!notBlank(etl.getPassword())) missing.add("etl_password");
        }
        return missing;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isEmpty() && !s.isBlank();
    }

    /**
     * 将 DB 配置值写入 SchoolDataProperties
     */
    private void applyConfig(Map<String, String> dbConfig) {
        // source
        setIfPresent(dbConfig, "source", v -> properties.setSource(v));

        // api
        setIfPresent(dbConfig, "api_base_url", v -> properties.getApi().setBaseUrl(v));
        // 认证: appId + appSecret 签名（官方规范）
        setIfPresent(dbConfig, "api_app_id", v -> properties.getApi().setAppId(v));
        setIfPresent(dbConfig, "api_app_secret", v -> properties.getApi().setAppSecret(v));
        setIfPresent(dbConfig, "api_connect_timeout", v -> properties.getApi().setConnectTimeout(parseInt(v)));
        setIfPresent(dbConfig, "api_read_timeout", v -> properties.getApi().setReadTimeout(parseInt(v)));
        setIfPresent(dbConfig, "api_retry_attempts", v -> properties.getApi().setRetryAttempts(parseInt(v)));

        // etl
        setIfPresent(dbConfig, "etl_jdbc_url", v -> properties.getEtl().setJdbcUrl(v));
        setIfPresent(dbConfig, "etl_username", v -> properties.getEtl().setUsername(v));
        setIfPresent(dbConfig, "etl_password", v -> properties.getEtl().setPassword(v));
        setIfPresent(dbConfig, "etl_connection_timeout", v -> properties.getEtl().setConnectionTimeout(parseInt(v)));
        setIfPresent(dbConfig, "etl_query_timeout", v -> properties.getEtl().setQueryTimeout(parseInt(v)));

        // sync
        setIfPresent(dbConfig, "sync_enabled", v -> properties.getSync().setEnabled(parseBool(v)));
        setIfPresent(dbConfig, "sync_full_cron", v -> properties.getSync().setFullSyncCron(v));
        setIfPresent(dbConfig, "sync_incremental_cron", v -> properties.getSync().setIncrementalSyncCron(v));
        setIfPresent(dbConfig, "sync_validation_cron", v -> properties.getSync().setValidationCron(v));
        setIfPresent(dbConfig, "sync_batch_size", v -> properties.getSync().setBatchSize(parseInt(v)));
        setIfPresent(dbConfig, "sync_timeout_minutes", v -> properties.getSync().setSyncTimeoutMinutes(parseInt(v)));
    }

    private void setIfPresent(Map<String, String> map, String key, java.util.function.Consumer<String> setter) {
        String value = map.get(key);
        if (value != null) {
            setter.accept(value);
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean parseBool(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}
