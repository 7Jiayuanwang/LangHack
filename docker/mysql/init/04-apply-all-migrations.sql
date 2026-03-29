-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- LangHack 鍚堝苟杩佺Щ鑴氭湰锛圡ySQL 8.0 鍏煎锛?
-- 灏?Flyway V1 ~ V5 鐨勬墍鏈夎縼绉诲悎骞朵负鍗曚釜骞傜瓑鑴氭湰
-- 瀵瑰簲 Flyway 鏁村悎鏂囦欢:
--   V1__scoring_and_assignments.sql  (鍘?V5+V6+V7+V17)
--   V2__text_and_learning.sql        (鍘?V8+V9+V10+V12+V13+V14+V18)
--   V3__media_and_annotations.sql    (鍘?V1.2+V11+V15+V16)
--   V4__backup_system.sql            (鍘?V19+V20+V21+V22)
--   V5__monitoring_and_access.sql    (鍘?V23+V24+V25)
-- 鏃犺澶囦唤SQL鐗堟湰濡備綍锛屾鑴氭湰鍧囧彲瀹夊叏閲嶅鎵ц
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€

USE `english_learning`;

-- ========================================================
-- 杈呭姪瀛樺偍杩囩▼锛氭潯浠舵坊鍔犲垪锛圡ySQL 8.0 涓嶆敮鎸?ADD COLUMN IF NOT EXISTS锛?
-- ========================================================
DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
DELIMITER //
CREATE PROCEDURE `add_column_if_not_exists`(
    IN p_table VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_definition TEXT
)
BEGIN
    DECLARE col_count INT;
    SELECT COUNT(*) INTO col_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column;

    IF col_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 杈呭姪瀛樺偍杩囩▼锛氭潯浠舵坊鍔犵储寮?
DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;
DELIMITER //
CREATE PROCEDURE `add_index_if_not_exists`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns TEXT
)
BEGIN
    DECLARE idx_count INT;
    SELECT COUNT(*) INTO idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND INDEX_NAME = p_index;

    IF idx_count = 0 THEN
        SET @sql = CONCAT('CREATE INDEX `', p_index, '` ON `', p_table, '` (', p_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 杈呭姪瀛樺偍杩囩▼锛氭潯浠舵坊鍔犲敮涓€绱㈠紩
DROP PROCEDURE IF EXISTS `add_unique_index_if_not_exists`;
DELIMITER //
CREATE PROCEDURE `add_unique_index_if_not_exists`(
    IN p_table VARCHAR(64),
    IN p_index VARCHAR(64),
    IN p_columns TEXT
)
BEGIN
    DECLARE idx_count INT;
    SELECT COUNT(*) INTO idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND INDEX_NAME = p_index;

    IF idx_count = 0 THEN
        SET @sql = CONCAT('CREATE UNIQUE INDEX `', p_index, '` ON `', p_table, '` (', p_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;


-- ========================================================
-- V6: assignments 娣诲姞 weight 瀛楁
-- ========================================================
CALL add_column_if_not_exists('assignments', 'weight',
    "DECIMAL(5,2) DEFAULT 1.00 COMMENT '浠诲姟鏉冮噸锛堢敤浜庤绠楀姞鏉冨钩鍧囧垎锛岄粯璁?.00锛?");
CALL add_index_if_not_exists('assignments', 'idx_weight', '`weight`');


-- ========================================================
-- V7: assignments 娣诲姞 ideal_study_time_minutes 瀛楁
-- ========================================================
CALL add_column_if_not_exists('assignments', 'ideal_study_time_minutes',
    "INT DEFAULT 60 COMMENT '鐞嗘兂瀛︿範鏃堕暱锛堝垎閽燂級锛岀敤浜庤绠楀垎鏁帮紝榛樿60鍒嗛挓'");
CALL add_index_if_not_exists('assignments', 'idx_ideal_study_time', '`ideal_study_time_minutes`');


-- ========================================================
-- V8: learning_records 娣诲姞 text_id 瀛楁
-- ========================================================
CALL add_column_if_not_exists('learning_records', 'text_id',
    "BIGINT COMMENT '鍏宠仈鐨勮鏂嘔D' AFTER `word_id`");
CALL add_index_if_not_exists('learning_records', 'idx_text_id', '`text_id`');


-- ========================================================
-- V9: texts 娣诲姞 chinese_parallel_content 瀛楁
-- ========================================================
CALL add_column_if_not_exists('texts', 'chinese_parallel_content',
    "TEXT COMMENT '涓嫳瀵圭収鐗堟湰鍐呭'");


-- ========================================================
-- V10: texts 娣诲姞 markdown 鏀寔
-- ========================================================
CALL add_column_if_not_exists('texts', 'markdown_content',
    "LONGTEXT COMMENT 'Markdown鏍煎紡鐨勮鏂囧唴瀹? AFTER `content`");
CALL add_column_if_not_exists('texts', 'content_type',
    "ENUM('TEXT','MARKDOWN') DEFAULT 'TEXT' COMMENT '鍐呭绫诲瀷锛歍EXT-绾枃鏈紝MARKDOWN-Markdown鏍煎紡'");


-- ========================================================
-- V11: 鍒涘缓 media 琛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `media` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '濯掍綋ID',
  `media_type` ENUM('IMAGE','VIDEO') NOT NULL COMMENT '濯掍綋绫诲瀷',
  `file_name` VARCHAR(255) NOT NULL COMMENT '鍘熷鏂囦欢鍚?,
  `file_content` LONGBLOB NOT NULL COMMENT '鏂囦欢鍐呭',
  `file_size` BIGINT DEFAULT 0 COMMENT '鏂囦欢澶у皬锛堝瓧鑺傦級',
  `mime_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME绫诲瀷',
  `width` INT DEFAULT NULL COMMENT '瀹藉害锛堝儚绱狅紝浠呭浘鐗囷級',
  `height` INT DEFAULT NULL COMMENT '楂樺害锛堝儚绱狅紝浠呭浘鐗囷級',
  `duration` INT DEFAULT NULL COMMENT '鏃堕暱锛堢锛屼粎瑙嗛锛?,
  `uploaded_by` BIGINT DEFAULT NULL COMMENT '涓婁紶鑰匢D',
  `text_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈鐨勮鏂嘔D',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '濯掍綋鎻忚堪',
  `status` ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'APPROVED' COMMENT '瀹℃牳鐘舵€?,
  `deleted` TINYINT DEFAULT 0 COMMENT '鏄惁鍒犻櫎 0-鏈垹闄?1-宸插垹闄?,
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_media_type` (`media_type`),
  KEY `idx_uploaded_by` (`uploaded_by`),
  KEY `idx_text_id` (`text_id`),
  KEY `idx_status` (`status`),
  KEY `idx_deleted` (`deleted`),
  KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='濯掍綋琛紙鍥剧墖鍜岃棰戯級';


-- ========================================================
-- V12: texts 娣诲姞澶氱増鏈?markdown 瀛楁
-- ========================================================
CALL add_column_if_not_exists('texts', 'cet4_markdown_content',
    "LONGTEXT COMMENT 'CET-4闅惧害鐨凪arkdown鐗堟湰鍐呭'");
CALL add_column_if_not_exists('texts', 'cet6_markdown_content',
    "LONGTEXT COMMENT 'CET-6闅惧害鐨凪arkdown鐗堟湰鍐呭'");
CALL add_column_if_not_exists('texts', 'academic_markdown_content',
    "LONGTEXT COMMENT '瀛︽湳鐗堢殑Markdown鐗堟湰鍐呭'");
CALL add_column_if_not_exists('texts', 'chinese_parallel_markdown_content',
    "LONGTEXT COMMENT '涓嫳瀵圭収鐨凪arkdown鐗堟湰鍐呭'");


-- ========================================================
-- V13: texts.unit_id 鏀逛负鍙┖
-- ========================================================
-- 姝や慨鏀逛负 MODIFY COLUMN锛屽箓绛夋墽琛屽畨鍏?
ALTER TABLE `texts` MODIFY COLUMN `unit_id` BIGINT DEFAULT NULL COMMENT '鍗曞厓ID锛堝彲涓虹┖锛岃〃绀烘湭鍒嗛厤鍒板崟鍏冿級';


-- ========================================================
-- V14: 鍒涘缓 text_learning_records 琛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `text_learning_records` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `student_id` BIGINT NOT NULL COMMENT '瀛︾敓ID',
  `text_id` BIGINT NOT NULL COMMENT '璇炬枃ID',
  `assignment_id` BIGINT DEFAULT NULL COMMENT '鍏宠仈鐨勪换鍔D',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '闃呰寮€濮嬫椂闂?,
  `end_time` DATETIME DEFAULT NULL COMMENT '闃呰缁撴潫鏃堕棿',
  `duration_seconds` INT DEFAULT 0 COMMENT '闃呰鏃堕暱(绉?',
  `is_completed` TINYINT(1) DEFAULT 0 COMMENT '鏄惁宸插畬鎴愰槄璇?,
  `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_text` (`student_id`, `text_id`),
  KEY `idx_assignment` (`assignment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鏂囩珷瀛︿範璁板綍琛?;


-- ========================================================
-- V15: vocabulary_books 娣诲姞 SM-2 闂撮殧閲嶅瀛楁
-- ========================================================
CALL add_column_if_not_exists('vocabulary_books', 'repetition',
    "INT DEFAULT 0 COMMENT 'SM-2 repetition number'");
CALL add_column_if_not_exists('vocabulary_books', 'interval_days',
    "INT DEFAULT 0 COMMENT 'SM-2 interval in days'");
CALL add_column_if_not_exists('vocabulary_books', 'easiness_factor',
    "DOUBLE DEFAULT 2.5 COMMENT 'SM-2 easiness factor'");
CALL add_column_if_not_exists('vocabulary_books', 'next_review_time',
    "DATETIME COMMENT 'SM-2 next scheduled review time'");


-- ========================================================
-- V16: 鍒涘缓 underlines 琛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `underlines` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `text_id` BIGINT NOT NULL,
    `selected_text` TEXT,
    `start_index` INT,
    `end_index` INT,
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_text_id` (`text_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- V1.2: underlines 娣诲姞 serialized_range 瀛楁
CALL add_column_if_not_exists('underlines', 'serialized_range',
    "TEXT COMMENT 'Serialized DOM range for robust highlighting'");


-- ========================================================
-- V17: score_records 娣诲姞鍞竴绾︽潫锛堥槻姝㈤噸澶嶅垎鏁拌褰曪級
-- ========================================================
-- 鍏堟竻鐞嗗彲鑳界殑閲嶅鏁版嵁
DROP PROCEDURE IF EXISTS `add_score_records_unique`;
DELIMITER //
CREATE PROCEDURE `add_score_records_unique`()
BEGIN
    DECLARE idx_count INT;
    SELECT COUNT(*) INTO idx_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'score_records'
      AND INDEX_NAME = 'uk_student_assignment_type';

    IF idx_count = 0 THEN
        -- 娓呯悊閲嶅鏁版嵁锛堜繚鐣橧D鏈€澶х殑璁板綍锛?
        DELETE sr1 FROM score_records sr1
        INNER JOIN score_records sr2
          ON sr1.student_id = sr2.student_id
          AND sr1.assignment_id = sr2.assignment_id
          AND sr1.score_type = sr2.score_type
          AND sr1.id < sr2.id
        WHERE sr1.assignment_id IS NOT NULL;

        -- 娣诲姞鍞竴绾︽潫
        ALTER TABLE score_records ADD UNIQUE KEY `uk_student_assignment_type` (`student_id`, `assignment_id`, `score_type`);
    END IF;
END //
DELIMITER ;
CALL add_score_records_unique();
DROP PROCEDURE IF EXISTS `add_score_records_unique`;


-- ========================================================
-- V18: texts 娣诲姞 is_featured 瀛楁 + 鍒涘缓 user_text_reads 琛?
-- ========================================================
CALL add_column_if_not_exists('texts', 'is_featured',
    "TINYINT(1) DEFAULT 0 COMMENT '鏄惁鎺ㄨ崘'");

CREATE TABLE IF NOT EXISTS `user_text_reads` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `text_id` BIGINT NOT NULL,
    `read_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_text` (`user_id`, `text_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ========================================================
-- V19: 鍒涘缓 backup_records 琛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `backup_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `filename` VARCHAR(255) NOT NULL COMMENT '澶囦唤鏂囦欢鍚?,
    `file_size` BIGINT DEFAULT 0 COMMENT '鏂囦欢澶у皬(瀛楄妭)',
    `backup_type` VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '澶囦唤绫诲瀷: MANUAL/SCHEDULED',
    `status` VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '鐘舵€? IN_PROGRESS/SUCCESS/FAILED',
    `storage_path` VARCHAR(500) NOT NULL COMMENT '瀛樺偍璺緞',
    `created_by` VARCHAR(100) COMMENT '鍒涘缓鑰?,
    `error_message` TEXT COMMENT '閿欒淇℃伅',
    `database_size` BIGINT DEFAULT 0 COMMENT '鏁版嵁搴撳ぇ灏?瀛楄妭)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    `completed_time` DATETIME COMMENT '瀹屾垚鏃堕棿',
    INDEX `idx_backup_type` (`backup_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='鏁版嵁搴撳浠借褰曡〃';


-- ========================================================
-- V20: backup_records 娣诲姞澧為噺澶囦唤鏀寔瀛楁
-- ========================================================
CALL add_column_if_not_exists('backup_records', 'backup_mode',
    "VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '澶囦唤妯″紡: FULL/INCREMENTAL' AFTER `backup_type`");
CALL add_column_if_not_exists('backup_records', 'base_backup_id',
    "BIGINT DEFAULT NULL COMMENT '澧為噺澶囦唤鐨勫熀鍑嗗叏閲忓浠絀D' AFTER `backup_mode`");
CALL add_index_if_not_exists('backup_records', 'idx_backup_mode', '`backup_mode`');


-- ========================================================
-- V21: 鍒涘缓 backup_operation_logs 琛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `backup_operation_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `operator` VARCHAR(100) NOT NULL COMMENT '鎿嶄綔浜?,
    `operation_type` VARCHAR(50) NOT NULL COMMENT '鎿嶄綔绫诲瀷',
    `target_id` BIGINT COMMENT '鐩爣澶囦唤璁板綍ID',
    `target_name` VARCHAR(255) COMMENT '鐩爣澶囦唤鏂囦欢鍚?,
    `detail` VARCHAR(1000) COMMENT '鎿嶄綔璇︽儏',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '鎿嶄綔缁撴灉: SUCCESS/FAILED',
    `ip_address` VARCHAR(50) COMMENT '鎿嶄綔IP鍦板潃',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鎿嶄綔鏃堕棿',
    INDEX `idx_operator` (`operator`),
    INDEX `idx_operation_type` (`operation_type`),
    INDEX `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='澶囦唤鎿嶄綔鏃ュ織琛?;


-- ========================================================
-- V22: 鍒涘缓 backup_config 琛紙鍗曡閰嶇疆锛?
-- ========================================================
CREATE TABLE IF NOT EXISTS `backup_config` (
    `id` INT NOT NULL DEFAULT 1,
    `auto_backup_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '鑷姩澶囦唤寮€鍏?,
    `backup_cron` VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * ?' COMMENT 'cron琛ㄨ揪寮?,
    `backup_hour` INT NOT NULL DEFAULT 2 COMMENT '澶囦唤灏忔椂(0-23)',
    `backup_minute` INT NOT NULL DEFAULT 0 COMMENT '澶囦唤鍒嗛挓(0-59)',
    `retention_days` INT NOT NULL DEFAULT 30 COMMENT '淇濈暀澶╂暟',
    `max_count` INT NOT NULL DEFAULT 50 COMMENT '鏈€澶у浠芥暟閲?,
    `incremental_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '澧為噺澶囦唤寮€鍏?,
    `full_backup_interval_days` INT NOT NULL DEFAULT 7 COMMENT '鍏ㄩ噺澶囦唤闂撮殧澶╂暟',
    `updated_by` VARCHAR(50) DEFAULT NULL COMMENT '鏈€鍚庢洿鏂颁汉',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏈€鍚庢洿鏂版椂闂?,
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_single_row` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='澶囦唤閰嶇疆琛?鍗曡)';

-- 鎻掑叆榛樿閰嶇疆锛堝鏋滀笉瀛樺湪锛?
INSERT IGNORE INTO `backup_config` (`id`, `auto_backup_enabled`, `backup_cron`, `backup_hour`, `backup_minute`,
    `retention_days`, `max_count`, `incremental_enabled`, `full_backup_interval_days`)
VALUES (1, 1, '0 0 2 * * ?', 2, 0, 30, 50, 0, 7);


-- ========================================================
-- V23: 鍒涘缓璁块棶鐩戞帶鍜孉I璋冪敤缁熻琛?
-- ========================================================

-- 璁块棶鏃ュ織琛?
CREATE TABLE IF NOT EXISTS `access_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT DEFAULT NULL COMMENT '鐢ㄦ埛ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '鐢ㄦ埛鍚?,
    `method` VARCHAR(10) NOT NULL COMMENT 'HTTP鏂规硶',
    `uri` VARCHAR(500) NOT NULL COMMENT '璇锋眰URI',
    `query_string` VARCHAR(1000) DEFAULT NULL COMMENT '鏌ヨ鍙傛暟',
    `status_code` INT NOT NULL COMMENT 'HTTP鐘舵€佺爜',
    `response_time_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '鍝嶅簲鏃堕棿(姣)',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT 'IP鍦板潃',
    `user_agent` VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX `idx_created_time` (`created_time`),
    INDEX `idx_username` (`username`),
    INDEX `idx_uri` (`uri`(191)),
    INDEX `idx_status_code` (`status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='璁块棶鏃ュ織琛?;

-- 姣忓皬鏃惰闂粺璁?
CREATE TABLE IF NOT EXISTS `access_stats_hourly` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stat_hour` DATETIME NOT NULL COMMENT '缁熻灏忔椂(鏁寸偣)',
    `total_requests` INT NOT NULL DEFAULT 0 COMMENT '鎬昏姹傛暟',
    `unique_users` INT NOT NULL DEFAULT 0 COMMENT '鐙珛鐢ㄦ埛鏁?,
    `avg_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿(姣)',
    `max_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?姣)',
    `error_count` INT NOT NULL DEFAULT 0 COMMENT '閿欒鏁?4xx+5xx)',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY `uk_stat_hour` (`stat_hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='姣忓皬鏃惰闂粺璁?;

-- 姣忔棩璁块棶缁熻
CREATE TABLE IF NOT EXISTS `access_stats_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stat_date` DATE NOT NULL COMMENT '缁熻鏃ユ湡',
    `total_requests` INT NOT NULL DEFAULT 0 COMMENT '鎬昏姹傛暟',
    `unique_users` INT NOT NULL DEFAULT 0 COMMENT '鐙珛鐢ㄦ埛鏁?,
    `peak_concurrent` INT NOT NULL DEFAULT 0 COMMENT '宄板€煎苟鍙戞暟',
    `avg_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿(姣)',
    `max_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?姣)',
    `error_count` INT NOT NULL DEFAULT 0 COMMENT '閿欒鏁?,
    `top_pages` TEXT DEFAULT NULL COMMENT '鐑棬椤甸潰JSON',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY `uk_stat_date` (`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='姣忔棩璁块棶缁熻';

-- AI璋冪敤鏃ュ織琛?
CREATE TABLE IF NOT EXISTS `ai_call_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service_name` VARCHAR(50) NOT NULL COMMENT '鏈嶅姟鍚嶇О',
    `method_name` VARCHAR(100) DEFAULT NULL COMMENT '璋冪敤鏂规硶鍚?,
    `success` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '鏄惁鎴愬姛',
    `response_time_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '鍝嶅簲鏃堕棿(姣)',
    `error_message` VARCHAR(500) DEFAULT NULL COMMENT '閿欒淇℃伅',
    `input_tokens` INT DEFAULT NULL COMMENT '杈撳叆token鏁?,
    `output_tokens` INT DEFAULT NULL COMMENT '杈撳嚭token鏁?,
    `model_name` VARCHAR(100) DEFAULT NULL COMMENT '妯″瀷鍚嶇О',
    `triggered_by` VARCHAR(100) DEFAULT NULL COMMENT '瑙﹀彂鐢ㄦ埛',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    INDEX `idx_service_name` (`service_name`),
    INDEX `idx_created_time` (`created_time`),
    INDEX `idx_success` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI璋冪敤鏃ュ織琛?;

-- AI璋冪敤鏃ョ粺璁?
CREATE TABLE IF NOT EXISTS `ai_call_stats_daily` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `stat_date` DATE NOT NULL COMMENT '缁熻鏃ユ湡',
    `service_name` VARCHAR(50) NOT NULL COMMENT '鏈嶅姟鍚嶇О',
    `total_calls` INT NOT NULL DEFAULT 0 COMMENT '鎬昏皟鐢ㄦ鏁?,
    `success_calls` INT NOT NULL DEFAULT 0 COMMENT '鎴愬姛娆℃暟',
    `failed_calls` INT NOT NULL DEFAULT 0 COMMENT '澶辫触娆℃暟',
    `avg_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '骞冲潎鍝嶅簲鏃堕棿',
    `max_response_time_ms` INT NOT NULL DEFAULT 0 COMMENT '鏈€澶у搷搴旀椂闂?,
    `total_input_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '鎬昏緭鍏oken',
    `total_output_tokens` BIGINT NOT NULL DEFAULT 0 COMMENT '鎬昏緭鍑簍oken',
    `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
    UNIQUE KEY `uk_date_service` (`stat_date`, `service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI璋冪敤鏃ョ粺璁?;


-- ========================================================
-- V24: users.role 娣诲姞 OPS 瑙掕壊
-- ========================================================
-- MySQL 8.0 MODIFY COLUMN 鏄箓绛夌殑锛堥噸澶嶆墽琛屽畨鍏級
ALTER TABLE `users` MODIFY COLUMN `role` ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER' COMMENT '瑙掕壊';


-- ========================================================
-- V25: 纭繚婕旂ず璐﹀彿瀛樺湪涓斿彲姝ｅ父鐧诲綍
-- 瀵嗙爜: admin123 (澶嶇敤 admin 璐﹀彿鐨?BCrypt hash)
-- ========================================================
-- 纭繚 teacher 璐﹀彿瀛樺湪
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'teacher',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'TEACHER', '婕旂ず鏁欏笀', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

-- 纭繚 student 璐﹀彿瀛樺湪
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'student',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'STUDENT', '婕旂ず瀛︾敓', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');

-- 鍚屾瀵嗙爜锛堢‘淇濅笌 admin 涓€鑷达級+ 纭繚 ACTIVE 鐘舵€?+ 鏈杞垹闄?
UPDATE users SET
  password = (SELECT pw FROM (SELECT password AS pw FROM users WHERE username = 'admin' LIMIT 1) tmp),
  status = 'ACTIVE',
  deleted = 0
WHERE username IN ('teacher', 'student');


-- ========================================================
-- 琛ュ厖锛氭爣璁颁竴浜涙枃绔犱负鎺ㄨ崘锛堝鏋滄病鏈夋帹鑽愭枃绔狅級
-- ========================================================
DROP PROCEDURE IF EXISTS `seed_featured_texts`;
DELIMITER //
CREATE PROCEDURE `seed_featured_texts`()
BEGIN
    DECLARE featured_count INT;
    SELECT COUNT(*) INTO featured_count FROM `texts` WHERE `is_featured` = 1 AND `deleted` = 0;

    IF featured_count = 0 THEN
        UPDATE `texts` SET `is_featured` = 1
        WHERE `deleted` = 0 AND `is_public` = 1
        ORDER BY `view_count` DESC, `id` ASC
        LIMIT 20;
    END IF;
END //
DELIMITER ;
CALL seed_featured_texts();
DROP PROCEDURE IF EXISTS `seed_featured_texts`;


-- ========================================================
-- V6: 绯荤粺閰嶇疆琛?+ AI 缈昏瘧鏈嶅姟榛樿閰嶇疆
-- ========================================================
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_group` VARCHAR(50) NOT NULL COMMENT '閰嶇疆鍒嗙粍: ai_translate, ai_article, ai_chat',
    `config_key` VARCHAR(100) NOT NULL COMMENT '閰嶇疆閿?,
    `config_value` TEXT COMMENT '閰嶇疆鍊?,
    `description` VARCHAR(255) COMMENT '閰嶇疆璇存槑锛堣繍缁撮潰鏉挎樉绀猴級',
    `is_secret` TINYINT DEFAULT 0 COMMENT '鏄惁鏁忔劅锛堝墠绔劚鏁忔樉绀猴級',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绯荤粺閰嶇疆琛?;

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 鍦板潃锛圤penAI 鍏煎锛?, 0),
('ai_translate', 'api_key', '', 'API 瀵嗛挜', 1),
('ai_translate', 'model', 'tencent/Hunyuan-MT-7B', '妯″瀷鍚嶇О', 0),
('ai_translate', 'enabled', 'true', '鏄惁鍚敤', 0),
('ai_translate', 'timeout', '30000', '瓒呮椂鏃堕棿(ms)', 0);


-- ========================================================
-- 娓呯悊杈呭姪瀛樺偍杩囩▼
-- ========================================================
DROP PROCEDURE IF EXISTS `add_column_if_not_exists`;
DROP PROCEDURE IF EXISTS `add_index_if_not_exists`;
DROP PROCEDURE IF EXISTS `add_unique_index_if_not_exists`;


-- ========================================================
-- 鏇存柊 Flyway 鍘嗗彶琛紙鏍囪鎵€鏈夎縼绉讳负宸插畬鎴愶級
-- 闃叉 Spring Boot 鍚姩鏃?Flyway 灏濊瘯閲嶅鎵ц
-- ========================================================
CREATE TABLE IF NOT EXISTS `update_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cron_expr` VARCHAR(64) NOT NULL DEFAULT '0 0 3 * * ?' COMMENT 'Spring cron 表达式',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时自动更新',
    `git_branch` VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT '跟踪的 git 分支',
    `backup_before_update` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '更新前是否备份数据库',
    `last_run_at` DATETIME DEFAULT NULL COMMENT '最近一次执行时间',
    `last_run_status` VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统自动更新配置';

INSERT IGNORE INTO `update_config`
    (`id`, `cron_expr`, `enabled`, `git_branch`, `backup_before_update`, `last_run_status`)
VALUES (1, '0 0 3 * * ?', 0, 'master', 1, 'NEVER');

DROP PROCEDURE IF EXISTS `mark_flyway_migration`;
DELIMITER //
CREATE PROCEDURE `mark_flyway_migration`(
    IN p_version VARCHAR(50),
    IN p_description VARCHAR(200),
    IN p_script VARCHAR(200)
)
BEGIN
    DECLARE migration_count INT;

    -- 纭繚 flyway_schema_history 琛ㄥ瓨鍦?
    CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
        `installed_rank` INT NOT NULL,
        `version` VARCHAR(50) DEFAULT NULL,
        `description` VARCHAR(200) NOT NULL,
        `type` VARCHAR(20) NOT NULL,
        `script` VARCHAR(1000) NOT NULL,
        `checksum` INT DEFAULT NULL,
        `installed_by` VARCHAR(100) NOT NULL,
        `installed_on` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        `execution_time` INT NOT NULL,
        `success` TINYINT(1) NOT NULL,
        PRIMARY KEY (`installed_rank`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    SELECT COUNT(*) INTO migration_count
    FROM `flyway_schema_history`
    WHERE `version` = p_version AND `success` = 1;

    IF migration_count = 0 THEN
        -- 鍒犻櫎鍙兘瀛樺湪鐨勫け璐ヨ褰?
        DELETE FROM `flyway_schema_history` WHERE `version` = p_version AND `success` = 0;

        -- 鎻掑叆鎴愬姛璁板綍
        INSERT INTO `flyway_schema_history`
            (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `execution_time`, `success`)
        VALUES (
            (SELECT COALESCE(MAX(t.installed_rank), 0) + 1 FROM (SELECT installed_rank FROM flyway_schema_history) AS t),
            p_version, p_description, 'SQL', p_script, 0, 'docker-init', 0, 1
        );
    END IF;
END //
DELIMITER ;

-- 鏍囪鏁村悎鍚庣殑杩佺Щ涓哄凡瀹屾垚锛?涓暣鍚堟枃浠讹級
CALL mark_flyway_migration('1', 'scoring and assignments', 'V1__scoring_and_assignments.sql');
CALL mark_flyway_migration('2', 'text and learning', 'V2__text_and_learning.sql');
CALL mark_flyway_migration('3', 'media and annotations', 'V3__media_and_annotations.sql');
CALL mark_flyway_migration('4', 'backup system', 'V4__backup_system.sql');
CALL mark_flyway_migration('5', 'monitoring and access', 'V5__monitoring_and_access.sql');
CALL mark_flyway_migration('6', 'system config and ai providers', 'V6__system_config_and_ai_providers.sql');
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `api_url` VARCHAR(500) NOT NULL,
    `api_key` TEXT NOT NULL,
    `model` VARCHAR(200) NOT NULL,
    `weight` INT NOT NULL DEFAULT 1,
    `enabled` TINYINT NOT NULL DEFAULT 1,
    `timeout_ms` INT NOT NULL DEFAULT 30000,
    `remark` VARCHAR(255),
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `ai_provider` (`id`, `service`, `name`, `api_url`, `api_key`, `model`, `weight`, `enabled`, `timeout_ms`, `remark`)
SELECT 1, 'translate', 'SiliconFlow (default)',
       COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_url'), 'https://api.siliconflow.cn/v1/chat/completions'),
       COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_key'), ''),
       COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='model'), 'tencent/Hunyuan-MT-7B'),
       1, 1,
       COALESCE(CAST((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='timeout') AS UNSIGNED), 30000),
       'migrated from system_config'
WHERE NOT EXISTS (SELECT 1 FROM ai_provider WHERE service = 'translate');
CALL mark_flyway_migration('7', 'ai provider multi', 'V7__ai_provider_multi.sql');

CREATE TABLE IF NOT EXISTS `update_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `cron_expr` VARCHAR(64) NOT NULL DEFAULT '0 0 3 * * ?' COMMENT 'Spring cron 表达式',
    `enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时自动更新',
    `git_branch` VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT '跟踪的 git 分支',
    `backup_before_update` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '更新前是否备份数据库',
    `last_run_at` DATETIME DEFAULT NULL COMMENT '最近一次执行时间',
    `last_run_status` VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统自动更新配置';

INSERT IGNORE INTO `update_config`
    (`id`, `cron_expr`, `enabled`, `git_branch`, `backup_before_update`, `last_run_status`)
VALUES (1, '0 0 3 * * ?', 0, 'master', 1, 'NEVER');
CALL mark_flyway_migration('8', 'update config', 'V8__update_config.sql');

ALTER TABLE `media`
    ADD COLUMN IF NOT EXISTS `file_path` VARCHAR(500) COMMENT 'stored media file path';

ALTER TABLE `media`
    ADD COLUMN IF NOT EXISTS `thumbnail_path` VARCHAR(500) COMMENT 'stored thumbnail file path';
CALL mark_flyway_migration('9', 'media path columns', 'V9__media_path_columns.sql');

DROP PROCEDURE IF EXISTS `mark_flyway_migration`;


-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
-- 杩佺Щ瀹屾垚
-- 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€
SELECT '鉁?All migrations (V1 ~ V6, consolidated) applied successfully!' AS result;


