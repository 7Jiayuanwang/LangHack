-- ═══════════════════════════════════════════════════════════════
-- V12: 学校数据集成整合（补丁合并）
-- 整合自: V12.1 (字段映射 camelCase) + V13 (system_config seed)
--       + V14 (student_schedule 表 + 字段映射) + V16 (course→school_course 重命名)
--       + V18 (功能开关 seed) + V19 (字段映射 snake_case)
--       + V20 (API 认证 appId+appSecret)
--
-- 注意: Flyway 在本项目禁用 (application.yml flyway.enabled=false)，
--       实际生产部署走 docker/mysql/init/04-apply-all-migrations.sql
--
-- 执行顺序（重要）:
--   Part 1: Schema 变更 (V16 rename + V14 create)
--   Part 2: system_config seed (V13 + V18 + V20)
--   Part 3: field_mappings data (V12.1 + V14 + V19)
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
-- 接口: /api/xskb
-- ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS student_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    class_id VARCHAR(50) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50) NOT NULL,
    teacher_name VARCHAR(50) NOT NULL,
    day_of_week INT NOT NULL,
    period VARCHAR(20) NOT NULL,
    week_range VARCHAR(50) NOT NULL,
    class_location VARCHAR(100) NOT NULL,
    academic_year VARCHAR(20) NOT NULL,
    semester INT NOT NULL,
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
-- Part 3.1: 字段映射 camelCase target（用于 JSON → Java 实体）(原 V12.1)
-- 为7个数据源配置字段映射，target_field 匹配 Java 实体 camelCase 属性
-- ═══════════════════════════════════════════════════════════════

