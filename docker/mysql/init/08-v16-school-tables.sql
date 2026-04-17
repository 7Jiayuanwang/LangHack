USE `english_learning`;
SET NAMES utf8mb4;

-- V9 School Data Sync Tables (generated from V9 migration, column COMMENTs removed for init compatibility)
-- Source: V9__school_data_sync.sql (原 V16)

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
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    class_code VARCHAR(50) NOT NULL UNIQUE ,
    class_name VARCHAR(100) NOT NULL ,
    class_short_name VARCHAR(50) ,
    college_code VARCHAR(50) NOT NULL ,
    college_name VARCHAR(100) NOT NULL ,
    major_code VARCHAR(50) NOT NULL ,
    major_name VARCHAR(100) NOT NULL ,
    grade VARCHAR(10) NOT NULL ,
    class_type VARCHAR(20) ,
    head_teacher_code VARCHAR(50) ,
    head_teacher_name VARCHAR(50) ,
    student_count INT DEFAULT 0 ,
    male_count INT DEFAULT 0 ,
    female_count INT DEFAULT 0 ,
    class_status VARCHAR(20) NOT NULL ,
    establish_date DATE ,
    graduate_date DATE ,
    remark VARCHAR(500) ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    INDEX idx_college (college_code),
    INDEX idx_major (major_code),
    INDEX idx_grade (grade),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Administrative class info';

-- 1.2 课程表
CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    course_code VARCHAR(50) NOT NULL UNIQUE ,
    course_name VARCHAR(200) NOT NULL ,
    course_name_en VARCHAR(200) ,
    course_short_name VARCHAR(100) ,
    course_nature VARCHAR(50) NOT NULL ,
    course_category VARCHAR(50) NOT NULL ,
    credits DECIMAL(3,1) NOT NULL ,
    total_hours INT NOT NULL ,
    theory_hours INT ,
    experiment_hours INT ,
    practice_hours INT ,
    computer_hours INT ,
    college_code VARCHAR(50) NOT NULL ,
    college_name VARCHAR(100) NOT NULL ,
    department_code VARCHAR(50) ,
    department_name VARCHAR(100) ,
    leader_code VARCHAR(50) ,
    leader_name VARCHAR(50) ,
    assessment_method VARCHAR(50) NOT NULL ,
    is_bilingual VARCHAR(10) ,
    is_online VARCHAR(10) ,
    applicable_majors VARCHAR(500) ,
    prerequisite_codes VARCHAR(500) ,
    prerequisite_names VARCHAR(500) ,
    course_intro TEXT ,
    teaching_objectives TEXT ,
    course_status VARCHAR(20) NOT NULL ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    INDEX idx_college (college_code),
    INDEX idx_nature (course_nature),
    INDEX idx_category (course_category),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Course basic info';

-- ============================================================================
-- 2. 教学组织层 (Teaching Organization Layer)
-- ============================================================================

