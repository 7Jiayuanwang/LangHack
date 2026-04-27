-- ============================================================================
-- 学校数据库 ETL 视图创建脚本
-- ============================================================================
-- 用途：为 LangHack 系统提供标准化的数据视图
-- 前提：需要学校数据库管理员在学校数据库中执行此脚本
-- 注意：此脚本中的字段名和表名需要根据学校数据库的实际结构调整
-- ============================================================================

USE school_db;

-- ----------------------------------------------------------------------------
-- 1. 行政班基本信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供行政班的基本信息，包括班级名称、学院、年级等
-- 对应表：需要学校提供实际的行政班表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_administrative_class AS
SELECT
    BJDM AS class_id,              -- 班级代码（主键）
    BJMC AS class_name,            -- 班级名称
    XSH AS college_code,           -- 学院代码
    XYMC AS college_name,          -- 学院名称
    NJ AS grade,                   -- 年级
    BJRS AS student_count,         -- 班级人数
    BJZT AS status,                -- 班级状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_administrative_class        -- ← 替换为实际的行政班表名
WHERE BJZT = '1';                  -- 只查询有效班级

-- 创建索引以提升查询性能
CREATE INDEX idx_admin_class_updated ON t_administrative_class(GXSJ);


-- ----------------------------------------------------------------------------
-- 2. 课程基本信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供课程的基本信息，包括课程代码、课程名称、开课学院等
-- 对应表：需要学校提供实际的课程表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_school_course AS
SELECT
    KCDM AS course_code,           -- 课程代码（主键）
    KCMC AS course_name,           -- 课程名称
    KKYX AS college_name,          -- 开课学院
    XF AS credits,                 -- 学分
    KCXZ AS course_type,           -- 课程性质
    KCZT AS status,                -- 课程状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_school_course               -- ← 替换为实际的课程表名
WHERE KCZT = '1';                  -- 只查询有效课程

CREATE INDEX idx_school_course_updated ON t_school_course(GXSJ);


-- ----------------------------------------------------------------------------
-- 3. 教学班信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供教学班信息，包括教学班号、课程代码、教师等
-- 对应表：需要学校提供实际的教学班表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_teaching_class AS
SELECT
    JXBID AS class_id,             -- 教学班号（主键）
    KCDM AS course_code,           -- 课程代码
    KCMC AS course_name,           -- 课程名称
    RKJSH AS teacher_id,           -- 任课教师工号
    JSXM AS teacher_name,          -- 教师姓名
    XN AS academic_year,           -- 学年
    XQ AS semester,                -- 学期
    KKYX AS college_name,          -- 开课学院
    JXBRS AS student_count,        -- 教学班人数
    JXBZT AS status,               -- 教学班状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_teaching_class              -- ← 替换为实际的教学班表名
WHERE JXBZT = '1';                 -- 只查询有效教学班

CREATE INDEX idx_teaching_class_updated ON t_teaching_class(GXSJ);


-- ----------------------------------------------------------------------------
-- 4. 课程安排信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供课表信息，包括上课时间、地点等
-- 对应表：需要学校提供实际的课表表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_course_schedule AS
SELECT
    KBID AS schedule_id,           -- 课表ID（主键）
    JXBID AS class_id,             -- 教学班号
    KCDM AS course_code,           -- 课程代码
    KCMC AS course_name,           -- 课程名称
    RKJSH AS teacher_id,           -- 教师工号
    JSXM AS teacher_name,          -- 教师姓名
    XN AS academic_year,           -- 学年
    XQ AS semester,                -- 学期
    SKXQJ AS day_of_week,          -- 星期几（1-7）
    SKJC AS period,                -- 上课节次
    SKZC AS week_range,            -- 上课周次
    SKDD AS location,              -- 上课地点
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_course_schedule             -- ← 替换为实际的课表表名
WHERE 1=1;                         -- 查询所有课表

CREATE INDEX idx_course_schedule_updated ON t_course_schedule(GXSJ);


-- ----------------------------------------------------------------------------
-- 5. 学生选课记录视图
-- ----------------------------------------------------------------------------
-- 说明：提供学生选课信息，包括学号、课程班号等
-- 对应表：需要学校提供实际的选课表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_course_selection AS
SELECT
    XKID AS selection_id,          -- 选课ID（主键）
    XH AS student_id,              -- 学号
    XSXM AS student_name,          -- 学生姓名
    KCBH AS course_class_code,     -- 课程班号
    KCDM AS course_code,           -- 课程代码
    KCMC AS course_name,           -- 课程名称
    XN AS academic_year,           -- 学年
    XQ AS semester,                -- 学期
    XF AS credits,                 -- 学分
    XKZT AS status,                -- 选课状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_course_selection            -- ← 替换为实际的选课表名
WHERE XKZT IN ('1', '2');          -- 只查询有效选课记录

CREATE INDEX idx_course_selection_updated ON t_course_selection(GXSJ);


-- ----------------------------------------------------------------------------
-- 6. 等级考试成绩视图
-- ----------------------------------------------------------------------------
-- 说明：提供学生等级考试成绩，如四六级成绩
-- 对应表：需要学校提供实际的等级考试成绩表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_level_exam_scores AS
SELECT
    CJID AS score_id,              -- 成绩ID（主键）
    XH AS student_id,              -- 学号
    XSXM AS student_name,          -- 学生姓名
    KSMC AS exam_name,             -- 考试名称（如"CET4"、"CET6"）
    KSSJ AS exam_date,             -- 考试时间
    ZKZH AS admission_ticket,      -- 准考证号
    CJ AS score,                   -- 成绩
    DJDJ AS level,                 -- 等级
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_level_exam_scores           -- ← 替换为实际的等级考试成绩表名
WHERE 1=1;                         -- 查询所有成绩

