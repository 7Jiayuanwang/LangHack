-- 默认任务计划系统
CREATE TABLE IF NOT EXISTS task_plans (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL COMMENT '计划名称',
  course_id BIGINT NOT NULL COMMENT '关联课程ID',
  cycle_type ENUM('WEEKLY','MONTHLY') NOT NULL COMMENT '周期类型',
  semester_start DATE NOT NULL COMMENT '学期开始日期',
  semester_end DATE NOT NULL COMMENT '学期结束日期',
  article_target INT NOT NULL DEFAULT 3 COMMENT '每周期文章篇数目标',
  word_target INT NOT NULL DEFAULT 50 COMMENT '每周期单词数目标',
  word_category ENUM('CET4','CET6','GAOKAO') NOT NULL DEFAULT 'CET4' COMMENT '单词词库类型',
  paper_target INT NOT NULL DEFAULT 1 COMMENT '每周期真题套数目标',
  article_weight INT NOT NULL DEFAULT 40 COMMENT '文章权重(0-100)',
  word_weight INT NOT NULL DEFAULT 30 COMMENT '单词权重(0-100)',
  paper_weight INT NOT NULL DEFAULT 30 COMMENT '真题权重(0-100)',
  total_score DECIMAL(5,2) NOT NULL DEFAULT 100.00 COMMENT '满分值',
  status ENUM('DRAFT','ACTIVE','CLOSED') NOT NULL DEFAULT 'DRAFT' COMMENT '计划状态',
  created_by BIGINT NOT NULL COMMENT '创建者ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_task_plan_course (course_id),
  KEY idx_task_plan_status (status),
  KEY idx_task_plan_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='默认任务计划';

CREATE TABLE IF NOT EXISTS task_plan_classes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL COMMENT '计划ID',
  class_id BIGINT NOT NULL COMMENT '班级ID',
  opted_out TINYINT NOT NULL DEFAULT 0 COMMENT '教师是否退出(0否1是)',
  article_target_override INT DEFAULT NULL COMMENT '文章目标覆盖',
  word_target_override INT DEFAULT NULL COMMENT '单词目标覆盖',
  word_category_override ENUM('CET4','CET6','GAOKAO') DEFAULT NULL COMMENT '词库类型覆盖',
  paper_target_override INT DEFAULT NULL COMMENT '真题目标覆盖',
  article_weight_override INT DEFAULT NULL COMMENT '文章权重覆盖',
  word_weight_override INT DEFAULT NULL COMMENT '单词权重覆盖',
  paper_weight_override INT DEFAULT NULL COMMENT '真题权重覆盖',
  modified_by BIGINT DEFAULT NULL COMMENT '最后修改者',
  modified_time DATETIME DEFAULT NULL,
  UNIQUE KEY uk_plan_class (plan_id, class_id),
  KEY idx_tpc_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务计划班级关联';

CREATE TABLE IF NOT EXISTS task_plan_records (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_id BIGINT NOT NULL COMMENT '计划ID',
  student_id BIGINT NOT NULL COMMENT '学生ID',
  class_id BIGINT NOT NULL COMMENT '班级ID',
  period_start DATE NOT NULL COMMENT '周期开始日期',
  period_end DATE NOT NULL COMMENT '周期结束日期',
  articles_completed INT NOT NULL DEFAULT 0 COMMENT '已完成文章数',
  words_completed INT NOT NULL DEFAULT 0 COMMENT '已完成单词数',
  papers_completed INT NOT NULL DEFAULT 0 COMMENT '已完成真题数',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_record (plan_id, student_id, period_start),
  KEY idx_tpr_student (student_id),
  KEY idx_tpr_class (class_id),
  KEY idx_tpr_period (period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务计划学生周期记录';
