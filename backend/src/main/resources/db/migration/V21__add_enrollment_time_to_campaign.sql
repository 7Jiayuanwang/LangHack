-- V21: 为活动表添加报名时间字段

ALTER TABLE campaign_activities
ADD COLUMN enrollment_start_time DATETIME NULL COMMENT '报名开始时间' AFTER end_time,
ADD COLUMN enrollment_end_time DATETIME NULL COMMENT '报名结束时间' AFTER enrollment_start_time;

-- 为已有活动设置默认报名时间（与活动时间一致）
UPDATE campaign_activities
SET enrollment_start_time = start_time,
    enrollment_end_time = end_time
WHERE enrollment_start_time IS NULL;
