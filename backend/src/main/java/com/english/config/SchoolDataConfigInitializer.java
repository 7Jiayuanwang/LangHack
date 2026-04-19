package com.english.config;

import com.english.service.SchoolDataConfigRefreshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 学校数据配置初始化器
 * <p>
 * 应用启动完成后，从 DB 加载 school_data 配置覆盖 YAML 默认值
 */
@Component
public class SchoolDataConfigInitializer {

    private static final Logger logger = LoggerFactory.getLogger(SchoolDataConfigInitializer.class);

    private final SchoolDataConfigRefreshService refreshService;
    private final SchoolDataProperties properties;

    public SchoolDataConfigInitializer(SchoolDataConfigRefreshService refreshService,
                                       SchoolDataProperties properties) {
        this.refreshService = refreshService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            refreshService.refreshFromDb();
            logger.info("School data config initialized from DB on startup");
        } catch (Exception e) {
            logger.warn("Failed to load school data config from DB on startup, using YAML defaults: {}", e.getMessage());
        }
        validateAndAlert();
    }

    /**
     * 启动期认证配置校验。
     * source=api 且 appId/appSecret 任一为空 → 打印 ERROR 横幅引导修复。
     * source=etl 时同样校验 jdbcUrl。
     */
    private void validateAndAlert() {
        String source = properties.getSource();
        if ("api".equalsIgnoreCase(source)) {
            SchoolDataProperties.ApiProperties api = properties.getApi();
            boolean appIdMissing = isBlank(api.getAppId());
            boolean appSecretMissing = isBlank(api.getAppSecret());
            if (appIdMissing || appSecretMissing) {
                logger.error(
                        "\n═══════════════════════════════════════════════════════════════\n" +
                        "⚠️  School API 认证未配置，数据同步将全部失败！\n" +
                        "缺失: appId={}, appSecret={}\n" +
                        "修复方式（任选一）:\n" +
                        "  1) 运维面板 → 学校数据配置 → 填入 App ID / App Secret → 保存 → 测试连接\n" +
                        "  2) 编辑 docker/.env 设置 SCHOOL_API_APP_ID / SCHOOL_API_APP_SECRET 后重启后端\n" +
                        "  3) 重新运行 scripts/deploy.sh 交互向导\n" +
                        "详见: docs/SCHOOL_DATA_API_FIX.md\n" +
                        "═══════════════════════════════════════════════════════════════",
                        appIdMissing ? "(empty)" : "SET",
                        appSecretMissing ? "(empty)" : "SET");
            } else {
                logger.info("School API auth configured (source=api, appId=SET, appSecret=SET)");
            }
        } else if ("etl".equalsIgnoreCase(source)) {
            SchoolDataProperties.EtlProperties etl = properties.getEtl();
            if (isBlank(etl.getJdbcUrl()) || isBlank(etl.getUsername())) {
                logger.error(
                        "\n═══════════════════════════════════════════════════════════════\n" +
                        "⚠️  School ETL 配置未完成，JDBC 直连无法使用！\n" +
                        "缺失: jdbcUrl={}, username={}\n" +
                        "请在运维面板或 docker/.env 中补充 SCHOOL_ETL_JDBC_URL / SCHOOL_ETL_USERNAME / SCHOOL_ETL_PASSWORD\n" +
                        "═══════════════════════════════════════════════════════════════",
                        isBlank(etl.getJdbcUrl()) ? "(empty)" : "SET",
                        isBlank(etl.getUsername()) ? "(empty)" : "SET");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isEmpty() || s.isBlank();
    }
}
