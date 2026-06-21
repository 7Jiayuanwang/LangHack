-- ═══════════════════════════════════════════════════════════════
-- V5: 系统配置 + AI提供商 + 多模型支持 + HiAgent 凭证配置
-- 整合自: 原 V6 (system_config + 默认配置)
--         + V7 (ai_provider 表 + 数据迁移)
--         + V11 (ai_provider.app_id 内联到 V7)
--         + V13 (system_config.ai_config HiAgent 凭证)
--         + V13 Part 4 (ai_provider.protocol 协议字段)
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 创建系统配置表 + 默认配置 [原 V6]
-- ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_group` VARCHAR(50) NOT NULL COMMENT '配置分组: ai_translate, ai_article, ai_chat',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '配置说明（运维面板显示）',
    `is_secret` TINYINT DEFAULT 0 COMMENT '是否敏感（前端脱敏显示）',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 地址（OpenAI 兼容）', 0),
('ai_translate', 'api_key', '', 'API 密钥', 1),
('ai_translate', 'model', 'Qwen/Qwen2.5-7B-Instruct', '模型名称', 0),
('ai_translate', 'enabled', 'true', '是否启用', 0),
('ai_translate', 'timeout', '30000', '超时时间(ms)', 0);

-- ────────────────────────────────────────
-- 2. AI 提供商表 + 数据迁移 [原 V7 + V11 + V13 Part 4]
--    app_id 字段已内联 [原 V11]
--    protocol 字段 [原 V13 Part 4]
-- ────────────────────────────────────────
DROP PROCEDURE IF EXISTS _v5_ai_provider_protocol;
CREATE PROCEDURE _v5_ai_provider_protocol()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_provider' AND COLUMN_NAME = 'protocol') THEN
        ALTER TABLE `ai_provider` ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible' AFTER `service`;
    END IF;
    -- Ensure comment is set (idempotent modify)
    ALTER TABLE `ai_provider`
        MODIFY COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
            COMMENT '协议类型: openai_compatible | hiagent';
END;
CALL _v5_ai_provider_protocol();
DROP PROCEDURE IF EXISTS _v5_ai_provider_protocol;

CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible' COMMENT '协议类型: openai_compatible | hiagent',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
    `app_id` VARCHAR(200) NULL COMMENT 'App ID（HiAgent 等服务商必填）',
    `model` VARCHAR(200) NOT NULL COMMENT 'default model',
    `weight` INT NOT NULL DEFAULT 1 COMMENT 'weighted routing priority',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT 'whether provider is enabled',
    `timeout_ms` INT NOT NULL DEFAULT 30000 COMMENT 'request timeout in ms',
    `remark` VARCHAR(255) COMMENT 'operator note',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI provider configuration';

INSERT IGNORE INTO `ai_provider`
    (`id`, `service`, `name`, `api_url`, `api_key`, `model`, `weight`, `enabled`, `timeout_ms`, `remark`)
SELECT
    1,
    'translate',
    'SiliconFlow (default)',
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_url'),
             'https://api.siliconflow.cn/v1/chat/completions'),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_key'), ''),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='model'),
             'Qwen/Qwen2.5-7B-Instruct'),
    1,
    1,
    COALESCE(CAST((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='timeout') AS UNSIGNED),
             30000),
    'migrated from system_config'
WHERE NOT EXISTS (SELECT 1 FROM ai_provider WHERE service = 'translate');

-- ────────────────────────────────────────
-- 3. HiAgent 凭证 seed 到 ai_config group [原 V13 Part 2]
--    值默认为空字符串，表示"尚未从前端配置，使用 application.yml 环境变量"
--    前端面板写入 DB 后，HiAgentServiceImpl 会优先读 DB 值
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);
