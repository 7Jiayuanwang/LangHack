-- ═══════════════════════════════════════════════════════════════
-- V16: AI 问答恢复 + HiAgent 凭证前端可配置化
-- 整合历史：V17 → V16 (2026-04-21 合并重编号；V15 占位为 ECDICTImporter.java)
--
-- 背景：
--   1. 运维操作 / 缓存漂移 / 历史覆盖可能导致 system_config.page_ai_qa 被置为 'false'
--      或 feature_override 里有对 page_ai_qa 的禁用规则 → 路由守卫拦截 → "进不去"
--   2. HiAgent 改写/问答凭证原本只通过 application.yml + 环境变量注入，
--      违反"所有配置前端可配"的运维诉求 → 把 4+1 个 HiAgent 凭证迁到 system_config.ai_config
--      后端 HiAgentServiceImpl 读值优先 DB，回退到 @Value（环境变量）
-- ═══════════════════════════════════════════════════════════════

-- ────────────────────────────────────────
-- 1. 恢复 page_ai_qa 开关为 true（无论之前是何值）
--    并清除 feature_override 里的禁用覆盖
-- ────────────────────────────────────────
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';

-- 若历史未 seed，补 seed（INSERT IGNORE 保持幂等）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_ai_qa', 'true', '学生端-AI问答页', 0);

-- 清除所有对 page_ai_qa 的禁用覆盖（tag/user scope）
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';

-- ────────────────────────────────────────
-- 2. HiAgent 凭证 seed 到 ai_config group（is_secret=1 for keys）
--    值默认为空字符串，表示"尚未从前端配置，使用 application.yml 环境变量"
--    前端面板写入 DB 后，HiAgentServiceImpl 会优先读 DB 值
-- ────────────────────────────────────────
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);
