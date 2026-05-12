-- V21: 为 teacher_student_binding 表添加显示字段（学院、姓名、课程名称）
-- 用于前端管理界面直接展示，避免多表 JOIN 查询

ALTER TABLE teacher_student_binding
    ADD COLUMN college_name VARCHAR(100) NULL COMMENT '学院名称' AFTER student_id,
    ADD COLUMN teacher_name VARCHAR(100) NULL COMMENT '教师姓名' AFTER teacher_id,
    ADD COLUMN student_name VARCHAR(100) NULL COMMENT '学生姓名' AFTER student_id,
    ADD COLUMN course_name VARCHAR(200) NULL COMMENT '课程名称' AFTER course_code;

-- 为学院名称添加索引，支持按学院筛选
CREATE INDEX idx_college_name ON teacher_student_binding(college_name);
