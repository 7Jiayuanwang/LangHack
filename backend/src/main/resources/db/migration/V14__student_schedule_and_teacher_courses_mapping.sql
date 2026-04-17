-- ============================================================================
-- Flyway Migration Script: 学生课表表结构+字段映射, 教师授课字段映射
-- Version: V14
-- Description: 创建student_schedule表, 新增student_schedule和teacher_courses字段映射
-- Reference: docs/SCHOOL_DATA_API_SPEC.md 接口8/9
-- Author: System
-- Date: 2026-04-15
-- ============================================================================

-- ============================================================================
-- 1. 学生课表 (student_schedule) 表结构 — 接口: /api/xskb
-- ============================================================================
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

-- ============================================================================
-- 2. 教师授课 (teacher_courses) 字段映射 — 接口: /api/jssk
-- 教师授课接口补充教师与教学班的关联信息，与teaching_class表共享
-- ============================================================================
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('teacher_courses', 'jgh', 'teacher_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 1, 1, '教师工号'),
('teacher_courses', 'xm', 'teacher_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 2, 1, '教师姓名'),
('teacher_courses', 'jxbid', 'class_id', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 3, 1, '教学班号'),
('teacher_courses', 'kcbh', 'course_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 4, 1, '课程编号'),
('teacher_courses', 'kcmc', 'course_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 5, 1, '课程名称'),
('teacher_courses', 'xn', 'academic_year', 'STRING', 'STRING', 'NONE', NULL, NULL, 1, 6, 1, '学年'),
('teacher_courses', 'xq', 'semester', 'NUMBER', 'NUMBER', 'NONE', NULL, NULL, 1, 7, 1, '学期');

-- ============================================================================
-- 3. 学生课表 (student_schedule) 字段映射 — 接口: /api/xskb
-- 学生维度课表数据，包含选课后的完整课表信息
-- ============================================================================
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
