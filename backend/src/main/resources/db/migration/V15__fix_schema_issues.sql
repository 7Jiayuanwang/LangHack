-- V15: Fix runtime schema mismatches found in production

-- 1. backup_records.category
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'backup_records'
      AND COLUMN_NAME = 'category'
);
SET @add_category_sql = IF(
    @col_exists = 0,
    'ALTER TABLE backup_records ADD COLUMN category VARCHAR(50) DEFAULT ''SYSTEM'' COMMENT ''Backup category: SYSTEM/USER/AUTO'' AFTER backup_mode',
    'SELECT 1'
);
PREPARE stmt FROM @add_category_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. videos.subtitle_content_zh
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'videos'
      AND COLUMN_NAME = 'subtitle_content_zh'
);
SET @add_subtitle_sql = IF(
    @col_exists = 0,
    'ALTER TABLE videos ADD COLUMN subtitle_content_zh LONGTEXT COMMENT ''Chinese subtitle content (SRT/VTT)'' AFTER subtitle_content',
    'SELECT 1'
);
PREPARE stmt FROM @add_subtitle_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. Relax nullable constraints for school data tables.
ALTER TABLE course_selection
    MODIFY COLUMN credits DECIMAL(3,1) NULL COMMENT 'Upstream credits may be null',
    MODIFY COLUMN course_name VARCHAR(200) NULL COMMENT 'Upstream course name may be null',
    MODIFY COLUMN teacher_id VARCHAR(50) NULL COMMENT 'Upstream teacher id may be null',
    MODIFY COLUMN teacher_name VARCHAR(50) NULL COMMENT 'Upstream teacher name may be null',
    MODIFY COLUMN academic_year VARCHAR(20) NULL COMMENT 'Upstream academic year may be null',
    MODIFY COLUMN semester INT NULL COMMENT 'Upstream semester may be null',
    MODIFY COLUMN selection_time DATETIME NULL COMMENT 'Upstream selection time may be null',
    MODIFY COLUMN selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT 'Selection status';

ALTER TABLE course_schedule
    MODIFY COLUMN class_id VARCHAR(50) NULL COMMENT 'Teaching class id',
    MODIFY COLUMN day_of_week INT NULL COMMENT 'Day of week',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT 'Course period',
    MODIFY COLUMN week_range VARCHAR(50) NULL COMMENT 'Week range',
    MODIFY COLUMN class_location VARCHAR(100) NULL COMMENT 'Class location';

ALTER TABLE teaching_class
    MODIFY COLUMN class_name VARCHAR(100) NULL COMMENT 'Teaching class name',
    MODIFY COLUMN capacity INT NULL DEFAULT 0 COMMENT 'Class capacity',
    MODIFY COLUMN class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT 'Class status';

-- 4. Clean orphan rows before re-adding the FK.
DELETE cs
FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE cs.class_id IS NOT NULL
  AND tc.class_id IS NULL;

DELETE csel
FROM course_selection csel
LEFT JOIN school_course sc ON csel.course_code = sc.course_code
WHERE csel.course_code IS NOT NULL
  AND sc.course_code IS NULL;

-- 5. Rebuild course_schedule FK with ON DELETE SET NULL.
SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @drop_fk_sql = IF(
    @fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY fk_course_schedule_teaching_class',
    'SELECT 1'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'course_schedule_ibfk_1'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @drop_fk_sql = IF(
    @fk_exists > 0,
    'ALTER TABLE course_schedule DROP FOREIGN KEY course_schedule_ibfk_1',
    'SELECT 1'
);
PREPARE stmt FROM @drop_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists = (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @add_fk_sql = IF(
    @fk_exists = 0,
    'ALTER TABLE course_schedule ADD CONSTRAINT fk_course_schedule_teaching_class FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE',
    'SELECT 1'
);
PREPARE stmt FROM @add_fk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 6. Add missing field mappings.
INSERT IGNORE INTO field_mappings (
    data_source, source_field, target_field, source_type, target_type,
    converter, converter_config, default_value, is_required, sort_order, is_active, remark
) VALUES
    ('course_selection', 'XF', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, 'Credits from upstream if present'),
    ('course_selection', '学分', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, 'Credits from upstream if present');

INSERT IGNORE INTO field_mappings (
    data_source, source_field, target_field, source_type, target_type,
    converter, converter_config, default_value, is_required, sort_order, is_active, remark
) VALUES
    ('course_selection', 'XM', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, 'Student name if present'),
    ('course_selection', '姓名', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, 'Student name if present');

-- 7. Add composite indexes used by list queries.
SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_selection'
      AND INDEX_NAME = 'idx_student_academic'
);
SET @add_idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_student_academic ON course_selection(student_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'course_schedule'
      AND INDEX_NAME = 'idx_class_academic'
);
SET @add_idx_sql = IF(
    @idx_exists = 0,
    'CREATE INDEX idx_class_academic ON course_schedule(class_id, academic_year, semester)',
    'SELECT 1'
);
PREPARE stmt FROM @add_idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
