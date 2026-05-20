-- V24: HiAgent 出题凭证配置项
-- 为 AI 出题功能添加独立的 HiAgent 凭证配置（与改写、问答凭证隔离）

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES
  ('ai_config', 'hiagent_quiz_api_key', '', 'HiAgent 出题应用 API Key（独立于改写/问答凭证）', 1),
  ('ai_config', 'hiagent_quiz_app_id', '', 'HiAgent 出题应用 App ID（独立于改写/问答凭证）', 0)
ON DUPLICATE KEY UPDATE description = VALUES(description);
