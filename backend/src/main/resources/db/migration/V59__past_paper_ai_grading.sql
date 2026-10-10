DROP PROCEDURE IF EXISTS _v59_past_paper_ai_grading;
DELIMITER $$
CREATE PROCEDURE _v59_past_paper_ai_grading()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'past_paper_attempts' AND COLUMN_NAME = 'grading_status'
  ) THEN
    ALTER TABLE past_paper_attempts
      ADD COLUMN grading_status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED/AI_UNAVAILABLE' AFTER answers_json;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'past_paper_attempts' AND COLUMN_NAME = 'ai_score_percentage'
  ) THEN
    ALTER TABLE past_paper_attempts
      ADD COLUMN ai_score_percentage DECIMAL(5,2) DEFAULT NULL COMMENT '主观题AI百分制得分' AFTER grading_status;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'past_paper_attempts' AND COLUMN_NAME = 'final_score_percentage'
  ) THEN
    ALTER TABLE past_paper_attempts
      ADD COLUMN final_score_percentage DECIMAL(5,2) DEFAULT NULL COMMENT '综合百分制得分' AFTER ai_score_percentage;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'past_paper_attempts' AND COLUMN_NAME = 'grading_json'
  ) THEN
    ALTER TABLE past_paper_attempts
      ADD COLUMN grading_json LONGTEXT DEFAULT NULL COMMENT '逐题判分与学习建议JSON' AFTER final_score_percentage;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'past_paper_attempts' AND COLUMN_NAME = 'graded_time'
  ) THEN
    ALTER TABLE past_paper_attempts
      ADD COLUMN graded_time DATETIME DEFAULT NULL COMMENT '判分完成时间' AFTER grading_json;
  END IF;
END$$
DELIMITER ;

CALL _v59_past_paper_ai_grading();
DROP PROCEDURE IF EXISTS _v59_past_paper_ai_grading;

INSERT INTO system_config (config_group, config_key, config_value, description, is_secret)
VALUES
  ('ai_config', 'hiagent_grading_api_url', 'https://agent.imau.edu.cn:32400/api/proxy/api/v1', 'HiAgent 真题判分应用 API Base URL', 0),
  ('ai_config', 'hiagent_grading_api_key', '', 'HiAgent 真题判分应用 API Key（独立应用）', 1),
  ('ai_config', 'hiagent_grading_app_id', '', 'HiAgent 真题判分应用 App ID（独立应用）', 0)
ON DUPLICATE KEY UPDATE description = VALUES(description);
