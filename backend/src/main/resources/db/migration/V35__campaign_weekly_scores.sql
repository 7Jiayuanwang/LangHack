-- 活动每周积分表：支持按周独立排名
CREATE TABLE IF NOT EXISTS campaign_weekly_scores (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  week_start DATE NOT NULL COMMENT '周起始日期（周一）',
  score INT NOT NULL DEFAULT 0 COMMENT '本周得分',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_campaign_user_week (campaign_id, user_id, week_start),
  INDEX idx_campaign_week (campaign_id, week_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动每周积分表';