-- 2.1 教学班表
CREATE TABLE IF NOT EXISTS teaching_class (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    class_id VARCHAR(50) NOT NULL UNIQUE ,
    class_name VARCHAR(100) ,
    course_code VARCHAR(50) NOT NULL ,
    course_name VARCHAR(200) NOT NULL ,
    teacher_id VARCHAR(50) NOT NULL ,
    teacher_name VARCHAR(50) NOT NULL ,
    academic_year VARCHAR(20) NOT NULL ,
    semester INT NOT NULL ,
    capacity INT NOT NULL ,
    enrolled_count INT DEFAULT 0 ,
    class_status VARCHAR(20) NOT NULL ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    FOREIGN KEY (course_code) REFERENCES course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_course (course_code),
    INDEX idx_teacher (teacher_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Teaching class info';

-- 2.2 课程安排表
CREATE TABLE IF NOT EXISTS course_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    class_id VARCHAR(50) NOT NULL ,
    day_of_week INT NOT NULL ,
    period VARCHAR(20) NOT NULL ,
    week_range VARCHAR(50) NOT NULL ,
    class_location VARCHAR(100) NOT NULL ,
    academic_year VARCHAR(20) NOT NULL ,
    semester INT NOT NULL ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_class_id (class_id),
    INDEX idx_day_period (day_of_week, period),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Course schedule info';

-- ============================================================================
-- 3. 选课与成绩层 (Enrollment & Grade Layer)
-- ============================================================================

-- 3.1 Course selection records
CREATE TABLE IF NOT EXISTS course_selection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    selection_id VARCHAR(50) NOT NULL UNIQUE ,
    student_id VARCHAR(50) NOT NULL ,
    student_name VARCHAR(100) NOT NULL ,
    course_code VARCHAR(50) NOT NULL ,
    course_name VARCHAR(200) NOT NULL ,
    class_id VARCHAR(50) NOT NULL ,
    teacher_id VARCHAR(50) NOT NULL ,
    teacher_name VARCHAR(50) NOT NULL ,
    credits DECIMAL(3,1) NOT NULL ,
    academic_year VARCHAR(20) NOT NULL ,
    semester INT NOT NULL ,
    class_time VARCHAR(100) ,
    class_location VARCHAR(100) ,
    selection_time DATETIME NOT NULL ,
    selection_status VARCHAR(20) NOT NULL ,
    regular_grade DECIMAL(5,2) ,
    final_grade DECIMAL(5,2) ,
    total_grade DECIMAL(5,2) ,
    grade_point DECIMAL(3,2) ,
    attendance_count INT DEFAULT 0 ,
    absence_count INT DEFAULT 0 ,
    grade_remark VARCHAR(500) ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    FOREIGN KEY (course_code) REFERENCES course(course_code) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (class_id) REFERENCES teaching_class(class_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_student (student_id),
    INDEX idx_course (course_code),
    INDEX idx_class (class_id),
    INDEX idx_academic_year_semester (academic_year, semester),
    INDEX idx_selection_status (selection_status),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Course selection records';

-- 3.2 Level exam scores
CREATE TABLE IF NOT EXISTS level_exam_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    student_id VARCHAR(50) NOT NULL ,
    student_name VARCHAR(100) NOT NULL ,
    exam_type VARCHAR(50) NOT NULL ,
    exam_name VARCHAR(100) NOT NULL ,
    admission_ticket_no VARCHAR(30) ,
    exam_date DATE NOT NULL ,
    score DECIMAL(5,2) NOT NULL ,
    grade VARCHAR(20) ,
    is_passed BOOLEAN NOT NULL ,
    passing_score DECIMAL(5,2) ,
    listening_score DECIMAL(5,2) ,
    reading_score DECIMAL(5,2) ,
    writing_score DECIMAL(5,2) ,
    translation_score DECIMAL(5,2) ,
    certificate_no VARCHAR(50) ,
    certificate_date DATE ,
    exam_location VARCHAR(100) ,
    exam_room_no VARCHAR(20) ,
    seat_no VARCHAR(10) ,
    registration_date DATE ,
    score_release_date DATE ,
    data_source VARCHAR(50) ,
    remark VARCHAR(500) ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    UNIQUE KEY uk_student_exam (student_id, exam_type, exam_date),
    INDEX idx_student_id (student_id),
    INDEX idx_exam_type (exam_type),
    INDEX idx_exam_date (exam_date),
    INDEX idx_is_passed (is_passed),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Level exam scores';

-- 4. 教学计划层
CREATE TABLE IF NOT EXISTS teaching_plans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    plan_id VARCHAR(30) NOT NULL UNIQUE ,
    plan_name VARCHAR(200) NOT NULL ,
    major_code VARCHAR(20) NOT NULL ,
    major_name VARCHAR(100) NOT NULL ,
    grade VARCHAR(10) NOT NULL ,
    schooling_length INT NOT NULL ,
    degree_type VARCHAR(20) NOT NULL ,
    education_level VARCHAR(20) NOT NULL ,
    college_code VARCHAR(20) NOT NULL ,
    college_name VARCHAR(100) NOT NULL ,
    total_credits DECIMAL(5,2) NOT NULL ,
    required_credits DECIMAL(5,2) NOT NULL ,
    elective_credits DECIMAL(5,2) NOT NULL ,
    practice_credits DECIMAL(5,2) ,
    general_education_credits DECIMAL(5,2) ,
    major_core_credits DECIMAL(5,2) ,
    major_elective_credits DECIMAL(5,2) ,
    training_objective TEXT ,
    training_requirements TEXT ,
    main_disciplines VARCHAR(200) ,
    core_courses TEXT ,
    plan_status VARCHAR(20) NOT NULL ,
    effective_date DATE NOT NULL ,
    expiry_date DATE ,
    creator VARCHAR(50) ,
    reviewer VARCHAR(50) ,
    review_date DATE ,
    version VARCHAR(20) ,
    revision_notes TEXT ,
    attachment_path VARCHAR(500) ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,
    
    INDEX idx_major_grade (major_code, grade),
    INDEX idx_plan_status (plan_status),
    INDEX idx_effective_date (effective_date),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Teaching plan main table';

CREATE TABLE IF NOT EXISTS teaching_plan_courses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT ,
    detail_id VARCHAR(30) NOT NULL UNIQUE ,
    plan_id VARCHAR(30) NOT NULL ,
    course_code VARCHAR(20) NOT NULL ,
    course_name VARCHAR(100) NOT NULL ,
    course_type VARCHAR(20) NOT NULL ,
    course_category VARCHAR(50) ,
    credits DECIMAL(4,2) NOT NULL ,
    hours INT NOT NULL ,
    theory_hours INT ,
    lab_hours INT ,
    semester INT NOT NULL ,
    assessment_method VARCHAR(20) ,
    prerequisite_courses VARCHAR(200) ,
    sort_order INT ,
    sync_time DATETIME NOT NULL ,
    sync_source VARCHAR(20) NOT NULL ,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,

    INDEX idx_plan_id (plan_id),
    INDEX idx_course_code (course_code),
    INDEX idx_sync_time (sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Teaching plan course details';
