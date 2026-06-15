-- ═══════════════════════════════════════════════════════════════
-- V45: AI 问答使用统计索引
-- 为 ai_call_log 添加复合索引，覆盖按用户+服务+时间的统计查询
-- ═══════════════════════════════════════════════════════════════

-- 复合索引：按触发用户 + 服务名 + 成功状态 + 时间查询
-- 用于 /api/student/ai-qa/usage/stats 接口的今日/累计统计
CREATE INDEX IF NOT EXISTS idx_triggered_service_success_time
    ON ai_call_log (triggered_by, service_name, success, created_time);
