-- V7: support multiple AI providers with weighted routing

CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
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
             'tencent/Hunyuan-MT-7B'),
    1,
    1,
    COALESCE(CAST((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='timeout') AS UNSIGNED),
             30000),
    'migrated from system_config'
WHERE NOT EXISTS (SELECT 1 FROM ai_provider WHERE service = 'translate');
