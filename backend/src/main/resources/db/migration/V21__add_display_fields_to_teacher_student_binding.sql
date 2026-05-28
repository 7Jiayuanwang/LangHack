-- V21: 为 teacher_student_binding 表添加显示字段（学院、姓名、课程名称）
-- 用于前端管理界面直接展示，避免多表 JOIN 查询

DROP PROCEDURE IF EXISTS v21_add_display_fields;
DELIMITER $$
CREATE PROCEDURE v21_add_display_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'teacher_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN teacher_name VARCHAR(100) NULL COMMENT '教师姓名' AFTER teacher_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'student_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN student_name VARCHAR(100) NULL COMMENT '学生姓名' AFTER student_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'college_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称' AFTER student_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND COLUMN_NAME = 'course_name') THEN
        ALTER TABLE teacher_student_binding ADD COLUMN course_name VARCHAR(200) NULL COMMENT '课程名称' AFTER course_code;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teacher_student_binding' AND INDEX_NAME = 'idx_college_name') THEN
        ALTER TABLE teacher_student_binding ADD INDEX idx_college_name (college_name);
    END IF;
END$$
DELIMITER ;
CALL v21_add_display_fields();
DROP PROCEDURE IF EXISTS v21_add_display_fields;
