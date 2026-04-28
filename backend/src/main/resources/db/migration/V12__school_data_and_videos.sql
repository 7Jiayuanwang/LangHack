-- ═══════════════════════════════════════════════════════════════
-- V12: 学校数据集成 + 视频管理表
-- 整合自: V12__school_data_integration.sql + V12__add_videos_table.sql
-- ═══════════════════════════════════════════════════════════════
-- ═══════════════════════════════════════════════════════════════
-- V12: 学校数据集成整合
-- 整合自: V12.1 (字段映射) + V13 (system_config seed)
--       + V14 (student_schedule 表) + V16 (course→school_course 重命名)
--       + V18 (功能开关 seed) + V19 (字段映射) + V20 (API 认证 appId+appSecret)
--       + V20-align (2026-04-21 按 CDSP 官方 PDF 对齐 field_mappings)
--
-- 历史合并说明:
--   - 2026-04-19: 九个补丁 (V12.1/V13/V14/V16/V18/V19/V20) 首次合并入 V12
--   - 2026-04-21: 按官方 PDF 原文修正 field_mappings, 剔除 V12.1/V19 遗留
--     snake_case 冗余映射（FieldMappingServiceImpl 会自动将 target_field
--     转 camelCase, snake_case/camelCase 两套是等价重复）, 合并 V20-align
--
-- 注意: Flyway 在本项目生产环境禁用 (application.yml flyway.enabled=false),
--       实际生产部署走 docker/mysql/init/04-apply-all-migrations.sql
--       CI test profile 启用 Flyway (application-test.yml)
--
-- 执行顺序:
--   Part 1: Schema 变更
--   Part 2: system_config seed
--   Part 3: field_mappings 数据（按 PDF 原文）
-- ═══════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════
-- Part 1.1: 重命名 course → school_course (原 V16)
-- SchoolCourse 实体 @TableName("school_course"), 修正 V9 遗留的 course 表名
-- ═══════════════════════════════════════════════════════════════

SET @course_exists = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course');
SET @school_course_exists = (SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'school_course');

-- 如果 course 存在且 school_course 不存在 → 重命名
SET @rename_sql = IF(@course_exists = 1 AND @school_course_exists = 0,
    'RENAME TABLE `course` TO `school_course`',
    'SELECT 1');
