-- V51: 任务计划功能增强
-- 1) task_plans 新增三个全局任务项开关
-- 2) task_plan_classes 新增六个教师覆盖列
-- 3) 新建 word_category_map 表用于向上兼容统计

ALTER TABLE task_plans
  ADD COLUMN article_enabled TINYINT NOT NULL DEFAULT 1 AFTER paper_target,
  ADD COLUMN word_enabled    TINYINT NOT NULL DEFAULT 1 AFTER article_enabled,
  ADD COLUMN paper_enabled   TINYINT NOT NULL DEFAULT 1 AFTER word_enabled;

ALTER TABLE task_plan_classes
  ADD COLUMN cycle_type_override      ENUM('WEEKLY','MONTHLY') DEFAULT NULL AFTER paper_weight_override,
  ADD COLUMN semester_start_override   DATE DEFAULT NULL AFTER cycle_type_override,
  ADD COLUMN semester_end_override     DATE DEFAULT NULL AFTER semester_start_override,
  ADD COLUMN article_enabled_override  TINYINT DEFAULT NULL AFTER semester_end_override,
  ADD COLUMN word_enabled_override     TINYINT DEFAULT NULL AFTER article_enabled_override,
  ADD COLUMN paper_enabled_override    TINYINT DEFAULT NULL AFTER word_enabled_override;

CREATE TABLE IF NOT EXISTS word_category_map (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  word      VARCHAR(100) NOT NULL,
  category  ENUM('GAOKAO','CET4','CET6') NOT NULL,
  UNIQUE KEY uk_word_cat (word, category),
  INDEX idx_word (word)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
