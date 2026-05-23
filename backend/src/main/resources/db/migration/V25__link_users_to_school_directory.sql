-- V25: 将 users 与 school_student / school_teacher 主数据建立显式联系
--
-- 背景:
--   现有约定为 users.username = school_student.student_id (或 school_teacher.teacher_id)，
--   仅在 SSO 登录路径上隐式生效。本迁移做三件事：
--     1. 规范化历史 student_<学号> 形式的 username，使其能直接命中主数据
--     2. 在 users 上新增明确外键列 school_student_id / school_teacher_id，并回填
--     3. 创建视图 v_user_school_profile，对外聚合学生的学院 / 班级 / 成绩 / 课表 / 师生绑定

-- 1) 规范化 username：student_<digits> -> <digits>
--    （前置确认无冲突；如运行时仍冲突，UPDATE 会因 UNIQUE 报错并整体回滚）
UPDATE users
SET username = SUBSTRING(username, 9)
WHERE username REGEXP '^student_[0-9]+$';

-- 2) 新增外键列
ALTER TABLE users
    ADD COLUMN school_student_id BIGINT NULL COMMENT '关联 school_student.id（学号匹配）' AFTER inner_uid,
    ADD COLUMN school_teacher_id BIGINT NULL COMMENT '关联 school_teacher.id（工号匹配）' AFTER school_student_id;

CREATE INDEX idx_users_school_student_id ON users(school_student_id);
CREATE INDEX idx_users_school_teacher_id ON users(school_teacher_id);

-- 3) 回填外键
UPDATE users u
JOIN school_student s ON s.student_id = u.username
SET u.school_student_id = s.id
WHERE u.school_student_id IS NULL;

UPDATE users u
JOIN school_teacher t ON t.teacher_id = u.username
SET u.school_teacher_id = t.id
WHERE u.school_teacher_id IS NULL;

-- 4) 视图：聚合学生的学校画像（学院 / 班级 / 选课数 / 最新成绩 / 师生绑定数）
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