PREPARE stmt FROM @rename_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 如果都不存在（全新环境）→ 创建 school_course
SET @create_sql = IF(@course_exists = 0 AND @school_course_exists = 0,
    'CREATE TABLE IF NOT EXISTS school_course (
        id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT ''主键'',
        course_code VARCHAR(50) NOT NULL UNIQUE COMMENT ''课程编号 (KCBH)'',
        course_name VARCHAR(200) NOT NULL COMMENT ''课程名称 (KCMC)'',
        course_name_en VARCHAR(200) COMMENT ''课程英文名称 (KCYWMC)'',
        course_short_name VARCHAR(100) COMMENT ''课程简称 (KCJC)'',
        course_nature VARCHAR(50) NOT NULL COMMENT ''课程性质 (KCXZ)'',
        course_category VARCHAR(50) NOT NULL COMMENT ''课程类别 (KCLB)'',
        credits DECIMAL(3,1) NOT NULL COMMENT ''学分 (XF)'',
        total_hours INT NOT NULL COMMENT ''总学时 (ZXS)'',
        theory_hours INT COMMENT ''理论学时 (LLXS)'',
        experiment_hours INT COMMENT ''实验学时 (SYXS)'',
        practice_hours INT COMMENT ''实践学时 (SJXS)'',
        computer_hours INT COMMENT ''上机学时 (SJXS_PC)'',
        college_code VARCHAR(50) NOT NULL COMMENT ''开课学院编号 (KKYXBH)'',
        college_name VARCHAR(100) NOT NULL COMMENT ''开课学院名称 (KKYXMC)'',
        department_code VARCHAR(50) COMMENT ''开课系部编号 (KKXBBH)'',
        department_name VARCHAR(100) COMMENT ''开课系部名称 (KKXBMC)'',
        leader_code VARCHAR(50) COMMENT ''课程负责人工号 (KCFZRGH)'',
        leader_name VARCHAR(50) COMMENT ''课程负责人姓名 (KCFZRXM)'',
        assessment_method VARCHAR(50) NOT NULL COMMENT ''考核方式 (KHFS)'',
        is_bilingual VARCHAR(10) COMMENT ''是否双语教学 (SFSYJX)'',
        is_online VARCHAR(10) COMMENT ''是否网络课程 (SFWLKC)'',
        applicable_majors VARCHAR(500) COMMENT ''适用专业 (SYZY)'',
        prerequisite_codes VARCHAR(500) COMMENT ''先修课程编号 (XXKCBH)'',
        prerequisite_names VARCHAR(500) COMMENT ''先修课程名称 (XXKCMC)'',
        course_intro TEXT COMMENT ''课程简介 (KCJJ)'',
        teaching_objectives TEXT COMMENT ''教学目标 (JXMB)'',
        course_status VARCHAR(20) NOT NULL COMMENT ''课程状态 (KCZT)'',
        sync_time DATETIME NOT NULL COMMENT ''同步时间'',
        sync_source VARCHAR(20) NOT NULL COMMENT ''同步来源'',
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'',
        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'',
        INDEX idx_college (college_code),
        INDEX idx_nature (course_nature),
        INDEX idx_category (course_category),
        INDEX idx_sync_time (sync_time)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''学校课程基本信息表''',
    'SELECT 1');
PREPARE stmt FROM @create_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ═══════════════════════════════════════════════════════════════
-- Part 1.2: 创建 student_schedule 表 (原 V14 Part 1)
-- 存储学生个人课表数据 (/api/xskb)
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS student_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100),
    class_id VARCHAR(50) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50),
    teacher_name VARCHAR(100),
    day_of_week TINYINT NOT NULL,
    period VARCHAR(20) NOT NULL,
    week_range VARCHAR(50),
    class_location VARCHAR(200),
    academic_year VARCHAR(20) NOT NULL,
    semester TINYINT NOT NULL,
    sync_time DATETIME NOT NULL,
    sync_source VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_student (student_id),
    INDEX idx_class_id (class_id),
    INDEX idx_course (course_code),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Student schedule info';

-- ═══════════════════════════════════════════════════════════════
-- Part 2.1: 数据源配置种子 (原 V13)
-- NOTE: api_app_id / api_app_secret 在 Part 2.3 添加（V20）
-- ═══════════════════════════════════════════════════════════════
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'source', 'api', '数据源类型: api 或 etl', 0),
('school_data', 'api_base_url', 'https://api.imau.edu.cn', 'API基础URL', 0),
('school_data', 'api_connect_timeout', '5000', 'API连接超时(ms)', 0),
('school_data', 'api_read_timeout', '30000', 'API读取超时(ms)', 0),
('school_data', 'api_retry_attempts', '3', 'API重试次数', 0),
('school_data', 'etl_jdbc_url', '', 'ETL JDBC连接URL', 0),
('school_data', 'etl_username', '', 'ETL数据库用户名', 0),
('school_data', 'etl_password', '', 'ETL数据库密码', 1),
('school_data', 'etl_connection_timeout', '30000', 'ETL连接超时(ms)', 0),
('school_data', 'etl_query_timeout', '60000', 'ETL查询超时(ms)', 0);

-- 同步调度配置
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'sync_enabled', 'false', '是否启用同步调度', 0),
('school_data', 'sync_full_cron', '0 0 2 * * ?', '全量同步cron', 0),
('school_data', 'sync_incremental_cron', '0 0 * * * ?', '增量同步cron', 0),
('school_data', 'sync_validation_cron', '0 0 4 * * ?', '数据校验cron', 0),
('school_data', 'sync_batch_size', '500', '批量大小', 0),
('school_data', 'sync_timeout_minutes', '60', '同步超时(分钟)', 0);

-- ═══════════════════════════════════════════════════════════════
-- Part 2.2: 功能开关 seed (原 V18)
-- ═══════════════════════════════════════════════════════════════
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_browser', 'false', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
('feature_toggle', 'ops.school_data_config', 'false', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0),
('feature_toggle', 'page_schedule', 'false', '学生端课表查看功能（从教务系统同步课表数据）', 0);

-- ═══════════════════════════════════════════════════════════════
-- Part 2.3: API 认证 appId + appSecret 签名 (原 V20)
-- 官方接口文档: sign = Base64( MD5( appId + appSecret + timestamp ) )
-- 请求头: appId / timestamp / sign
-- ═══════════════════════════════════════════════════════════════
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'api_app_id', '', 'API 应用 ID（由学校数据平台分配，用于签名认证）', 1),
('school_data', 'api_app_secret', '', 'API 应用 Secret（由学校数据平台分配，与 appId + timestamp 组合生成签名）', 1);

-- 清理旧部署遗留的 api_token（Bearer Token 认证已废弃；全新部署不会有此记录）
DELETE FROM `system_config`
WHERE `config_group` = 'school_data' AND `config_key` = 'api_token';

-- ═══════════════════════════════════════════════════════════════
-- Part 3: field_mappings 按 CDSP 官方 PDF 对齐 (原 V20-align)
--
-- 基于 9 份 cdsp.imau.edu.cn data-api/v2 官方接口文档原文复核（2026-04-21）：
--   行政班基本信息 / 课程基本信息 / 本专科生课程信息 / 课程安排信息 /
--   学生选课 / 本专科生等级考试成绩 / 教学计划信息 / 教师授课 / 学生课表
--
-- 配套代码:
--   - FieldMappingServiceImpl.applyMapping 大小写不敏感查表
--     (PDF 返回字段 3 个端点 UPPERCASE + 6 个 lowercase)
--   - SchoolDataSyncServiceImpl.upsertEntity: teaching_class 数据源
--     路由到 upsertSchoolCourse (bzkcxx 端点实际返回课程信息，非教学班)
--   - target_field 统一 camelCase, 匹配 Java 实体属性
--
-- 已知限制（非本 part 职责）:
--   - course_selection PDF 仅返回 XH/KSXZDM/KSXZMC/KCBH 4 个字段，
--     远少于 CourseSelection 实体 NOT NULL 列数，upsert 可能失败。
--   - teaching_plans 返回的是教学计划中的课程条目，部分实体必填
--     字段 (planName/planStatus) PDF 不提供。
--   - 上述需通过 DDL 放宽或默认值策略解决，本 part 仅确保字段映射本身正确。
-- ═══════════════════════════════════════════════════════════════

-- Step 3.0: 清空 V12 历史遗留的错误 mappings（幂等）
DELETE FROM field_mappings WHERE data_source IN (
    'administrative_class', 'school_course', 'teaching_class',
    'course_schedule', 'course_selection', 'level_exam_scores',
    'teaching_plans', 'teacher_courses', 'student_schedule'
);

-- 1. administrative_class (cdsp_1710949911983374368 / 行政班基本信息)
-- PDF fields (UPPERCASE): XZBH, BJMC, XSLB, SSXYBM, SSXYMC, SSNJ, BZRGH, BZRXM, ZY, JBNY
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'XZBH',         'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号（英文缩写）'),
('administrative_class', '行政班编号',   'class_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '行政班编号（中文）'),
('administrative_class', 'BJMC',         'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称（英文缩写）'),
('administrative_class', '班级名称',     'class_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '班级名称（中文）'),
('administrative_class', 'XSLB',         'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别（英文缩写）'),
('administrative_class', '学生类别',     'class_type',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '学生类别（中文）'),
('administrative_class', 'SSXYBM',       'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码（英文缩写）'),
('administrative_class', '所属学院编码', 'college_code',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4,  1, '所属学院编码（中文）'),
('administrative_class', 'SSXYMC',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称（英文缩写）'),
('administrative_class', '所属学院名称', 'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '所属学院名称（中文）'),
('administrative_class', 'SSNJ',         'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级（英文缩写）'),
('administrative_class', '所属年级',     'grade',             'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '所属年级（中文）'),
('administrative_class', 'BZRGH',        'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号（英文缩写）'),
('administrative_class', '班主任工号',   'head_teacher_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '班主任工号（中文）'),
('administrative_class', 'BZRXM',        'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名（英文缩写）'),
('administrative_class', '班主任姓名',   'head_teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '班主任姓名（中文）'),
('administrative_class', 'ZY',           'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称（英文缩写）'),
('administrative_class', '专业名称',     'major_name',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '专业名称（中文）'),
('administrative_class', 'JBNY',         'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月（英文缩写）'),
('administrative_class', '建班年月',     'establish_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 10, 1, '建班年月（中文）');

-- 2. school_course (cdsp_1715290945064353805 / 课程基本信息)
-- PDF fields (lowercase): kcxs, kcdm, kcxf, kcywmc, kcjj, kslx, kkyx, kcmc
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcdm',       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码（英文缩写）'),
('school_course', '课程代码',   'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程代码（中文）'),
('school_course', 'kcmc',       'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（英文缩写）'),
('school_course', '课程名称',   'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('school_course', 'kcywmc',     'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（英文缩写）'),
('school_course', '课程英文名称', 'course_name_en',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('school_course', 'kcxf',       'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（英文缩写）'),
('school_course', '课程学分',   'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('school_course', 'kcxs',       'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（英文缩写）'),
('school_course', '课程学时',   'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('school_course', 'kcjj',       'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（英文缩写）'),
('school_course', '课程简介',   'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('school_course', 'kslx',       'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（英文缩写）'),
('school_course', '考试类型',   'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('school_course', 'kkyx',       'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（英文缩写）'),
('school_course', '开课院系',   'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- 3. teaching_class (cdsp_1721804030860152850 / 本专科生课程信息)
-- 此端点实际返回课程信息（非教学班），代码已路由到 upsertSchoolCourse
-- PDF fields (UPPERCASE): KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
-- 注意：CDSP API 可能返回中文字段名，需同时支持英文缩写和中文字段名
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'KCH',          'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（英文缩写）'),
('teaching_class', '课程号',       'course_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程号（中文）'),
('teaching_class', 'KCMC',         'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（英文缩写）'),
('teaching_class', '课程名称',     'course_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称（中文）'),
('teaching_class', 'KCYWMC',       'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（英文缩写）'),
('teaching_class', '课程英文名称', 'course_name_en',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称（中文）'),
('teaching_class', 'KCXF',         'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（英文缩写）'),
('teaching_class', '课程学分',     'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4, 1, '课程学分（中文）'),
('teaching_class', 'KCXS',         'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（英文缩写）'),
('teaching_class', '课程学时',     'total_hours',       'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '课程学时（中文）'),
('teaching_class', 'KCJJ',         'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（英文缩写）'),
('teaching_class', '课程简介',     'course_intro',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '课程简介（中文）'),
('teaching_class', 'KSLX',         'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（英文缩写）'),
('teaching_class', '考试类型',     'assessment_method', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '考试类型（中文）'),
('teaching_class', 'KKYX',         'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（英文缩写）'),
('teaching_class', '开课院系',     'college_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '开课院系（中文）');

-- 4. course_schedule (cdsp_1715290377801515044 / 课程安排信息)
-- PDF fields (lowercase): xkkch, xn, xq, skxqj, skjc, kczc, skdd
-- xq 返回中文 "秋"/"春" varchar，target_type=STRING，service 层做中文→Integer 转换
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'xkkch',        'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（作 class_id）'),
('course_schedule', '选课课号',     'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（中文字段名）'),
('course_schedule', 'skxqj',        'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7（英文缩写）'),
('course_schedule', '上课星期几',   'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7（中文字段名）'),
('course_schedule', 'skjc',         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次（英文缩写）'),
('course_schedule', '上课节次',     'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次（中文字段名）'),
('course_schedule', 'kczc',         'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次（描述性，英文缩写）'),
('course_schedule', '课程周次',     'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程周次（描述性，中文字段名）'),
('course_schedule', 'skdd',         'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点（英文缩写）'),
('course_schedule', '上课地点',     'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '上课地点（中文字段名）'),
('course_schedule', 'xn',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年如 "2024"，英文缩写）'),
('course_schedule', '学年',         'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（中文字段名）'),
('course_schedule', 'xq',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期中文 "秋"/"春"，service 层转 Integer（英文缩写）'),
('course_schedule', '学期',         'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期（中文字段名）');

-- 5. course_selection (cdsp_1715294802871926794 / 学生选课)
-- PDF fields (UPPERCASE): XH, KSXZDM, KSXZMC, KCBH
-- 注意：PDF 只返回 4 个字段，远少于 CourseSelection 实体 NOT NULL 列数。
-- KCBH 在链式拉取与历史兼容中按”课程班号”使用，应统一落到 classId。
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'XH',     'student_id',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'KCBH',   'class_id',          'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程班号（作 class_id）'),
('course_selection', 'KSXZDM', 'selection_status',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '考试性质代码（作选课状态）'),
('course_selection', 'KSXZMC', 'class_time',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '考试性质名称（暂存 classTime）');

-- 6. level_exam_scores (DS0076 / 本专科生等级考试成绩)
-- PDF fields (lowercase): xh, xhxm, xm, kskm, ksrq, fskscj
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh',     'student_id',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm',     'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '姓名'),
('level_exam_scores', 'kskm',   'exam_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试科目（作考试名称）'),
('level_exam_scores', 'ksrq',   'exam_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '考试日期'),
('level_exam_scores', 'fskscj', 'score',        'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '考试成绩（分数）');

-- 7. teaching_plans (cdsp_1715289655026466836 / 教学计划信息)
-- PDF fields (lowercase): zxs, skjszgh, kkdwdm, kkxq, cksm, ksfsdm, kkjhid, kkxn,
--   kcdm, kkxq_name, kkdwdm_name, ksfsmc
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'kkjhid',      'plan_id',            'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '开课计划 ID（plan_id 主键）'),
('teaching_plans', 'kcdm',        'course_code',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2,  1, '课程代码'),
('teaching_plans', 'cksm',        'training_objective', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '参考书目（作培养目标）'),
('teaching_plans', 'zxs',         'total_credits',      'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 4,  1, '总学时（近似总学分）'),
('teaching_plans', 'skjszgh',     'creator',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '授课教师职工号（作创建者）'),
('teaching_plans', 'kkdwdm',      'college_code',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '开课单位代码'),
('teaching_plans', 'kkdwdm_name', 'college_name',       'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7,  1, '开课单位名称'),
('teaching_plans', 'kkxn',        'grade',              'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8,  1, '开课学年（作年级）'),
('teaching_plans', 'kkxq_name',   'plan_name',          'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '开课学期名称（作计划名称）'),
('teaching_plans', 'ksfsdm',      'plan_status',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '考试方式代码（作计划状态）'),
('teaching_plans', 'ksfsmc',      'version',            'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '考试方式名称（作版本）');

-- 8. teacher_courses (cdsp_1715294645677801497 / 教师授课)
-- PDF fields (lowercase): rkjs, xn, xq, kcdm, kxh, rkjsh, kcmc, kcbh, sfzj, kcbbm, rkjsxm
-- 教师授课数据 upsert 到 TeachingClass 实体（service 代码既有逻辑）
-- 语义（按 PDF 返回示例核对 2026-04-21）:
--   kcdm = 课程代码（如 "594625"，课程本身编码）→ courseCode
--   kcbh = 课程班号（如 "51425977"，某次开班的全局唯一 ID）→ classId (TeachingClass 主键)
--   kxh  = 课序号（班内序号，通常与 kcdm 相同）→ 不入库
--   xq   = 学期，API 返回中文 "秋"/"春" varchar → target_type=STRING 先保留原值，
--          SchoolDataSyncServiceImpl.upsertTeachingClass 读取时再做中文→Integer 转换
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'kcbh',   'class_id',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程班号（作 class_id，全局唯一开班 ID）'),
('teacher_courses', 'kcdm',   'course_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程代码'),
('teacher_courses', 'kcmc',   'course_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('teacher_courses', 'rkjsh',  'teacher_id',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '任课教师工号'),
('teacher_courses', 'rkjsxm', 'teacher_name',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '任课教师姓名'),
('teacher_courses', 'xn',     'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年（CDSP 单年格式如 "2024"）'),
('teacher_courses', 'xq',     'semester',      'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学期 varchar 中文 "秋"/"春"，service 层转 Integer'),
('teacher_courses', 'kcbbm',  'class_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '课程班别名（作班级展示名）');

-- 9. student_schedule (cdsp_1715294397274341377 / 学生课表)
-- PDF fields (lowercase): zcdm, skjszgh, jsmc, lsxykh, kcmc, js, skcd, djj, xqj,
--   xkkh, xh, xq, xn, zcap, kcbh
-- xq 返回中文 "秋"/"春" varchar，target_type=STRING，service 层做中文→Integer 转换
-- 注意：CDSP API 实际返回中文字段名（非英文缩写），需同时支持两种格式
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', '学号',           'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（中文字段名）'),
('student_schedule', 'xh',            'student_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1,  1, '学号（英文缩写）'),
('student_schedule', '选课课号',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，中文字段名）'),
('student_schedule', 'xkkh',          'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '选课课号（作 class_id，英文缩写）'),
('student_schedule', '课程班号',       'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2,  1, '课程班号（作 class_id，中文字段名）'),
('student_schedule', '课程编号',       'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（中文字段名，非必填）'),
('student_schedule', 'kcbh',          'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3,  1, '课程编号（英文缩写，非必填）'),
('student_schedule', '课程名称',       'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（中文字段名）'),
('student_schedule', 'kcmc',          'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4,  1, '课程名称（英文缩写）'),
('student_schedule', '上课教师职工号', 'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（中文字段名）'),
('student_schedule', 'skjszgh',       'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5,  1, '上课教师职工号（英文缩写）'),
('student_schedule', '教师名称',       'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（中文字段名）'),
('student_schedule', 'jsmc',          'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6,  1, '教师名称（英文缩写）'),
('student_schedule', '星期几',         'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（中文字段名）'),
('student_schedule', 'xqj',           'day_of_week',    'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7,  1, '星期几 1-7（英文缩写）'),
('student_schedule', '第几节',         'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（中文字段名）'),
('student_schedule', 'djj',           'period',         'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8,  1, '第几节（英文缩写）'),
('student_schedule', '周次安排',       'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，中文字段名）'),
('student_schedule', 'zcap',          'week_range',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9,  1, '周次安排（如 "1-9"，英文缩写）'),
('student_schedule', '教室',           'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（中文字段名）'),
('student_schedule', 'js',            'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教室（英文缩写）'),
('student_schedule', '学年',           'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，中文字段名）'),
('student_schedule', 'xn',            'academic_year',  'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '学年（CDSP 单年如 "2024"，英文缩写）'),
('student_schedule', '学期',           'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（中文字段名）'),
('student_schedule', 'xq',            'semester',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学期中文 "秋"/"春"，service 层转 Integer（英文缩写）');

-- ═══════════════════════════════════════════════════════════════
-- Part 4: 视频管理表
-- ═══════════════════════════════════════════════════════════════


-- ═══════════════════════════════════════════════════════════════
-- Part 4: 视频管理表
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS `videos` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(200) NOT NULL COMMENT '视频标题',
    `description` TEXT COMMENT '视频简介',
    `media_id` BIGINT DEFAULT NULL COMMENT '关联的媒体记录ID',
    `cover_media_id` BIGINT DEFAULT NULL COMMENT '封面媒体ID',
    `video_url` VARCHAR(500) DEFAULT NULL COMMENT '视频URL（外部链接时使用）',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面URL',
    `subtitle_content` LONGTEXT COMMENT '字幕内容（SRT/VTT格式）',
    `user_id` BIGINT DEFAULT NULL COMMENT '上传者用户ID',
    `is_public` TINYINT DEFAULT 1 COMMENT '是否公开 0-私有 1-公开',
    `is_featured` TINYINT(1) DEFAULT 0 COMMENT '是否推荐',
    `featured_priority` INT DEFAULT 0 COMMENT '推荐优先级',
    `tags` VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    `view_count` INT DEFAULT 0 COMMENT '浏览次数',
    `like_count` INT DEFAULT 0 COMMENT '收藏/点赞次数',
    `status` VARCHAR(20) DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT/PUBLISHED/ARCHIVED',
    `associated_text_id` BIGINT DEFAULT NULL COMMENT '关联的课文ID（用于收藏映射）',
    `transcription_status` VARCHAR(20) DEFAULT NULL COMMENT '转录状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_media_id` (`media_id`),
    KEY `idx_is_public` (`is_public`),
    KEY `idx_is_featured` (`is_featured`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_associated_text_id` (`associated_text_id`),
    KEY `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='视频表';
