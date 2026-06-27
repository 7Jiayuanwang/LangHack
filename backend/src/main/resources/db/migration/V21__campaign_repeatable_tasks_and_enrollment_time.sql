-- V21__campaign_repeatable_tasks_and_enrollment_time.sql
-- 活动任务重复积分功能 + 报名时间字段
-- 创建日期: 2026-06-24

-- Part 1: 为活动表添加报名时间字段
ALTER TABLE campaign_activities
ADD COLUMN enrollment_start_time DATETIME NULL COMMENT '报名开始时间' AFTER end_time,
ADD COLUMN enrollment_end_time DATETIME NULL COMMENT '报名结束时间' AFTER enrollment_start_time;

-- 为已有活动设置默认报名时间（与活动时间一致）
UPDATE campaign_activities
SET enrollment_start_time = start_time,
    enrollment_end_time = end_time
WHERE enrollment_start_time IS NULL;

-- Part 2: 为 campaign_tasks 表添加重复积分相关字段
ALTER TABLE campaign_tasks
ADD COLUMN is_repeatable TINYINT(1) DEFAULT 0 COMMENT '是否可重复完成（重复积分）' AFTER auto_collect,
ADD COLUMN max_completions INT DEFAULT NULL COMMENT '最大完成次数（NULL=无限制，用于总得分上限控制）' AFTER is_repeatable,
ADD COLUMN daily_limit INT DEFAULT NULL COMMENT '单日最大完成次数（NULL=无限制，用于单日得分上限控制）' AFTER max_completions;

-- Part 3: 为 campaign_task_completions 表添加每日完成记录追踪
ALTER TABLE campaign_task_completions
ADD COLUMN earned_score INT DEFAULT 0 COMMENT '已获得总积分' AFTER completion_count,
ADD COLUMN last_completion_date DATE COMMENT '最后一次完成日期' AFTER earned_score,
ADD COLUMN today_completion_count INT DEFAULT 0 COMMENT '今日完成次数' AFTER last_completion_date;

-- Part 4: 添加索引优化查询性能
ALTER TABLE campaign_task_completions
ADD INDEX idx_user_task_date (user_id, task_id, last_completion_date);

-- Part 5: 数据修复 - 为已完成的任务补充 completion_count 和 earned_score
UPDATE campaign_task_completions ctc
JOIN campaign_tasks ct ON ctc.task_id = ct.id
SET
  ctc.completion_count = IF(ctc.is_completed = 1 AND ctc.completion_count = 0, 1, ctc.completion_count),
  ctc.earned_score = IF(ctc.is_completed = 1 AND ctc.earned_score = 0, ct.points, ctc.earned_score)
WHERE ctc.is_completed = 1
  AND (ctc.completion_count = 0 OR ctc.earned_score = 0);

-- Part 6: 重新计算所有参与记录的总得分和完成任务数
UPDATE campaign_participations cp
SET
  cp.total_score = (
    SELECT COALESCE(SUM(ctc.earned_score), 0)
    FROM campaign_task_completions ctc
    WHERE ctc.campaign_id = cp.campaign_id
      AND ctc.user_id = cp.user_id
  ),
  cp.completed_tasks = (
    SELECT COUNT(*)
    FROM campaign_task_completions ctc
    WHERE ctc.campaign_id = cp.campaign_id
      AND ctc.user_id = cp.user_id
      AND ctc.is_completed = 1
  );

-- 注释说明
-- is_repeatable = 1: 任务可重复完成，每次达标都积分
-- max_completions: 限制总完成次数，例如设置为 10 表示最多完成10次就不再积分
-- daily_limit: 限制单日完成次数，例如设置为 3 表示每天最多完成3次
-- earned_score: 用户在此任务上已累计获得的总积分（= completion_count * points）
-- today_completion_count: 今日已完成次数，每日0点后首次完成时自动重置
--
-- 示例场景：
-- 1. 每天阅读30分钟，每完成一次得1分，每天最多3次 -> is_repeatable=1, daily_limit=3
-- 2. 学习新单词，每学50个得1分，总共最多完成20次 -> is_repeatable=1, max_completions=20
-- 3. 上传课文，上传一次即可，不可重复 -> is_repeatable=0（默认）

