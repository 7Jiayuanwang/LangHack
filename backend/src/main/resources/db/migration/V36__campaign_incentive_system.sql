-- ============================================================
-- 活动计分规则激励优化（合并 V36 + V37 + V38）
-- 1. campaign_activities 表新增激励配置字段
-- 2. 新增 campaign_streak_records 连续学习记录表
-- 3. 新增 campaign_score_logs 积分流水表
-- ============================================================

-- ============================================================
-- 1. 活动表新增激励配置字段
-- ============================================================
ALTER TABLE campaign_activities
  ADD COLUMN streak_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用连续学习加成',
  ADD COLUMN streak_config JSON DEFAULT NULL COMMENT '连续加成阶梯配置，JSON数组 [{day:2,multiplier:1.05},{day:3,multiplier:1.1}...]',
  ADD COLUMN streak_max_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.30 COMMENT '连续加成封顶系数',
  ADD COLUMN streak_reset_days INT NOT NULL DEFAULT 7 COMMENT '连续加成重置周期（天），默认7天',
  ADD COLUMN streak_start_date DATE DEFAULT NULL COMMENT '连续加成周期起始日期（为空则使用活动开始时间）',
  ADD COLUMN class_ranking_mode VARCHAR(20) NOT NULL DEFAULT 'WEIGHTED' COMMENT '班级排名模式: WEIGHTED(归一化加权) / AVG_SCORE(纯人均积分) / TOTAL_SCORE(总积分)',
  ADD COLUMN class_avg_weight DECIMAL(3,2) NOT NULL DEFAULT 0.60 COMMENT '班级排名中人均积分权重(0~1)',
  ADD COLUMN class_participation_weight DECIMAL(3,2) NOT NULL DEFAULT 0.40 COMMENT '班级排名中参与率权重(0~1)',
  ADD COLUMN participation_calc_mode VARCHAR(20) NOT NULL DEFAULT 'DENSITY' COMMENT '参与率计算方式: HEAD_COUNT(活跃人数占比) / DENSITY(活跃天数密度)';

-- ============================================================
-- 2. 新增连续学习记录表
-- ============================================================
CREATE TABLE IF NOT EXISTS campaign_streak_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  week_start DATE NOT NULL COMMENT '周期起始日期（以活动开始日为锚点，每隔 streak_reset_days 天为一期）',
  streak_days INT NOT NULL DEFAULT 0 COMMENT '本周期连续活跃天数',
  current_multiplier DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '当前积分系数',
  last_active_date DATE DEFAULT NULL COMMENT '最后活跃日期',
  active_dates JSON DEFAULT NULL COMMENT '本周期活跃日期数组 ["2026-07-14","2026-07-15"]',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_campaign_user_week (campaign_id, user_id, week_start),
  INDEX idx_campaign_week (campaign_id, week_start),
  INDEX idx_user_campaign (user_id, campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动连续学习记录表';

-- ============================================================
-- 3. 新增积分流水表（账单式记录每笔得分明细）
-- ============================================================
CREATE TABLE IF NOT EXISTS campaign_score_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  campaign_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  task_id BIGINT DEFAULT NULL COMMENT '关联任务ID',
  task_name VARCHAR(100) DEFAULT NULL COMMENT '任务名称快照',
  action VARCHAR(50) NOT NULL COMMENT '动作描述，如 TASK_COMPLETE / STREAK_BONUS',
  base_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '基础得分',
  multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '加成系数',
  final_score DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '实际得分（base_score * multiplier）',
  streak_days INT DEFAULT NULL COMMENT '当时的连续天数',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_campaign_user (campaign_id, user_id),
  INDEX idx_user_time (user_id, created_time DESC),
  INDEX idx_campaign_time (campaign_id, created_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动积分流水表';

-- ============================================================
-- 4. 已有积分字段从 INT 升级为 DECIMAL（精确小数积分）
-- ============================================================
ALTER TABLE campaign_task_completions MODIFY COLUMN earned_score DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE campaign_participations MODIFY COLUMN total_score DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE campaign_weekly_scores MODIFY COLUMN score DECIMAL(10,2) NOT NULL DEFAULT 0.00;
