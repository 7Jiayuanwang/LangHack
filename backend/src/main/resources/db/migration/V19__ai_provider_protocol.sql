-- V19: AI 服务商协议类型字段
-- 新增 protocol 列，区分 OpenAI 兼容格式与 HiAgent 专有协议
-- 默认值 openai_compatible，现有数据无需更新

ALTER TABLE `ai_provider`
    ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        AFTER `service`;

ALTER TABLE `ai_provider`
    MODIFY COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        COMMENT '协议类型: openai_compatible | hiagent';
