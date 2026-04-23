-- ═══════════════════════════════════════════════════════════════
-- V17: 补齐学生端"背单词"页 feature_toggle 配置
-- 整合历史：V18 → V17 (2026-04-21 合并重编号)
--
-- 背景：
--   Layout.vue 底部导航 / Dashboard.vue 发现页快捷入口均引用了
--   toggleKey = 'page_word_recite'，但该键从未 seed 到
--   system_config.feature_toggle，导致运维面板"全局开关"列表缺少
--   "背单词"条目 —— 开关与页面无法一一对应。
--
--   同时 router/index.js 中 /student/word-recite 路由守卫缺失
--   featureKey，本迁移配合前端补齐后，达成"开关关闭 → 导航隐藏 +
--   快捷入口隐藏 + 直接访问被拦截"三层联动。
-- ═══════════════════════════════════════════════════════════════

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`)
VALUES ('feature_toggle', 'page_word_recite', 'true', '学生端-背单词页', 0);

-- 若历史中存在但描述为空，统一规范化描述
UPDATE `system_config`
SET `description` = '学生端-背单词页'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'page_word_recite'
  AND (`description` IS NULL OR `description` = '');