-- ========================================================================
-- P0 严重问题修复（2026-06-24 追加）
-- ========================================================================

-- P0-01 修复: 添加唯一约束防止重复报名（竞态条件）
ALTER TABLE campaign_participations
ADD UNIQUE INDEX uk_campaign_user (campaign_id, user_id);

-- P0-03 修复: 添加关键数据库索引以提升性能
-- 预期提升: 排名查询 5s → <100ms (50x 提升)

-- 1. 排名查询索引 (campaign_participations)
-- 用于 getIndividualRanking: 按 campaign_id + enrollment_status + total_score 排序
CREATE INDEX IF NOT EXISTS idx_enrollment_score
ON campaign_participations(campaign_id, enrollment_status, total_score DESC);

-- 2. 任务完成记录查询索引 (campaign_task_completions)
-- 用于按活动、任务、用户快速定位完成记录
CREATE INDEX IF NOT EXISTS idx_campaign_task_user
ON campaign_task_completions(campaign_id, task_id, user_id);

-- 3. 活动任务筛选索引 (campaign_tasks)
-- 用于筛选活动下的必做任务
CREATE INDEX IF NOT EXISTS idx_campaign_required
ON campaign_tasks(campaign_id, is_required, deleted);

-- 注: 这些索引针对 CampaignStatsServiceImpl 中高频查询场景
-- idx_enrollment_score: 覆盖 getIndividualRanking/getClassRanking/getCollegeRanking
-- idx_campaign_task_user: 覆盖 getTaskCompletion/updateTaskCompletion
-- idx_campaign_required: 覆盖 calculateCompletionStatus 中的必做任务筛选

-- ========================================================================
-- 可重复任务增量跟踪字段（2026-06-26 追加）
-- ========================================================================
-- 问题: 可重复任务每次定时采集都基于 currentValue(绝对总量) 判断达标，
-- 若 value >= targetValue 则加分，导致同一进度被重复加分。
-- 修复: 新增 last_counted_value 记录上次计分时的进度值，
--       新增积分 = (currentValue - lastCountedValue) / targetValue 向下取整。

ALTER TABLE campaign_task_completions
ADD COLUMN last_counted_value DECIMAL(10,2) DEFAULT 0.0000 COMMENT '上次计分时的进度值（用于增量计分）' AFTER current_value;

-- 为已有的完成记录初始化 last_counted_value = current_value，避免历史数据被重复计分
UPDATE campaign_task_completions
SET last_counted_value = current_value
WHERE last_counted_value = 0 AND current_value > 0;

-- ═══════════════════════════════════════════════════════════════
-- 扩展 school_student 字段长度（整合自 V21__extend_school_student_fields.sql）
-- 修复 DataIntegrityViolationException: phone/counselor_name VARCHAR(50) → VARCHAR(100)
-- ═══════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS _v21_extend_student_fields;
CREATE PROCEDURE _v21_extend_student_fields()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'phone'
                 AND CHARACTER_MAXIMUM_LENGTH = 50) THEN
        ALTER TABLE school_student
            MODIFY COLUMN phone VARCHAR(100) NULL COMMENT '联系电话（CDSP 可能返回多个号码拼接）';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'counselor_name'
                 AND CHARACTER_MAXIMUM_LENGTH = 50) THEN
        ALTER TABLE school_student
            MODIFY COLUMN counselor_name VARCHAR(100) NULL COMMENT '辅导员姓名（可能包含多个辅导员或职务）';
    END IF;
END;
CALL _v21_extend_student_fields();
DROP PROCEDURE IF EXISTS _v21_extend_student_fields;
