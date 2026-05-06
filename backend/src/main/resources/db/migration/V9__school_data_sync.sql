-- ============================================================================
-- Flyway Migration Script: 学校数据同步表结构
-- Version: V100
-- Description: 创建学校数据接入相关的所有表
-- Author: System
-- Date: 2026-04-09
-- ============================================================================

-- ============================================================================
-- 1. 基础数据层 (Foundation Layer)
-- ============================================================================

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

-- ============================================================================
-- 2. 教学组织层 (Teaching Organization Layer)
-- ============================================================================

-- 2.1 教学班表
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

-- 2.2 课程安排表
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

-- ============================================================================
-- 3. 选课与成绩层 (Enrollment & Grade Layer)
-- ============================================================================

-- 3.1 选课记录表
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

-- 3.2 等级考试成绩表
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

-- 4. 教学计划层
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

-- ============================================================================
-- 6. 辅助表
-- ============================================================================

-- 6.1 同步日志表
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

-- 6.2 字段映射配置表
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
