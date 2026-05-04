-- V29: 补充功能开关 + backup_records 分类字段
-- 2026-04-30

-- 1. 功能开关种子数据
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'student.lazy_course_selections', 'true',  '学生端按需 Lazy 同步选课记录（陈旧度阈值 1 天）', 0),
('feature_toggle', 'teacher.school_data_enabled',   'true',  '教师端学校数据功能（我的学生/课表/选课记录）', 0);

-- 2. backup_records 添加 category 字段（分类备份）
SET @add_backup_records_category_sql = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM information_schema.COLUMNS
            WHERE table_schema = DATABASE()
              AND table_name = 'backup_records'
              AND column_name = 'category'
        ),
        'SELECT 1',
        'ALTER TABLE `backup_records` ADD COLUMN `category` VARCHAR(50) NULL COMMENT ''备份分类: system_config/user_data/business_data/school_data/full'' AFTER `backup_mode`'
    )
);
PREPARE add_backup_records_category_stmt FROM @add_backup_records_category_sql;
EXECUTE add_backup_records_category_stmt;
DEALLOCATE PREPARE add_backup_records_category_stmt;
