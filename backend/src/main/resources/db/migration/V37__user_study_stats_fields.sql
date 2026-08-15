-- V37: Add study statistics fields to users table (from author-profile feature)
ALTER TABLE users
  ADD COLUMN total_read_time INT NOT NULL DEFAULT 0 COMMENT '总阅读时长(分钟)',
  ADD COLUMN completed_quizzes INT NOT NULL DEFAULT 0 COMMENT '完成测验数',
  ADD COLUMN correct_answers INT NOT NULL DEFAULT 0 COMMENT '正确答题数',
  ADD COLUMN study_days INT NOT NULL DEFAULT 0 COMMENT '学习天数',
  ADD COLUMN last_study_time DATETIME DEFAULT NULL COMMENT '最后学习时间';
