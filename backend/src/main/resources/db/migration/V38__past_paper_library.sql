CREATE TABLE past_papers (
  id BIGINT NOT NULL AUTO_INCREMENT,
  paper_key VARCHAR(160) NOT NULL,
  category VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  exam_date VARCHAR(32) DEFAULT NULL,
  duration_minutes INT NOT NULL DEFAULT 30,
  scope VARCHAR(100) DEFAULT NULL,
  question_count INT NOT NULL,
  content_json LONGTEXT NOT NULL,
  published TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT DEFAULT NULL,
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_past_paper_key (paper_key),
  KEY idx_past_papers_category_published (category, published)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Shared past examination paper library';