-- 行政班 (administrative_class) — /api/xzxx
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'xzbh', 'classCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '行政班编号'),
('administrative_class', 'xzbmc', 'className', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '行政班名称'),
('administrative_class', 'xzbjc', 'classShortName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '行政班简称'),
('administrative_class', 'yxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '学院编号'),
('administrative_class', 'yxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学院名称'),
('administrative_class', 'zybh', 'majorCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '专业编号'),
('administrative_class', 'zymc', 'majorName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '专业名称'),
('administrative_class', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '年级'),
('administrative_class', 'bjlx', 'classType', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '班级类型'),
('administrative_class', 'bzrgh', 'headTeacherCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '班主任工号'),
('administrative_class', 'bzrxm', 'headTeacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '班主任姓名'),
('administrative_class', 'xsrs', 'studentCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 12, 1, '学生人数'),
('administrative_class', 'nsrs', 'maleCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 13, 1, '男生人数'),
('administrative_class', 'nsrs_nv', 'femaleCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 14, 1, '女生人数'),
('administrative_class', 'bjzt', 'classStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '班级状态'),
('administrative_class', 'clrq', 'establishDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 16, 1, '成立日期'),
('administrative_class', 'byrq', 'graduateDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 0, 17, 1, '毕业日期'),
('administrative_class', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '备注');

-- 学校课程 (school_course) — /api/kcxx
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('school_course', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '课程编号'),
('school_course', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程名称'),
('school_course', 'kcywmc', 'courseNameEn', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '课程英文名称'),
('school_course', 'kcjc', 'courseShortName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '课程简称'),
('school_course', 'kcxz', 'courseNature', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程性质'),
('school_course', 'kclb', 'courseCategory', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '课程类别'),
('school_course', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('school_course', 'zxs', 'totalHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '总学时'),
('school_course', 'llxs', 'theoryHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 9, 1, '理论学时'),
('school_course', 'syxs', 'experimentHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 10, 1, '实验学时'),
('school_course', 'sjxs', 'practiceHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 11, 1, '实践学时'),
('school_course', 'sjxs_pc', 'computerHours', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 12, 1, '上机学时'),
('school_course', 'kkyxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 13, 1, '开课学院编号'),
('school_course', 'kkyxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 14, 1, '开课学院名称'),
('school_course', 'kkxbbh', 'departmentCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 15, 1, '开课系部编号'),
('school_course', 'kkxbmc', 'departmentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 16, 1, '开课系部名称'),
('school_course', 'kcfzrgh', 'leaderCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 17, 1, '课程负责人工号'),
('school_course', 'kcfzrxm', 'leaderName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 18, 1, '课程负责人姓名'),
('school_course', 'khfs', 'assessmentMethod', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 19, 1, '考核方式'),
('school_course', 'sfsyjx', 'isBilingual', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 20, 1, '是否双语教学'),
('school_course', 'sfwlkc', 'isOnline', 'STRING', 'STRING', 'BOOLEAN', '{"trueValues":["是","1","true"],"falseValues":["否","0","false"]}', NULL, 0, 21, 1, '是否网络课程'),
('school_course', 'syzy', 'applicableMajors', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 22, 1, '适用专业'),
('school_course', 'xxkcbh', 'prerequisiteCodes', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 23, 1, '先修课程编号'),
('school_course', 'xxkcmc', 'prerequisiteNames', 'STRING', 'STRING', 'SPLIT', '{"delimiter":","}', NULL, 0, 24, 1, '先修课程名称'),
('school_course', 'kcjj', 'courseIntro', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 25, 1, '课程简介'),
('school_course', 'jxmb', 'teachingObjectives', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 26, 1, '教学目标'),
('school_course', 'kczt', 'courseStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 27, 1, '课程状态');

-- 教学班 (teaching_class) — /api/bzkcxx
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_class', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('teaching_class', 'jxbmc', 'className', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '教学班名称'),
('teaching_class', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程编号'),
('teaching_class', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程名称'),
('teaching_class', 'jgh', 'teacherId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '授课教师工号'),
('teaching_class', 'xm', 'teacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '授课教师姓名'),
('teaching_class', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学年'),
('teaching_class', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '学期'),
('teaching_class', 'bjrl', 'capacity', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 9, 1, '班级容量'),
('teaching_class', 'yxrs', 'enrolledCount', 'NUMBER', 'NUMBER', 'NONE', NULL, '0', 0, 10, 1, '已选人数'),
('teaching_class', 'kczt', 'classStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '课程状态');

-- 课程安排 (course_schedule) — /api/kcap
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教学班号'),
('course_schedule', 'skxqj', 'dayOfWeek', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 2, 1, '上课星期几 1-7'),
('course_schedule', 'skjc', 'period', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '上课节次'),
('course_schedule', 'zcd', 'weekRange', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '周次'),
('course_schedule', 'cdmc', 'classLocation', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '上课地点'),
('course_schedule', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年'),
('course_schedule', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学期');

-- 选课记录 (course_selection) — /api/xsxk
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'xh', 'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'kcbh', 'courseCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程编号'),
('course_selection', 'kcmc', 'courseName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '课程名称'),
('course_selection', 'jhbh', 'planId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '计划编号'),
('course_selection', 'xn', 'academicYear', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '学年'),
('course_selection', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学期'),
('course_selection', 'xf', 'credits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学分'),
('course_selection', 'cjj', 'totalGrade', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 8, 1, '成绩'),
('course_selection', 'kcxz', 'courseNature', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '课程性质'),
('course_selection', 'jxbid', 'classId', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 10, 1, '教学班号'),
('course_selection', 'jgh', 'teacherId', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 11, 1, '教师工号'),
('course_selection', 'xm', 'teacherName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '教师姓名');

-- 等级考试成绩 (level_exam_scores) — /api/djcj
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh', 'studentId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm', 'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '姓名'),
('level_exam_scores', 'ksbh', 'examId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试编号'),
('level_exam_scores', 'ksmc', 'examName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '考试名称'),
('level_exam_scores', 'ksrq', 'examDate', 'STRING', 'DATE', 'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 5, 1, '考试日期'),
('level_exam_scores', 'cj', 'score', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '成绩'),
('level_exam_scores', 'dj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 7, 1, '等级'),
('level_exam_scores', 'zkzh', 'admissionTicketNo', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 8, 1, '准考证号'),
('level_exam_scores', 'bz', 'remark', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '备注');

-- 教学计划 (teaching_plans) — /api/jxjh
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teaching_plans', 'jhbh', 'planId', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '计划编号'),
('teaching_plans', 'jhmc', 'planName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '计划名称'),
('teaching_plans', 'zybh', 'majorCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '专业代码'),
('teaching_plans', 'zymc', 'majorName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '专业名称'),
('teaching_plans', 'nj', 'grade', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '年级'),
('teaching_plans', 'xz', 'schoolingLength', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 6, 1, '学制'),
('teaching_plans', 'xwlx', 'degreeType', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '学位类型'),
('teaching_plans', 'pycc', 'educationLevel', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 8, 1, '培养层次'),
('teaching_plans', 'yxbh', 'collegeCode', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 9, 1, '学院代码'),
('teaching_plans', 'yxmc', 'collegeName', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 10, 1, '学院名称'),
('teaching_plans', 'zxf', 'totalCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 11, 1, '总学分'),
('teaching_plans', 'bxxf', 'requiredCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 12, 1, '必修学分'),
('teaching_plans', 'xxxf', 'electiveCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 13, 1, '选修学分'),
('teaching_plans', 'sjxf', 'practiceCredits', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 14, 1, '实践学分'),
('teaching_plans', 'jhzt', 'planStatus', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 15, 1, '计划状态');

-- ═══════════════════════════════════════════════════════════════
-- Part 3.2: 教师授课 + 学生课表字段映射 (原 V14 Part 2&3)
-- ═══════════════════════════════════════════════════════════════

-- 教师授课 (teacher_courses) — /api/jssk
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'jgh', 'teacher_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教师工号'),
('teacher_courses', 'xm', 'teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '教师姓名'),
('teacher_courses', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '教学班号'),
('teacher_courses', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程编号'),
('teacher_courses', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程名称'),
('teacher_courses', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年'),
('teacher_courses', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学期');

-- 学生课表 (student_schedule) — /api/xskb
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('student_schedule', 'xh', 'student_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('student_schedule', 'xm', 'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '学生姓名'),
('student_schedule', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '教学班号'),
('student_schedule', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程编号'),
('student_schedule', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程名称'),
('student_schedule', 'jgh', 'teacher_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '教师工号'),
('student_schedule', 'jsxm', 'teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 7, 1, '教师姓名'),
('student_schedule', 'skxqj', 'day_of_week', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 8, 1, '上课星期几 1-7'),
('student_schedule', 'skjc', 'period', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 9, 1, '上课节次'),
('student_schedule', 'zcd', 'week_range', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 10, 1, '周次'),
('student_schedule', 'cdmc', 'class_location', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 11, 1, '上课地点'),
('student_schedule', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 12, 1, '学年'),
('student_schedule', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 13, 1, '学期');

-- ═══════════════════════════════════════════════════════════════
-- Part 3.3: 字段映射 snake_case target（DB 列直接映射）(原 V19)
-- NOTE: 此部分与 Part 3.1 并存 — Part 3.1 target=camelCase（Java 实体），
--       Part 3.3 target=snake_case（DB 列）。两套映射用于不同转换路径。
-- FIX (2026-04-19): 原 V19 init SQL 使用了错误列名 converter_type/description/created_by，
--                   导致 INSERT 被 IGNORE 吞掉；此版本使用正确列名 converter/remark
-- ═══════════════════════════════════════════════════════════════

-- 学生课表 snake_case 补充映射（/api/xskb）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('student_schedule', 'zcdm', 'week_code', 'NONE', NULL, '周次代码'),
('student_schedule', 'skjszgh', 'teacher_id', 'NONE', NULL, '上课教师职工号'),
('student_schedule', 'jsmc', 'teacher_name', 'NONE', NULL, '教师名称'),
('student_schedule', 'lsxykh', 'teacher_school_id', 'NONE', NULL, '老师校园卡号'),
('student_schedule', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('student_schedule', 'js', 'classroom', 'NONE', NULL, '教室'),
('student_schedule', 'skcd', 'duration', 'NONE', NULL, '上课长度（节数）'),
('student_schedule', 'djj', 'period', 'NONE', NULL, '第几节'),
('student_schedule', 'xqj', 'day_of_week', 'NONE', NULL, '星期几（1-7）'),
('student_schedule', 'xkkh', 'course_selection_code', 'NONE', NULL, '选课课号'),
('student_schedule', 'xh', 'student_id', 'NONE', NULL, '学号'),
('student_schedule', 'xq', 'semester', 'NONE', NULL, '学期（1/2）'),
('student_schedule', 'xn', 'academic_year', 'NONE', NULL, '学年（如2024）'),
('student_schedule', 'zcap', 'week_schedule', 'NONE', NULL, '周次安排（如"1-9"）'),
('student_schedule', 'kcbh', 'class_code', 'NONE', NULL, '课程班号');

-- 行政班 snake_case（/api/xzxx）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('administrative_class', 'xzbh', 'class_code', 'NONE', NULL, '行政班编号'),
('administrative_class', 'xzmc', 'class_name', 'NONE', NULL, '行政班名称'),
('administrative_class', 'xzbjc', 'class_short_name', 'NONE', NULL, '行政班简称'),
('administrative_class', 'yxbh', 'college_code', 'NONE', NULL, '学院编号'),
('administrative_class', 'yxmc', 'college_name', 'NONE', NULL, '学院名称'),
('administrative_class', 'zybh', 'major_code', 'NONE', NULL, '专业编号'),
('administrative_class', 'zymc', 'major_name', 'NONE', NULL, '专业名称'),
('administrative_class', 'nj', 'grade', 'NONE', NULL, '年级'),
('administrative_class', 'bjlx', 'class_type', 'NONE', NULL, '班级类型'),
('administrative_class', 'bzrgh', 'head_teacher_code', 'NONE', NULL, '班主任工号'),
('administrative_class', 'bzrxm', 'head_teacher_name', 'NONE', NULL, '班主任姓名'),
('administrative_class', 'xsrs', 'student_count', 'NONE', NULL, '学生人数'),
('administrative_class', 'nsrs', 'male_count', 'NONE', NULL, '男生人数'),
('administrative_class', 'nsrs_nv', 'female_count', 'NONE', NULL, '女生人数'),
('administrative_class', 'bjzt', 'class_status', 'NONE', NULL, '班级状态'),
('administrative_class', 'clrq', 'establish_date', 'DATE', NULL, '成立日期'),
('administrative_class', 'byrq', 'graduate_date', 'DATE', NULL, '毕业日期'),
('administrative_class', 'bz', 'remark', 'NONE', NULL, '备注');

-- 学校课程 snake_case（/api/kcxx）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('school_course', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('school_course', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('school_course', 'kcywmc', 'course_name_en', 'NONE', NULL, '课程英文名称'),
('school_course', 'kcjc', 'course_short_name', 'NONE', NULL, '课程简称'),
('school_course', 'kcxz', 'course_nature', 'NONE', NULL, '课程性质'),
('school_course', 'kclb', 'course_category', 'NONE', NULL, '课程类别'),
('school_course', 'xf', 'credits', 'NONE', NULL, '学分'),
('school_course', 'zxs', 'total_hours', 'NONE', NULL, '总学时'),
('school_course', 'llxs', 'theory_hours', 'NONE', NULL, '理论学时'),
('school_course', 'syxs', 'experiment_hours', 'NONE', NULL, '实验学时'),
('school_course', 'sjxs', 'practice_hours', 'NONE', NULL, '实践学时'),
('school_course', 'sjxs_pc', 'computer_hours', 'NONE', NULL, '上机学时'),
('school_course', 'kkyxbh', 'college_code', 'NONE', NULL, '开课学院编号'),
('school_course', 'kkyxmc', 'college_name', 'NONE', NULL, '开课学院名称'),
('school_course', 'kkxbbh', 'department_code', 'NONE', NULL, '开课系部编号'),
('school_course', 'kkxbmc', 'department_name', 'NONE', NULL, '开课系部名称'),
('school_course', 'kcfzrgh', 'leader_code', 'NONE', NULL, '课程负责人工号'),
('school_course', 'kcfzrxm', 'leader_name', 'NONE', NULL, '课程负责人姓名'),
('school_course', 'khfs', 'assessment_method', 'NONE', NULL, '考核方式'),
('school_course', 'sfsyjx', 'is_bilingual', 'BOOLEAN', NULL, '是否双语教学'),
('school_course', 'sfwlkc', 'is_online', 'BOOLEAN', NULL, '是否网络课程'),
('school_course', 'syzy', 'applicable_majors', 'NONE', NULL, '适用专业'),
('school_course', 'xxkcbh', 'prerequisite_codes', 'NONE', NULL, '先修课程编号'),
('school_course', 'xxkcmc', 'prerequisite_names', 'NONE', NULL, '先修课程名称'),
('school_course', 'kcjj', 'course_intro', 'NONE', NULL, '课程简介'),
('school_course', 'jxmb', 'teaching_objectives', 'NONE', NULL, '教学目标'),
('school_course', 'kczt', 'course_status', 'NONE', NULL, '课程状态');

-- 教学班 snake_case（/api/bzkcxx）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('teaching_class', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('teaching_class', 'jxbmc', 'class_name', 'NONE', NULL, '教学班名称'),
('teaching_class', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('teaching_class', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('teaching_class', 'jgh', 'teacher_id', 'NONE', NULL, '授课教师工号'),
('teaching_class', 'xm', 'teacher_name', 'NONE', NULL, '教师姓名'),
('teaching_class', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('teaching_class', 'xqm', 'semester', 'NONE', NULL, '学期'),
('teaching_class', 'bjrl', 'capacity', 'NONE', NULL, '班级容量'),
('teaching_class', 'yxrs', 'enrolled_count', 'NONE', NULL, '已选人数'),
('teaching_class', 'kczt', 'class_status', 'NONE', NULL, '课程状态');

-- 课程安排 snake_case（/api/kcap）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('course_schedule', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('course_schedule', 'xqj', 'day_of_week', 'NONE', NULL, '星期几'),
('course_schedule', 'jcs', 'period', 'NONE', NULL, '节次'),
('course_schedule', 'zcd', 'week_range', 'NONE', NULL, '周次'),
('course_schedule', 'cdmc', 'class_location', 'NONE', NULL, '上课地点'),
('course_schedule', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('course_schedule', 'xqm', 'semester', 'NONE', NULL, '学期');

-- 学生选课 snake_case（/api/xsxk）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('course_selection', 'xkjlid', 'selection_id', 'NONE', NULL, '选课记录ID'),
('course_selection', 'xh', 'student_id', 'NONE', NULL, '学号'),
('course_selection', 'xm', 'student_name', 'NONE', NULL, '学生姓名'),
('course_selection', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('course_selection', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('course_selection', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('course_selection', 'jgh', 'teacher_id', 'NONE', NULL, '授课教师工号'),
('course_selection', 'jsxm', 'teacher_name', 'NONE', NULL, '授课教师姓名'),
('course_selection', 'xf', 'credits', 'NONE', NULL, '学分'),
('course_selection', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('course_selection', 'xqm', 'semester', 'NONE', NULL, '学期'),
('course_selection', 'sksj', 'class_time', 'NONE', NULL, '上课时间'),
('course_selection', 'skdd', 'class_location', 'NONE', NULL, '上课地点'),
('course_selection', 'xksj', 'selection_time', 'DATE', NULL, '选课时间'),
('course_selection', 'xkzt', 'selection_status', 'NONE', NULL, '选课状态'),
('course_selection', 'pscj', 'regular_grade', 'NONE', NULL, '平时成绩'),
('course_selection', 'qmcj', 'final_grade', 'NONE', NULL, '期末成绩'),
('course_selection', 'zpcj', 'total_grade', 'NONE', NULL, '总评成绩'),
('course_selection', 'jd', 'grade_point', 'NONE', NULL, '绩点'),
('course_selection', 'cqcs', 'attendance_count', 'NONE', NULL, '出勤次数'),
('course_selection', 'qqcs', 'absence_count', 'NONE', NULL, '缺勤次数'),
('course_selection', 'cjbz', 'grade_remark', 'NONE', NULL, '成绩备注');

-- 等级考试成绩 snake_case（/api/djcj）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('level_exam_scores', 'xh', 'student_id', 'NONE', NULL, '学号'),
('level_exam_scores', 'xm', 'student_name', 'NONE', NULL, '学生姓名'),
('level_exam_scores', 'kslx', 'exam_type', 'NONE', NULL, '考试类型'),
('level_exam_scores', 'ksmc', 'exam_name', 'NONE', NULL, '考试名称'),
('level_exam_scores', 'zkzh', 'admission_ticket_no', 'NONE', NULL, '准考证号'),
('level_exam_scores', 'kssj', 'exam_date', 'DATE', NULL, '考试时间'),
('level_exam_scores', 'cj', 'score', 'NONE', NULL, '成绩'),
('level_exam_scores', 'dj', 'grade', 'NONE', NULL, '等级'),
('level_exam_scores', 'sftg', 'is_passed', 'BOOLEAN', NULL, '是否通过'),
('level_exam_scores', 'hgfsx', 'passing_score', 'NONE', NULL, '合格分数线'),
('level_exam_scores', 'tlcj', 'listening_score', 'NONE', NULL, '听力成绩'),
('level_exam_scores', 'ydcj', 'reading_score', 'NONE', NULL, '阅读成绩'),
('level_exam_scores', 'xzcj', 'writing_score', 'NONE', NULL, '写作成绩'),
('level_exam_scores', 'fycj', 'translation_score', 'NONE', NULL, '翻译成绩'),
('level_exam_scores', 'zsbh', 'certificate_no', 'NONE', NULL, '证书编号'),
('level_exam_scores', 'fzrq', 'certificate_date', 'DATE', NULL, '发证日期'),
('level_exam_scores', 'ksdd', 'exam_location', 'NONE', NULL, '考试地点'),
('level_exam_scores', 'kch', 'exam_room_no', 'NONE', NULL, '考场号'),
('level_exam_scores', 'zwh', 'seat_no', 'NONE', NULL, '座位号'),
('level_exam_scores', 'bmsj', 'registration_date', 'DATE', NULL, '报名时间'),
('level_exam_scores', 'cjfbsj', 'score_release_date', 'DATE', NULL, '成绩发布时间'),
('level_exam_scores', 'sjly', 'data_source', 'NONE', NULL, '数据来源'),
('level_exam_scores', 'bz', 'remark', 'NONE', NULL, '备注');

-- 教学计划 snake_case（/api/jxjh）
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('teaching_plans', 'jhbh', 'plan_id', 'NONE', NULL, '教学计划编号'),
('teaching_plans', 'jhmc', 'plan_name', 'NONE', NULL, '教学计划名称'),
('teaching_plans', 'zybh', 'major_code', 'NONE', NULL, '专业代码'),
('teaching_plans', 'zymc', 'major_name', 'NONE', NULL, '专业名称'),
('teaching_plans', 'nj', 'grade', 'NONE', NULL, '年级'),
('teaching_plans', 'xz', 'schooling_length', 'NONE', NULL, '学制（年）');

-- 教师授课 snake_case（/api/jssk）补充
INSERT IGNORE INTO `field_mappings` (`data_source`, `source_field`, `target_field`, `converter`, `converter_config`, `remark`) VALUES
('teacher_courses', 'jgh', 'teacher_id', 'NONE', NULL, '教师职工号'),
('teacher_courses', 'jsxm', 'teacher_name', 'NONE', NULL, '教师姓名'),
('teacher_courses', 'jxbid', 'class_id', 'NONE', NULL, '教学班号'),
('teacher_courses', 'kcbh', 'course_code', 'NONE', NULL, '课程编号'),
('teacher_courses', 'kcmc', 'course_name', 'NONE', NULL, '课程名称'),
('teacher_courses', 'xnm', 'academic_year', 'NONE', NULL, '学年'),
('teacher_courses', 'xqm', 'semester', 'NONE', NULL, '学期'),
('teacher_courses', 'skrs', 'student_count', 'NONE', NULL, '上课人数');