CREATE INDEX idx_level_exam_updated ON t_level_exam_scores(GXSJ);


-- ----------------------------------------------------------------------------
-- 7. 教学计划信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供教学计划信息，包括专业、年级、培养方案等
-- 对应表：需要学校提供实际的教学计划表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_teaching_plans AS
SELECT
    JHJHID AS plan_id,             -- 教学计划ID（主键）
    ZYMC AS major_name,            -- 专业名称
    NJ AS grade,                   -- 年级
    PYFAMC AS plan_name,           -- 培养方案名称
    JHZT AS status,                -- 计划状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_teaching_plans              -- ← 替换为实际的教学计划表名
WHERE JHZT = '1';                  -- 只查询有效计划

CREATE INDEX idx_teaching_plans_updated ON t_teaching_plans(GXSJ);


-- ----------------------------------------------------------------------------
-- 8. 教师授课信息视图
-- ----------------------------------------------------------------------------
-- 说明：提供教师授课信息，包括教师工号、课程代码、教学班号等
-- 对应表：需要学校提供实际的教师授课表名
-- ----------------------------------------------------------------------------

CREATE OR REPLACE VIEW v_teacher_courses AS
SELECT
    SKID AS teaching_id,           -- 授课ID（主键）
    RKJSH AS teacher_id,           -- 教师工号
    JSXM AS teacher_name,          -- 教师姓名
    KCDM AS course_code,           -- 课程代码
    KCMC AS course_name,           -- 课程名称
    KCBH AS class_id,              -- 课程班号（教学班号）
    XN AS academic_year,           -- 学年
    XQ AS semester,                -- 学期
    KKYX AS college_name,          -- 开课学院
    SKZT AS status,                -- 授课状态
    GXSJ AS updated_at,            -- 更新时间（必须字段）
    CJSJ AS created_at             -- 创建时间
FROM t_teacher_courses             -- ← 替换为实际的教师授课表名
WHERE SKZT = '1';                  -- 只查询有效授课记录

CREATE INDEX idx_teacher_courses_updated ON t_teacher_courses(GXSJ);


-- ============================================================================
-- 授权脚本
-- ============================================================================
-- 说明：为 LangHack 系统创建只读用户并授予视图查询权限
-- 注意：请根据实际情况修改用户名、密码和允许的主机
-- ============================================================================

-- 创建只读用户（请修改密码）
CREATE USER IF NOT EXISTS 'langhack_readonly'@'%' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';

-- 授予视图查询权限
GRANT SELECT ON school_db.v_administrative_class TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_school_course TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teaching_class TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_course_schedule TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_course_selection TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_level_exam_scores TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teaching_plans TO 'langhack_readonly'@'%';
GRANT SELECT ON school_db.v_teacher_courses TO 'langhack_readonly'@'%';

-- 刷新权限
FLUSH PRIVILEGES;

-- 验证权限
SHOW GRANTS FOR 'langhack_readonly'@'%';


-- ============================================================================
-- 测试查询
-- ============================================================================
-- 说明：验证视图是否创建成功并返回数据
-- ============================================================================

-- 测试行政班视图
SELECT COUNT(*) AS total_classes FROM v_administrative_class;
SELECT * FROM v_administrative_class LIMIT 5;

-- 测试课程视图
SELECT COUNT(*) AS total_courses FROM v_school_course;
SELECT * FROM v_school_course LIMIT 5;

-- 测试教学班视图
SELECT COUNT(*) AS total_teaching_classes FROM v_teaching_class;
SELECT * FROM v_teaching_class LIMIT 5;

-- 测试课表视图
SELECT COUNT(*) AS total_schedules FROM v_course_schedule;
SELECT * FROM v_course_schedule LIMIT 5;

-- 测试选课视图
SELECT COUNT(*) AS total_selections FROM v_course_selection;
SELECT * FROM v_course_selection LIMIT 5;

-- 测试等级考试成绩视图
SELECT COUNT(*) AS total_exam_scores FROM v_level_exam_scores;
SELECT * FROM v_level_exam_scores LIMIT 5;

-- 测试教学计划视图
SELECT COUNT(*) AS total_plans FROM v_teaching_plans;
SELECT * FROM v_teaching_plans LIMIT 5;

-- 测试教师授课视图
SELECT COUNT(*) AS total_teacher_courses FROM v_teacher_courses;
SELECT * FROM v_teacher_courses LIMIT 5;


-- ============================================================================
-- 注意事项
-- ============================================================================
-- 1. 此脚本中的表名（t_*）和字段名（如 BJDM、BJMC）是示例，需要根据学校
--    数据库的实际结构进行调整
-- 2. 每个视图必须包含 updated_at 字段，用于增量同步
-- 3. 如果学校数据库表没有更新时间字段，需要添加或使用其他时间字段
-- 4. 建议在学校数据库的更新时间字段上创建索引以提升查询性能
-- 5. 只读用户的密码必须修改为强密码
-- 6. 根据安全要求，可以将 '%' 改为具体的 IP 地址或主机名
-- ============================================================================
