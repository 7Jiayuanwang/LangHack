-- V38: Seed 9 base learner tag definitions + tag management feature toggle

INSERT IGNORE INTO learner_composite_tags (code, display_name, tag_type, priority, rule_definition, action_hint, enabled) VALUES
('INACTIVE_RISK', '流失风险', 'risk', 10,
 '{"conditions":[{"source":"metric","field":"activeDays14d","operator":"LT","value":3},{"source":"metric","field":"studyMinutes14d","operator":"GT","value":0}],"combiner":"AND"}',
 '建议增加学习提醒频率', 1),

('VOCAB_WEAK', '词汇弱势', 'risk', 20,
 '{"conditions":[{"source":"metric","field":"totalWords","operator":"GTE","value":30},{"source":"metric","field":"weakRatio","operator":"GTE","value":0.6}],"combiner":"AND"}',
 '建议加强词汇复习', 1),

('REVIEW_OVERDUE', '复习逾期', 'risk', 30,
 '{"conditions":[{"source":"metric","field":"dueReviewCount","operator":"GT","value":0},{"source":"metric","field":"overdueReviewRatio","operator":"GTE","value":0.5}],"combiner":"AND"}',
 '请尽快完成待复习词汇', 1),

('CONSISTENT_LEARNER', '学习稳定', 'positive', 40,
 '{"conditions":[{"source":"metric","field":"activeDays7d","operator":"GTE","value":5}],"combiner":"AND"}',
 '保持良好的学习节奏', 1),

('CONTENT_LEVEL_STUCK', '版本停滞', 'ability', 50,
 '{"conditions":[{"source":"metric","field":"textsRead","operator":"GTE","value":40},{"source":"metric","field":"versionDistSize","operator":"EQ","value":1}],"combiner":"AND"}',
 '建议尝试更高难度版本', 1),

('CONTENT_LEVEL_JUMP', '版本跳跃', 'ability', 60,
 '{"conditions":[{"source":"metric","field":"activeDays7d","operator":"GTE","value":0}],"combiner":"AND"}',
 '注意难度跨度是否过大', 0),

('IMPROVING_TREND', '进步趋势', 'positive', 70,
 '{"conditions":[{"source":"metric","field":"oldActiveDays14d","operator":"GT","value":0},{"source":"metric","field":"trendRatio","operator":"GTE","value":1.3}],"combiner":"AND"}',
 '学习活跃度明显提升', 1),

('DECLINING_TREND', '下滑趋势', 'risk', 80,
 '{"conditions":[{"source":"metric","field":"oldActiveDays14d","operator":"GT","value":0},{"source":"metric","field":"trendRatio","operator":"LTE","value":0.6}],"combiner":"AND"}',
 '学习活跃度出现下降', 1),

('REVIEW_CHAMPION', '复习达人', 'positive', 90,
 '{"conditions":[{"source":"metric","field":"snap0OverdueRatio","operator":"LT","value":0.05},{"source":"metric","field":"snap1OverdueRatio","operator":"LT","value":0.05},{"source":"metric","field":"snap2OverdueRatio","operator":"LT","value":0.05}],"combiner":"AND"}',
 '复习习惯非常好', 1);

-- Feature toggle for tag management page
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description)
VALUES ('feature_toggle', 'admin.tag_management', 'true', '管理员-标签管理');
