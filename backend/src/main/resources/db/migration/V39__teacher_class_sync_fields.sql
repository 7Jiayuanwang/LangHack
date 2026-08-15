-- V39: 教师班级同步 — 为 courses / classes 表增加学校数据关联字段
-- 用于"同步学校班级"功能：从 CDSP 学校数据自动建班时去重

-- courses 表加学校课程代码（NULL = 手动创建的课程，不影响唯一索引）
DROP PROCEDURE IF EXISTS _v39_courses_school_course_code;
CREATE PROCEDURE _v39_courses_school_course_code()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'courses'
                     AND COLUMN_NAME = 'school_course_code') THEN
        ALTER TABLE courses ADD COLUMN school_course_code VARCHAR(50) NULL
            COMMENT '关联学校课程代码(school_course.course_code)，用于同步去重';
        ALTER TABLE courses ADD UNIQUE INDEX uk_school_course_code (school_course_code);
    END IF;
END;
CALL _v39_courses_school_course_code();
DROP PROCEDURE IF EXISTS _v39_courses_school_course_code;

-- classes 表加学校教学班号（NULL = 手动创建的班级，不影响唯一索引）
DROP PROCEDURE IF EXISTS _v39_classes_school_class_id;
CREATE PROCEDURE _v39_classes_school_class_id()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'classes'
                     AND COLUMN_NAME = 'school_class_id') THEN
        ALTER TABLE classes ADD COLUMN school_class_id VARCHAR(50) NULL
            COMMENT '关联学校教学班号(teaching_class.class_id/kcbh)，用于同步去重';
        ALTER TABLE classes ADD UNIQUE INDEX uk_school_class_id (school_class_id);
    END IF;
END;
CALL _v39_classes_school_class_id();
DROP PROCEDURE IF EXISTS _v39_classes_school_class_id;
