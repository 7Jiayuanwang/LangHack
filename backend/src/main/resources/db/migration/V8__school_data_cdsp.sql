-- ═══════════════════════════════════════════════════════════════════════
-- V8: 学校数据 CDSP 集成
-- 合并自:
--   - V9  (school_data_sync)        表结构与 field_mappings 基础
--   - V12 (school_data_and_videos)  system_config seed + field_mappings PDF 对齐
--         (仅保留 school_* / teaching_plans / student_schedule 部分,
--          videos / media 部分归入 V2)
--   - V14 (enhancements)            school_student / school_teacher / teacher_student_binding
--         (仅保留 school data / binding 部分, similarity_matrix 归入其他迁移)
--   - V42 (link_users_to_school_directory)  users ↔ school_student / school_teacher 关联
--
-- 执行顺序 (依赖关系):
--   Part 1  : school_* / teaching_* / course_* / level_exam / sync_logs / field_mappings 表 (V9)
--   Part 2  : student_schedule 表 (V12 Part 1.1)
--   Part 3  : school_student / school_teacher / teacher_student_binding 表 (V14 Part 1-3)
--   Part 4  : users.school_student_id / school_teacher_id 列 + 索引 + 回填 + 视图 (V42)
--   Part 5  : system_config seed (V12 Part 2.1/2.2/2.3)
--   Part 6  : field_mappings PDF 对齐 seed (V12 Part 3)
--   Part 7  : school_* 表字段调整 + FK 重声明 (V12, 仅保留 school_* 相关)
--   Part 8  : feature_toggle seeds (V14 Part 4)
--   Part 9  : 字段映射补充 (V12 末尾行政班学院/专业代码补充)
--
-- 注意:
--   - Flyway 在本项目生产环境禁用 (flyway.enabled=false),
--     实际部署走 docker/mysql/init/04-apply-all-migrations.sql
--   - CI test profile 启用 Flyway
--   - ADD COLUMN 一律包在 IF NOT EXISTS 存储过程内 (与预处理脚本兼容, 无 DELIMITER)
-- ═══════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════
-- Part 1: 基础数据层 + 教学组织层 + 选课成绩层 + 辅助表 (原 V9)
-- ═══════════════════════════════════════════════════════════════════════

-- 1.1 行政班表
CREATE TABLE IF NOT EXISTS administrative_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_code VARCHAR(50) NOT NULL UNIQUE COMMENT '行政班编号 (XZBH)',
    class_name VARCHAR(100) NOT NULL COMMENT '行政班名称 (XZBMC)',
    class_short_name VARCHAR(50) COMMENT '行政班简称 (XZBJC)',
    college_code VARCHAR(50) NOT NULL COMMENT '学院编号 (YXBH)',
    college_name VARCHAR(100) NULL COMMENT '学院名称 (YXMC)',
    major_code VARCHAR(50) NOT NULL COMMENT '专业编号 (ZYBH)',
    major_name VARCHAR(100) NULL COMMENT '专业名称 (ZYMC)',
    grade VARCHAR(10) NOT NULL COMMENT '年级 (NJ)',
    class_type VARCHAR(20) COMMENT '班级类型 (BJLX)',
    head_teacher_code VARCHAR(50) COMMENT '班主任工号 (BZRGH)',
    head_teacher_name VARCHAR(50) COMMENT '班主任姓名 (BZRXM)',
    student_count INT DEFAULT 0 COMMENT '学生人数 (XSRS)',
    male_count INT DEFAULT 0 COMMENT '男生人数 (NSRS)',
    female_count INT DEFAULT 0 COMMENT '女生人数 (NSRS_NV)',
    class_status VARCHAR(20) NOT NULL COMMENT '班级状态 (BJZT)',
    establish_date DATE COMMENT '成立日期 (CLRQ)',
    graduate_date DATE COMMENT '毕业日期 (BYRQ)',
    remark VARCHAR(500) COMMENT '备注 (BZ)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源 (api/etl)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_college (college_code),
    INDEX idx_major (major_code),
    INDEX idx_grade (grade),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行政班基本信息表';

-- 1.2 学校课程表
CREATE TABLE IF NOT EXISTS school_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    course_code VARCHAR(50) NOT NULL UNIQUE COMMENT '课程编号 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称 (KCMC)',
    course_name_en VARCHAR(200) COMMENT '课程英文名称 (KCYWMC)',
    course_short_name VARCHAR(100) COMMENT '课程简称 (KCJC)',
    course_nature VARCHAR(50) NOT NULL COMMENT '课程性质 (KCXZ)',
    course_category VARCHAR(50) NOT NULL COMMENT '课程类别 (KCLB)',
    credits DECIMAL(3,1) NOT NULL COMMENT '学分 (XF)',
    total_hours INT NOT NULL COMMENT '总学时 (ZXS)',
    theory_hours INT COMMENT '理论学时 (LLXS)',
    experiment_hours INT COMMENT '实验学时 (SYXS)',
    practice_hours INT COMMENT '实践学时 (SJXS)',
    computer_hours INT COMMENT '上机学时 (SJXS_PC)',
    college_code VARCHAR(50) NOT NULL COMMENT '开课学院编号 (KKYXBH)',
    college_name VARCHAR(100) NOT NULL COMMENT '开课学院名称 (KKYXMC)',
    department_code VARCHAR(50) COMMENT '开课系部编号 (KKXBBH)',
    department_name VARCHAR(100) COMMENT '开课系部名称 (KKXBMC)',
    leader_code VARCHAR(50) COMMENT '课程负责人工号 (KCFZRGH)',
    leader_name VARCHAR(50) COMMENT '课程负责人姓名 (KCFZRXM)',
    assessment_method VARCHAR(50) NOT NULL COMMENT '考核方式 (KHFS)',
    is_bilingual VARCHAR(10) COMMENT '是否双语教学 (SFSYJX)',
    is_online VARCHAR(10) COMMENT '是否网络课程 (SFWLKC)',
    applicable_majors VARCHAR(500) COMMENT '适用专业 (SYZY)',
    prerequisite_codes VARCHAR(500) COMMENT '先修课程编号 (XXKCBH)',
    prerequisite_names VARCHAR(500) COMMENT '先修课程名称 (XXKCMC)',
    course_intro TEXT COMMENT '课程简介 (KCJJ)',
    teaching_objectives TEXT COMMENT '教学目标 (JXMB)',
    course_status VARCHAR(20) NOT NULL COMMENT '课程状态 (KCZT)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_college (college_code),
    INDEX idx_nature (course_nature),
    INDEX idx_category (course_category),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学校课程基本信息表';

