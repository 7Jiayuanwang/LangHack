package com.english.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup Configuration Checker / 启动配置检查器
 *
 * Validates critical configuration at application startup and prints
 * WARN-level log messages for items that need attention.
 * 在应用启动时检查关键配置，对需要注意的项打印 WARN 级别日志。
 *
 * This does NOT block startup — it only provides guidance.
 * 不阻塞启动 — 仅提供配置指引。
 */
@Component
public class StartupConfigChecker {

    private static final Logger log = LoggerFactory.getLogger(StartupConfigChecker.class);

    private static final String SEPARATOR = "════════════════════════════════════════════════════════════════";
    private static final String WARN_PREFIX = "  ⚠ ";

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String corsAllowedOrigins;

    @Value("${spring.security.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.datasource.master.password:}")
    private String dbPassword;

    @Value("${spring.datasource.master.jdbc-url:}")
    private String dbUrl;

    @Value("${hiagent.api-key:}")
    private String hiagentApiKey;

    @Value("${openrouter.api-key:}")
    private String openrouterApiKey;

    @Value("${tencent.translate.secret-id:}")
    private String tencentSecretId;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void checkConfiguration() {
        int warningCount = 0;
        StringBuilder warnings = new StringBuilder();

        // ── CORS Check / CORS 检查 ──
        // This is the #1 cause of "Access Denied" in production deployments.
        // 这是生产环境 "拒绝访问" 的首要原因。
        if (isLocalhostOnly(corsAllowedOrigins)) {
            warningCount++;
            warnings.append(WARN_PREFIX)
                    .append("[CORS] allowed-origins only contains localhost addresses.\n")
                    .append("       CORS 仅允许 localhost，生产环境前端域名将被拒绝！\n")
                    .append("       Fix / 修复方式:\n")
                    .append("         1. 配置文件: application-prod.yml 中设置 app.cors.allowed-origins\n")
                    .append("         2. 环境变量: export APP_CORS_ALLOWED_ORIGINS=https://your-frontend-domain.com\n")
                    .append("         3. 命令行:   --app.cors.allowed-origins=https://your-frontend-domain.com\n\n");
        }

        // ── JWT Secret Check / JWT 密钥检查 ──
        if (isDefaultOrWeak(jwtSecret, "your_dummy_jwt_secret", 32)) {
            warningCount++;
            warnings.append(WARN_PREFIX)
                    .append("[JWT] Secret is default/weak — tokens can be forged!\n")
                    .append("      JWT 密钥为默认值或强度不足 — 令牌可被伪造！\n")
                    .append("      Fix / 修复: export JWT_SECRET=$(openssl rand -base64 48)\n\n");
        }

        // ── Database Password Check / 数据库密码检查 ──
        if (isBlankOrDefault(dbPassword, "123", "root", "password", "your_strong_password_here")) {
            warningCount++;
            warnings.append(WARN_PREFIX)
                    .append("[DB] Database password is default or too simple.\n")
                    .append("     数据库密码为默认值或过于简单。\n")
                    .append("     Fix / 修复: export DB_PASSWORD=<strong-password>\n\n");
        }

        // ── Redis Password Check / Redis 密码检查 ──
        if (redisPassword == null || redisPassword.trim().isEmpty()) {
            warningCount++;
            warnings.append(WARN_PREFIX)
                    .append("[Redis] No password set — Redis is unprotected.\n")
                    .append("        Redis 未设置密码 — 任何人可连接。\n")
                    .append("        Fix / 修复: export REDIS_PASSWORD=<strong-password>\n\n");
        }

        // ── AI Service Keys Check / AI 服务密钥检查 ──
        if (isBlank(hiagentApiKey) && isBlank(openrouterApiKey)) {
            warningCount++;
            warnings.append(WARN_PREFIX)
                    .append("[AI] Neither HiAgent nor OpenRouter API key is configured.\n")
                    .append("     HiAgent 和 OpenRouter 的 API Key 均未配置，AI 问答功能将不可用。\n")
                    .append("     Fix / 修复: export HIAGENT_API_KEY=... or export OPENROUTER_API_KEY=...\n\n");
        }

        // ── Tencent Cloud Keys Check / 腾讯云密钥检查 ──
        if (isBlank(tencentSecretId)) {
            // This is informational, not critical
            warnings.append("  ℹ [Tencent] Translation/ASR credentials not configured — translation features disabled.\n")
                    .append("     腾讯云凭据未配置 — 翻译和语音识别功能不可用。\n\n");
        }

        // ── Print Summary / 打印摘要 ──
        if (warningCount > 0) {
            log.warn("\n{}\n  STARTUP CONFIG CHECK / 启动配置检查  ({} warning(s))\n{}\n\n{}{}",
                    SEPARATOR, warningCount, SEPARATOR, warnings, SEPARATOR);
        } else {
            log.info("Startup config check passed — all critical settings look good. / 启动配置检查通过。");
            // Still print informational items if any
            if (warnings.length() > 0) {
                log.info("Optional config notes:\n{}", warnings);
            }
        }
    }

    /**
     * Check if CORS origins only contain localhost addresses.
     * 检查 CORS 来源是否仅包含 localhost 地址。
     */
    private boolean isLocalhostOnly(String origins) {
        if (isBlank(origins)) return true;
        String[] parts = origins.split(",");
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            if (!trimmed.contains("localhost") && !trimmed.contains("127.0.0.1")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if a value is blank/null, matches any known default, or is too short.
     * 检查值是否为空、匹配已知默认值、或长度不足。
     */
    private boolean isDefaultOrWeak(String value, String defaultPrefix, int minLength) {
        if (isBlank(value)) return true;
        if (value.length() < minLength) return true;
        String lower = value.toLowerCase();
        return lower.startsWith(defaultPrefix.toLowerCase());
    }

    /**
     * Check if value is blank or matches any of the known default/weak values.
     * 检查值是否为空或匹配任何已知默认/弱值。
     */
    private boolean isBlankOrDefault(String value, String... defaults) {
        if (isBlank(value)) return true;
        for (String def : defaults) {
            if (value.equalsIgnoreCase(def)) return true;
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