-- 1.3 教学班表
CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_id VARCHAR(50) NOT NULL UNIQUE COMMENT '教学班号 (JXBID)',
    class_name VARCHAR(100) COMMENT '教学班名称 (JXBMC)',
    course_code VARCHAR(50) NOT NULL COMMENT '课程编号 (KCBH)',
    course_name VARCHAR(200) NOT NULL COMMENT '课程名称 (KCMC)',
    teacher_id VARCHAR(50) NOT NULL COMMENT '授课教师工号 (JGH)',
    teacher_name VARCHAR(50) NOT NULL COMMENT '授课教师姓名 (XM)',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年 (XNM)',
    semester INT NOT NULL COMMENT '学期 (XQM)',
    capacity INT NULL DEFAULT 0 COMMENT '班级容量',
    enrolled_count INT DEFAULT 0 COMMENT '已选人数',
    class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT '课程状态',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_course (course_code),
    INDEX idx_teacher (teacher_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班信息表';

-- 1.4 课程安排表
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    class_id VARCHAR(50) NULL COMMENT '教学班号 (JXBID)',
    day_of_week INT NULL COMMENT '星期几 (XQJ) 1-7',
    period VARCHAR(20) NULL COMMENT '节次 (JCS) 如"1-2"',
    week_range VARCHAR(50) NULL COMMENT '周次 (ZCD) 如"1-16周"',
    class_location VARCHAR(100) NULL COMMENT '上课地点 (CDMC)',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年 (XNM)',
    semester INT NOT NULL COMMENT '学期 (XQM)',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_class_id (class_id),
    INDEX idx_day_period (day_of_week, period),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_class_academic (class_id, academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程安排信息表';

-- 1.5 选课记录表
CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    selection_id VARCHAR(50) NOT NULL UNIQUE COMMENT '选课记录ID',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) NULL COMMENT '学生姓名',
    course_code VARCHAR(50) NOT NULL COMMENT '课程代码',
    course_name VARCHAR(200) COMMENT '课程名称（CDSP API不返回，可为空）',
    class_id VARCHAR(50) NOT NULL COMMENT '教学班号',
    teacher_id VARCHAR(50) NULL COMMENT '授课教师工号',
    teacher_name VARCHAR(50) NULL COMMENT '授课教师姓名',
    credits DECIMAL(3,1) COMMENT '学分（CDSP API不返回，可为空）',
    academic_year VARCHAR(20) NULL COMMENT '学年',
    semester INT NULL COMMENT '学期',
    class_time VARCHAR(100) COMMENT '上课时间',
    class_location VARCHAR(100) COMMENT '上课地点',
    selection_time DATETIME NULL COMMENT '选课时间',
    selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT '选课状态 SELECTED/DROPPED/COMPLETED',
    regular_grade DECIMAL(5,2) COMMENT '平时成绩',
    final_grade DECIMAL(5,2) COMMENT '期末成绩',
    total_grade DECIMAL(5,2) COMMENT '总评成绩',
    grade_point DECIMAL(3,2) COMMENT '绩点',
    attendance_count INT DEFAULT 0 COMMENT '出勤次数',
    absence_count INT DEFAULT 0 COMMENT '缺勤次数',
    grade_remark VARCHAR(500) COMMENT '成绩备注',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    FOREIGN KEY (course_code) REFERENCES school_course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_student (student_id),
    INDEX idx_course (course_code),
    INDEX idx_class (class_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_student_academic (student_id, academic_year, semester),
    INDEX idx_selection_status (selection_status),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课记录表';

-- 1.6 等级考试成绩表
CREATE TABLE IF NOT EXISTS level_exam_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）',
    exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）',
    exam_name VARCHAR(100) NOT NULL COMMENT '考试名称',
    admission_ticket_no VARCHAR(30) COMMENT '准考证号',
    exam_date DATE NOT NULL COMMENT '考试时间',
    score DECIMAL(5,2) NOT NULL COMMENT '成绩',
    grade VARCHAR(20) COMMENT '等级',
    is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）',
    passing_score DECIMAL(5,2) COMMENT '合格分数线',
    listening_score DECIMAL(5,2) COMMENT '听力成绩',
    reading_score DECIMAL(5,2) COMMENT '阅读成绩',
    writing_score DECIMAL(5,2) COMMENT '写作成绩',
    translation_score DECIMAL(5,2) COMMENT '翻译成绩',
    certificate_no VARCHAR(50) COMMENT '证书编号',
    certificate_date DATE COMMENT '发证日期',
    exam_location VARCHAR(100) COMMENT '考试地点',
    exam_room_no VARCHAR(20) COMMENT '考场号',
    seat_no VARCHAR(10) COMMENT '座位号',
    registration_date DATE COMMENT '报名时间',
    score_release_date DATE COMMENT '成绩发布时间',
    data_source VARCHAR(50) COMMENT '数据来源',
    remark VARCHAR(500) COMMENT '备注',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_student_exam (student_id, exam_type, exam_date),
    INDEX idx_student_id (student_id),
    INDEX idx_exam_type (exam_type),
    INDEX idx_exam_date (exam_date),
    INDEX idx_is_passed (is_passed),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='等级考试成绩表';

-- 1.7 教学计划主表
CREATE TABLE IF NOT EXISTS teaching_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    plan_id VARCHAR(30) NOT NULL UNIQUE COMMENT '计划编号',
    plan_name VARCHAR(200) NOT NULL COMMENT '计划名称',
    major_code VARCHAR(20) NOT NULL COMMENT '专业代码',
    major_name VARCHAR(100) NULL COMMENT '专业名称',
    grade VARCHAR(10) NOT NULL COMMENT '年级',
    schooling_length INT NOT NULL COMMENT '学制',
    degree_type VARCHAR(20) NOT NULL COMMENT '学位类型',
    education_level VARCHAR(20) NOT NULL COMMENT '培养层次',
    college_code VARCHAR(20) NOT NULL COMMENT '学院代码',
    college_name VARCHAR(100) NULL COMMENT '学院名称',
    total_credits DECIMAL(5,2) NOT NULL COMMENT '总学分要求',
    required_credits DECIMAL(5,2) NOT NULL COMMENT '必修学分',
    elective_credits DECIMAL(5,2) NOT NULL COMMENT '选修学分',
    practice_credits DECIMAL(5,2) COMMENT '实践学分',
    general_education_credits DECIMAL(5,2) COMMENT '通识教育学分',
    major_core_credits DECIMAL(5,2) COMMENT '专业核心学分',
    major_elective_credits DECIMAL(5,2) COMMENT '专业选修学分',
    training_objective TEXT COMMENT '培养目标',
    training_requirements TEXT COMMENT '培养要求',
    main_disciplines VARCHAR(200) COMMENT '主干学科',
    core_courses TEXT COMMENT '核心课程',
    plan_status VARCHAR(20) NOT NULL COMMENT '计划状态',
    effective_date DATE NOT NULL COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期',
    creator VARCHAR(50) COMMENT '制定人',
    reviewer VARCHAR(50) COMMENT '审核人',
    review_date DATE COMMENT '审核时间',
    version VARCHAR(20) COMMENT '版本号',
    revision_notes TEXT COMMENT '修订说明',
    attachment_path VARCHAR(500) COMMENT '附件路径',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_major_grade (major_code, grade),
    INDEX idx_plan_status (plan_status),
    INDEX idx_effective_date (effective_date),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学计划主表';

-- 1.8 教学计划课程明细表
CREATE TABLE IF NOT EXISTS teaching_plan_courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    detail_id VARCHAR(30) NOT NULL UNIQUE COMMENT '明细编号',
    plan_id VARCHAR(30) NOT NULL COMMENT '计划编号',
    course_code VARCHAR(20) NOT NULL COMMENT '课程代码',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_type VARCHAR(20) NOT NULL COMMENT '课程类型',
    course_category VARCHAR(50) COMMENT '课程类别',
    credits DECIMAL(4,2) NOT NULL COMMENT '学分',
    hours INT NOT NULL COMMENT '学时',
    theory_hours INT COMMENT '理论学时',
    lab_hours INT COMMENT '实验学时',
    semester INT NOT NULL COMMENT '开课学期',
    assessment_method VARCHAR(20) COMMENT '考核方式',
    prerequisite_courses VARCHAR(200) COMMENT '先修课程',
    sort_order INT COMMENT '排序号',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    sync_source VARCHAR(20) NOT NULL COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_plan_id (plan_id),
    INDEX idx_course_code (course_code),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学计划课程明细表';

-- 1.9 同步日志表
CREATE TABLE IF NOT EXISTS sync_logs (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `sync_type` VARCHAR(50) NOT NULL COMMENT '同步类型: FULL/INCREMENTAL',
    `data_source` VARCHAR(50) NOT NULL COMMENT '数据源',
    `sync_method` VARCHAR(50) NOT NULL COMMENT '同步方法: REPLACE_ALL/UPSERT',
    `status` VARCHAR(50) NOT NULL COMMENT '状态: RUNNING/SUCCESS/FAILED',
    `total_count` INT DEFAULT 0 COMMENT '总记录数',
    `success_count` INT DEFAULT 0 COMMENT '成功数',
    `fail_count` INT DEFAULT 0 COMMENT '失败数',
    `skip_count` INT DEFAULT 0 COMMENT '跳过数',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `duration_ms` BIGINT COMMENT '耗时（毫秒）',
    `error_message` TEXT COMMENT '错误信息',
    `trigger_type` VARCHAR(50) COMMENT '触发类型: MANUAL/SCHEDULED',
    `operator` VARCHAR(50) COMMENT '操作人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_status (`status`),
    INDEX idx_sync_type (`sync_type`),
    INDEX idx_start_time (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='同步日志表';

-- 1.10 字段映射配置表
CREATE TABLE IF NOT EXISTS field_mappings (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `data_source` VARCHAR(50) NOT NULL COMMENT '数据源标识',
    `source_field` VARCHAR(100) NOT NULL COMMENT '源字段名（API返回的JSON键名）',
    `target_field` VARCHAR(100) NOT NULL COMMENT '目标字段名（Java实体属性名）',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '源字段类型',
    `target_type` VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '目标字段类型',
    `converter` VARCHAR(50) NOT NULL DEFAULT 'NONE' COMMENT '转换器: NONE/SPLIT/BOOLEAN/DATE',
    `converter_config` JSON COMMENT '转换器配置（JSON格式）',
    `default_value` VARCHAR(500) COMMENT '默认值（源数据为空时使用）',
    `is_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否必填',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `remark` VARCHAR(500) COMMENT '字段映射说明',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX idx_data_source (`data_source`),
    INDEX idx_target_field (`target_field`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字段映射配置表';

-- ═══════════════════════════════════════════════════════════════════════
-- Part 2: 学生个人课表 (原 V12 Part 1.1)
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS student_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100),
    class_id VARCHAR(50) NOT NULL,
    course_code VARCHAR(50) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    teacher_id VARCHAR(50),
    teacher_name VARCHAR(100),
    day_of_week TINYINT NULL,
    period VARCHAR(20) NULL,
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

-- ═══════════════════════════════════════════════════════════════════════
-- Part 3: 学校主数据 + 教师学生绑定 (原 V14 Part 1-3)
-- V14 在 school_student 上加列, V42 链接 users → 保留列添加顺序
-- ═══════════════════════════════════════════════════════════════════════

-- 3.1 学生主数据 (CDSP 链式拉取产物)
CREATE TABLE IF NOT EXISTS school_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id VARCHAR(50) NOT NULL UNIQUE COMMENT '学号（对应 CDSP xh / XH）',
    student_name VARCHAR(100) COMMENT '学生姓名（SSO attributes 或手工补全）',
    college_name VARCHAR(100) NULL COMMENT '学院名称（CDSP kkyx）',
    college_code VARCHAR(50) COMMENT '学院代码（CDSP 未提供，预留）',
    class_code VARCHAR(50) COMMENT '行政班号（CAS attributes 补全，首次 NULL）',
    class_name VARCHAR(100) COMMENT '班级名称（CAS attributes 补全）',
    grade VARCHAR(20) COMMENT '年级（可用学号前 4 位推断）',
    major_name VARCHAR(100) COMMENT '专业（CAS attributes 补全）',
    source_course_codes TEXT COMMENT '发现该学生的课程代码列表（JSON 数组），诊断用',
    sync_time DATETIME NOT NULL COMMENT '首次同步时间',
    last_exam_sync_time DATETIME COMMENT '上次四六级成绩同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name),
    INDEX idx_grade (grade),
    INDEX idx_class_code (class_code),
    INDEX idx_last_exam (last_exam_sync_time),
    INDEX idx_last_schedule (last_schedule_sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的学生主数据（链式拉取产物）';

-- 3.2 教师主数据 (CDSP 链式拉取产物)
CREATE TABLE IF NOT EXISTS school_teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    teacher_id VARCHAR(50) NOT NULL UNIQUE COMMENT '工号（CDSP rkjsh / skjszgh）',
    teacher_name VARCHAR(100) COMMENT '教师姓名（CDSP rkjsxm）',
    college_name VARCHAR(100) NULL COMMENT '学院（来自链式触发学院）',
    college_code VARCHAR(50) COMMENT '学院代码（预留）',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的教师主数据';

-- 3.3 V14: 放宽 college_name 为 NULL (兼容空数据)
DROP PROCEDURE IF EXISTS _v8_school_student_college_nullable;
CREATE PROCEDURE _v8_school_student_college_nullable()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'college_name'
                 AND IS_NULLABLE = 'NO') THEN
        ALTER TABLE school_student MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称（CDSP kkyx）';
    END IF;
END;
CALL _v8_school_student_college_nullable();
DROP PROCEDURE IF EXISTS _v8_school_student_college_nullable;

DROP PROCEDURE IF EXISTS _v8_school_teacher_college_nullable;
CREATE PROCEDURE _v8_school_teacher_college_nullable()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_teacher'
                 AND COLUMN_NAME = 'college_name'
                 AND IS_NULLABLE = 'NO') THEN
        ALTER TABLE school_teacher MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院（来自链式触发学院）';
    END IF;
END;
CALL _v8_school_teacher_college_nullable();
DROP PROCEDURE IF EXISTS _v8_school_teacher_college_nullable;

-- 3.4 教师-学生绑定
CREATE TABLE IF NOT EXISTS teacher_student_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    teacher_id VARCHAR(50) NOT NULL COMMENT '工号',
    student_id VARCHAR(50) NOT NULL COMMENT '学号',
    course_code VARCHAR(50) NOT NULL COMMENT '课程代码 kcdm',
    course_class_code VARCHAR(50) COMMENT '课程班号 kcbh',
    academic_year VARCHAR(20) NOT NULL DEFAULT '' COMMENT '学年（NULL 替换为空串以配合 UNIQUE 索引）',
    semester VARCHAR(10) NOT NULL DEFAULT '' COMMENT '学期（秋/春），NULL 替换为空串',
    source_type VARCHAR(20) NOT NULL DEFAULT 'CHAIN_SYNC' COMMENT '来源: CHAIN_SYNC / MANUAL',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_teacher_student_course (teacher_id, student_id, course_code, academic_year, semester),
    INDEX idx_teacher (teacher_id),
    INDEX idx_student (student_id),
    INDEX idx_course (course_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师-学生绑定关系表';

-- 3.5 显示字段 [原 V21: teacher_name/student_name/college_name/course_name + idx_college_name]
DROP PROCEDURE IF EXISTS _v8_tsb_display_cols;
CREATE PROCEDURE _v8_tsb_display_cols()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'teacher_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN teacher_name VARCHAR(100) NULL
            COMMENT '教师姓名' AFTER teacher_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'student_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN student_name VARCHAR(100) NULL
            COMMENT '学生姓名' AFTER student_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'college_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN college_name VARCHAR(100) NULL
            COMMENT '学院名称' AFTER student_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'course_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN course_name VARCHAR(200) NULL
            COMMENT '课程名称' AFTER course_code;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND INDEX_NAME = 'idx_college_name') THEN
        ALTER TABLE teacher_student_binding ADD INDEX idx_college_name (college_name);
    END IF;
END;
CALL _v8_tsb_display_cols();
DROP PROCEDURE IF EXISTS _v8_tsb_display_cols;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 4: users 关联学校主数据 (原 V42)
--   - 规范化历史 student_<digits> username → <digits>
--   - users 加 school_student_id / school_teacher_id
--   - 回填外键
--   - 创建 v_user_school_profile 视图
-- 顺序依赖: school_student / school_teacher 必须在 Part 3 已建
-- ═══════════════════════════════════════════════════════════════════════

-- 4.1 规范化 username
UPDATE users
SET username = SUBSTRING(username, 9)
WHERE username REGEXP '^student_[0-9]+$';

-- 4.2 幂等新增外键列 (内层 PROCEDURE 已在 V42 验证, 此处保留同款模式)
DROP PROCEDURE IF EXISTS _v8_add_school_columns;
CREATE PROCEDURE _v8_add_school_columns()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'users'
                     AND COLUMN_NAME = 'school_student_id') THEN
        ALTER TABLE users
            ADD COLUMN school_student_id BIGINT NULL COMMENT '关联 school_student.id（学号匹配）' AFTER inner_uid,
            ADD COLUMN school_teacher_id BIGINT NULL COMMENT '关联 school_teacher.id（工号匹配）' AFTER school_student_id;
    END IF;
END;
CALL _v8_add_school_columns();
DROP PROCEDURE IF EXISTS _v8_add_school_columns;

-- 4.3 幂等创建索引
DROP PROCEDURE IF EXISTS _v8_add_school_indexes;
CREATE PROCEDURE _v8_add_school_indexes()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'users'
                     AND INDEX_NAME = 'idx_users_school_student_id') THEN
        CREATE INDEX idx_users_school_student_id ON users(school_student_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'users'
                     AND INDEX_NAME = 'idx_users_school_teacher_id') THEN
        CREATE INDEX idx_users_school_teacher_id ON users(school_teacher_id);
    END IF;
END;
CALL _v8_add_school_indexes();
DROP PROCEDURE IF EXISTS _v8_add_school_indexes;

-- 4.4 回填外键
UPDATE users u
JOIN school_student s ON s.student_id COLLATE utf8mb4_unicode_ci = u.username
SET u.school_student_id = s.id
WHERE u.school_student_id IS NULL;

UPDATE users u
JOIN school_teacher t ON t.teacher_id COLLATE utf8mb4_unicode_ci = u.username
SET u.school_teacher_id = t.id
WHERE u.school_teacher_id IS NULL;

-- 4.5 视图: 聚合学生的学校画像
DROP VIEW IF EXISTS v_user_school_profile;
CREATE VIEW v_user_school_profile AS
SELECT
    u.id                          AS user_id,
    u.username                    AS username,
    u.role                        AS role,
    u.real_name                   AS real_name,
    s.id                          AS school_student_id,
    s.student_id                  AS student_id,
    s.student_name                AS student_name,
    s.college_name                AS college_name,
    s.class_code                  AS class_code,
    s.class_name                  AS class_name,
    s.grade                       AS grade,
    s.major_name                  AS major_name,
    (SELECT COUNT(*) FROM course_selection cs            WHERE cs.student_id = s.student_id) AS course_selection_count,
    (SELECT COUNT(*) FROM teacher_student_binding tsb    WHERE tsb.student_id = s.student_id) AS teacher_binding_count,
    (SELECT MAX(les.exam_date) FROM level_exam_scores les WHERE les.student_id = s.student_id) AS last_exam_date,
    (SELECT MAX(les.score)     FROM level_exam_scores les WHERE les.student_id = s.student_id) AS best_exam_score
FROM users u
JOIN school_student s ON s.id = u.school_student_id
WHERE u.deleted = 0;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 5: system_config seed (原 V12 Part 2)
--   5.1 数据源配置
--   5.2 功能开关
--   5.3 API 认证 appId + appSecret 签名
-- ═══════════════════════════════════════════════════════════════════════

-- 5.1 数据源配置
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

-- 5.2 功能开关 (运维端 + 学生端课表)
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_browser', 'true', '运维端学校数据浏览功能（行政班、课程、教学班等）', 0),
('feature_toggle', 'ops.school_data_config', 'true', '运维端学校数据源配置功能（API/ETL切换、连接测试）', 0),
('feature_toggle', 'page_schedule', 'true', '学生端课表查看功能（从教务系统同步课表数据）', 0);

-- 5.3 API 认证 appId + appSecret 签名
-- sign = Base64( MD5( appId + appSecret + timestamp ) )
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('school_data', 'api_app_id', '', 'API 应用 ID（由学校数据平台分配，用于签名认证）', 1),
('school_data', 'api_app_secret', '', 'API 应用 Secret（由学校数据平台分配，与 appId + timestamp 组合生成签名）', 1);

-- 清理旧部署遗留的 api_token (Bearer Token 认证已废弃; 全新部署不会有此记录)
DELETE FROM `system_config`
WHERE `config_group` = 'school_data' AND `config_key` = 'api_token';

-- ═══════════════════════════════════════════════════════════════════════
-- Part 6: field_mappings 按 CDSP 官方 PDF 对齐 (原 V12 Part 3)
--
-- 基于 9 份 cdsp.imau.edu.cn data-api/v2 官方接口文档原文复核 (2026-04-21):
--   行政班基本信息 / 课程基本信息 / 本专科生课程信息 / 课程安排信息 /
--   学生选课 / 本专科生等级考试成绩 / 教学计划信息 / 教师授课 / 学生课表
--
-- 注意:
--   - field_mappings 列定义 (V9 Part 1.10): source_field / target_field / converter / remark
--     (历史 V19 bug 误用 converter_type/description/created_by → INSERT IGNORE 静默失败)
--   - 本 Part 字段顺序严格按 V9 DDL 12 列定义: data_source, source_field, target_field,
--     source_type, target_type, converter, converter_config, default_value,
--     is_required, sort_order, is_active, remark
--   - V20-align PDF 修正: 剔除 V12.1/V19 遗留 snake_case 冗余映射
--     (FieldMappingServiceImpl.applyMapping 自动将 target_field 转 camelCase,
--      snake_case/camelCase 两套是等价重复)
-- ═══════════════════════════════════════════════════════════════════════

-- Step 6.0: 清空 V12 历史遗留的错误 mappings (幂等)
DELETE FROM field_mappings WHERE data_source IN (
    'administrative_class', 'school_course', 'teaching_class',
    'course_schedule', 'course_selection', 'level_exam_scores',
    'teaching_plans', 'teacher_courses', 'student_schedule'
);

-- 1. administrative_class (cdsp_1710949911983374368 / 行政班基本信息)
-- PDF fields (UPPERCASE): XZBH, BJMC, XSLB, SSXYBM, SSXYMC, SSNJ, BZRGH, BZRXM, ZY, JBNY
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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
-- 此端点实际返回课程信息 (非教学班), 代码已路由到 upsertSchoolCourse
-- PDF fields (UPPERCASE): KCXS, KCXF, KKYX, KCJJ, KCYWMC, KCMC, KCH, KSLX
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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
-- xq 返回中文 "秋"/"春" varchar, target_type=STRING, service 层做中文→Integer 转换
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_schedule', 'xkkch',        'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（作 class_id）'),
('course_schedule', '选课课号',     'class_id',       'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '选课课号（中文字段名）'),
('course_schedule', 'kcdm',         'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程代码（英文缩写）'),
('course_schedule', '课程代码',     'course_code',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程代码（中文字段名）'),
('course_schedule', 'kcmc',         'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程名称（英文缩写）'),
('course_schedule', '课程名称',     'course_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '课程名称（中文字段名）'),
('course_schedule', 'jsgh',         'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师工号（英文缩写）'),
('course_schedule', '教师工号',     'teacher_id',     'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师工号（中文字段名）'),
('course_schedule', 'jsxm',         'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师姓名（英文缩写）'),
('course_schedule', '教师姓名',     'teacher_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '教师姓名（中文字段名）'),
('course_schedule', 'kkyx',         'college_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '开课院系（英文缩写）'),
('course_schedule', '开课院系',     'college_name',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 1, 1, '开课院系（中文字段名）'),
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
-- PDF 只返回 4 个字段, 远少于 CourseSelection 实体 NOT NULL 列数
-- KCBH 在链式拉取与历史兼容中按"课程班号"使用, 应统一落到 classId
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('course_selection', 'XH',     'student_id',        'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('course_selection', 'KCBH',   'class_id',          'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '课程班号（作 class_id）'),
('course_selection', 'KSXZDM', 'selection_status',  'STRING', 'STRING', 'NONE', NULL, NULL, 0, 3, 1, '考试性质代码（作选课状态）'),
('course_selection', 'KSXZMC', 'class_time',        'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '考试性质名称（暂存 classTime）'),
('course_selection', 'XF',     'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '学分（兼容扩展返回）'),
('course_selection', '学分',   'credits',           'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 0, 5, 1, '学分（中文字段名）'),
('course_selection', 'XM',     'student_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '姓名（兼容扩展返回）'),
('course_selection', '姓名',   'student_name',      'STRING', 'STRING', 'NONE', NULL, NULL, 0, 6, 1, '姓名（中文字段名）');

-- 6. level_exam_scores (DS0076 / 本专科生等级考试成绩)
-- PDF fields (lowercase): xh, xhxm, xm, kskm, ksrq, fskscj
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xh',     'student_id',   'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '学号'),
('level_exam_scores', 'xm',     'student_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 2, 1, '姓名'),
('level_exam_scores', 'kskm',   'exam_name',    'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '考试科目（作考试名称）'),
('level_exam_scores', 'ksrq',   'exam_date',    'STRING', 'DATE',   'DATE', '{"pattern":"yyyy-MM-dd"}', NULL, 1, 4, 1, '考试日期'),
('level_exam_scores', 'fskscj', 'score',        'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 5, 1, '考试成绩（分数）');

-- 7. teaching_plans (cdsp_1715289655026466836 / 教学计划信息)
-- PDF fields (lowercase): zxs, skjszgh, kkdwdm, kkxq, cksm, ksfsdm, kkjhid, kkxn,
--   kcdm, kkxq_name, kkdwdm_name, ksfsmc
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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
-- 教师授课数据 upsert 到 TeachingClass 实体
-- 语义 (按 PDF 返回示例核对 2026-04-21):
--   kcdm = 课程代码 → courseCode
--   kcbh = 课程班号 → classId (TeachingClass 主键)
--   kxh  = 课序号 (班内序号, 通常与 kcdm 相同) → 不入库
--   xq   = 学期, API 返回中文 "秋"/"春" varchar
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
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

-- ═══════════════════════════════════════════════════════════════════════
-- Part 7: school_* / teaching_* 表字段调整 (原 V12)
-- 仅保留与 school_* 相关的部分:
--   - administrative_class / teaching_plans college_name/major_name 放宽 NULL
--   - course_selection 放宽多个字段 NULL
--   - course_schedule 放宽字段 NULL
--   - teaching_class 放宽字段 NULL
--   - FK 重声明 (course_schedule → teaching_class ON DELETE SET NULL)
--   - idx_student_academic / idx_class_academic 索引
-- videos / media 部分归入 V2
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE administrative_class
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称 (YXMC)',
    MODIFY COLUMN major_name VARCHAR(100) NULL COMMENT '专业名称 (ZYMC)';

ALTER TABLE teaching_plans
    MODIFY COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称',
    MODIFY COLUMN major_name VARCHAR(100) NULL COMMENT '专业名称';

UPDATE administrative_class SET college_name = NULL WHERE college_name = '';
UPDATE administrative_class SET major_name = NULL WHERE major_name = '';
UPDATE teaching_plans SET college_name = NULL WHERE college_name = '';
UPDATE teaching_plans SET major_name = NULL WHERE major_name = '';

ALTER TABLE course_selection
    MODIFY COLUMN student_name VARCHAR(100) NULL COMMENT '学生姓名',
    MODIFY COLUMN credits DECIMAL(3,1) NULL COMMENT '学分（CDSP API不返回，可为空）',
    MODIFY COLUMN teacher_id VARCHAR(50) NULL COMMENT '授课教师工号',
    MODIFY COLUMN teacher_name VARCHAR(50) NULL COMMENT '授课教师姓名',
    MODIFY COLUMN academic_year VARCHAR(20) NULL COMMENT '学年',
    MODIFY COLUMN semester INT NULL COMMENT '学期',
    MODIFY COLUMN selection_time DATETIME NULL COMMENT '选课时间',
    MODIFY COLUMN selection_status VARCHAR(20) NULL DEFAULT 'SELECTED' COMMENT '选课状态 SELECTED/DROPPED/COMPLETED';

ALTER TABLE course_schedule
    MODIFY COLUMN class_id VARCHAR(50) NULL COMMENT '教学班号 (JXBID)',
    MODIFY COLUMN day_of_week INT NULL COMMENT '星期几 (XQJ) 1-7',
    MODIFY COLUMN period VARCHAR(20) NULL COMMENT '节次 (JCS) 如"1-2"',
    MODIFY COLUMN week_range VARCHAR(50) NULL COMMENT '周次 (ZCD) 如"1-16周"',
    MODIFY COLUMN class_location VARCHAR(100) NULL COMMENT '上课地点 (CDMC)';

ALTER TABLE teaching_class
    MODIFY COLUMN class_name VARCHAR(100) NULL COMMENT '教学班名称 (JXBMC)',
    MODIFY COLUMN capacity INT NULL DEFAULT 0 COMMENT '班级容量',
    MODIFY COLUMN class_status VARCHAR(20) NULL DEFAULT 'ACTIVE' COMMENT '课程状态';

-- 清理孤儿 course_schedule (无对应 teaching_class)
DELETE cs
FROM course_schedule cs
LEFT JOIN teaching_class tc ON cs.class_id = tc.class_id
WHERE cs.class_id IS NOT NULL
  AND tc.class_id IS NULL;

-- 清理孤儿 course_selection (无对应 school_course)
DELETE csel
FROM course_selection csel
LEFT JOIN school_course sc ON csel.course_code = sc.course_code
WHERE csel.course_code IS NOT NULL
  AND sc.course_code IS NULL;

-- 重建 course_schedule FK (idempotent)
DROP PROCEDURE IF EXISTS _v8_drop_fk_course_schedule;
CREATE PROCEDURE _v8_drop_fk_course_schedule()
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'course_schedule'
                 AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
                 AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
        ALTER TABLE course_schedule DROP FOREIGN KEY fk_course_schedule_teaching_class;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'course_schedule'
                 AND CONSTRAINT_NAME = 'course_schedule_ibfk_1'
                 AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
        ALTER TABLE course_schedule DROP FOREIGN KEY course_schedule_ibfk_1;
    END IF;
END;
CALL _v8_drop_fk_course_schedule();
DROP PROCEDURE IF EXISTS _v8_drop_fk_course_schedule;

DROP PROCEDURE IF EXISTS _v8_add_fk_course_schedule;
CREATE PROCEDURE _v8_add_fk_course_schedule()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'course_schedule'
                     AND CONSTRAINT_NAME = 'fk_course_schedule_teaching_class'
                     AND CONSTRAINT_TYPE = 'FOREIGN KEY') THEN
        ALTER TABLE course_schedule ADD CONSTRAINT fk_course_schedule_teaching_class FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
END;
CALL _v8_add_fk_course_schedule();
DROP PROCEDURE IF EXISTS _v8_add_fk_course_schedule;

-- idx_student_academic / idx_class_academic (幂等)
DROP PROCEDURE IF EXISTS _v8_idx_student_academic;
CREATE PROCEDURE _v8_idx_student_academic()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'course_selection'
                     AND INDEX_NAME = 'idx_student_academic') THEN
        CREATE INDEX idx_student_academic ON course_selection(student_id, academic_year, semester);
    END IF;
END;
CALL _v8_idx_student_academic();
DROP PROCEDURE IF EXISTS _v8_idx_student_academic;

DROP PROCEDURE IF EXISTS _v8_idx_class_academic;
CREATE PROCEDURE _v8_idx_class_academic()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'course_schedule'
                     AND INDEX_NAME = 'idx_class_academic') THEN
        CREATE INDEX idx_class_academic ON course_schedule(class_id, academic_year, semester);
    END IF;
END;
CALL _v8_idx_class_academic();
DROP PROCEDURE IF EXISTS _v8_idx_class_academic;

-- ═══════════════════════════════════════════════════════════════════════
-- Part 8: Feature Toggle / 学院链式配置 seed (原 V14 Part 4)
-- 注: sso.auto_grant_student / sso.auto_grant_teacher 由 V7 强制覆盖为 true
-- ═══════════════════════════════════════════════════════════════════════

INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_chain_fetch', 'true', '运维端按学院链式拉取功能开关', 0),
('feature_toggle', 'student.lazy_data_sync', 'true', '学生端按需 Lazy 同步四六级/课表', 0),
('feature_toggle', 'sso.auto_grant_student', 'false', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'false', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0),
('school_data', 'sync_college_chain_cron', '0 30 3 * * ?', '按学院链式刷新 cron 表达式', 0),
('school_data', 'sync_college_chain_colleges', '外国语学院', '链式刷新白名单（逗号分隔学院名）', 0),
('school_data', 'sync_college_chain_match_mode', 'eq', '学院名匹配模式（eq=精确 / contains=包含）', 0);

-- 清理历史旧 key (无下划线)
DELETE FROM `system_config`
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN (
      'ops_school_data_chain_fetch',
      'sso_auto_grant_student',
      'sso_auto_grant_teacher',
      'student_lazy_data_sync',
      'page_user_tests'
  );

-- 标准化 description 与 value
UPDATE `system_config`
SET `description` = CASE `config_key`
        WHEN 'ops.school_data_browser' THEN '运维端学校数据浏览功能（行政班、课程、教学班等）'
        WHEN 'ops.school_data_config' THEN '运维端学校数据源配置功能（API/ETL切换、连接测试）'
        WHEN 'page_schedule' THEN '学生端-我的课表页'
        WHEN 'teacher.school_data_enabled' THEN '教师端学校数据功能（课表/选课/成绩）'
        WHEN 'ops.school_data_chain_fetch' THEN '运维端按学院链式拉取功能开关'
        WHEN 'student.lazy_data_sync' THEN '学生端按需 Lazy 同步四六级/课表'
        WHEN 'sso.auto_grant_student' THEN 'SSO 学号匹配 school_student 自动授权 STUDENT'
        WHEN 'sso.auto_grant_teacher' THEN 'SSO 工号匹配 school_teacher 自动授权 TEACHER'
        ELSE `description`
    END,
    `config_value` = CASE `config_key`
        WHEN 'ops.school_data_browser' THEN 'true'
        WHEN 'ops.school_data_config' THEN 'true'
        WHEN 'page_schedule' THEN 'true'
        WHEN 'ops.school_data_chain_fetch' THEN 'true'
        ELSE `config_value`
    END
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN (
      'ops.school_data_browser',
      'ops.school_data_config',
      'page_schedule',
      'teacher.school_data_enabled',
      'ops.school_data_chain_fetch',
      'student.lazy_data_sync',
      'sso.auto_grant_student',
      'sso.auto_grant_teacher'
  );

-- ═══════════════════════════════════════════════════════════════════════
-- Part 9: 字段映射补充 (原 V12 末尾)
--   - administrative_class 学院代码/名称补充 (YXDM/YXMC, 历史映射替换为新映射)
--   - teaching_plans 专业代码/名称补充 (zybh/zymc)
-- 注: PDF 字段实测 YXDM/YXMC 更准确, 旧 SSXYBM/SSXYMC 关闭
-- ═══════════════════════════════════════════════════════════════════════

UPDATE field_mappings
SET is_active = 0
WHERE data_source = 'administrative_class'
  AND source_field IN ('SSXYBM', 'SSXYMC', '所属学院编码', '所属学院名称');

INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',       'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（英文缩写）'),
('administrative_class', '学院代码',   'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（中文）'),
('administrative_class', 'YXMC',       'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（英文缩写）'),
('administrative_class', '学院名称',   'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（中文）'),
('administrative_class', 'ZYDM',       'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（英文缩写）'),
('administrative_class', '专业代码',   'major_code',   'STRING', 'STRING', 'NONE', NULL, NULL, 0, 9, 1, '专业代码（中文）'),
('teaching_plans', 'zybh', 'major_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 12, 1, '专业编号'),
('teaching_plans', 'zymc', 'major_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 13, 1, '专业名称');