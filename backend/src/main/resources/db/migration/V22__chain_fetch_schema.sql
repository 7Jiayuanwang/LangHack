-- ============================================================================
-- Flyway Migration Script: 按学院链式拉取数据模型
-- Version: V22
-- Description: 新建 school_student / school_teacher / teacher_student_binding 三张表
--              + feature toggle + college_chain cron 配置 seed
--              用于支撑"外国语学院 → 课程 → 选课 → 学生/教师"链式拉取落库
--              与 SSO 自动授权、教师-学生绑定查询等下游场景
-- Author: System
-- Date: 2026-04-22
-- ============================================================================

-- ============================================================================
-- 1. 学生主数据（CDSP 链式拉取产物）
-- ============================================================================
CREATE TABLE IF NOT EXISTS school_student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    student_id VARCHAR(50) NOT NULL UNIQUE COMMENT '学号（对应 CDSP xh / XH）',
    student_name VARCHAR(100) COMMENT '学生姓名（SSO attributes 或手工补全）',
    college_name VARCHAR(100) NOT NULL COMMENT '学院名称（CDSP kkyx）',
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

-- ============================================================================
-- 2. 教师主数据（CDSP 链式拉取产物）
-- ============================================================================
CREATE TABLE IF NOT EXISTS school_teacher (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    teacher_id VARCHAR(50) NOT NULL UNIQUE COMMENT '工号（CDSP rkjsh / skjszgh）',
    teacher_name VARCHAR(100) COMMENT '教师姓名（CDSP rkjsxm）',
    college_name VARCHAR(100) NOT NULL COMMENT '学院（来自链式触发学院）',
    college_code VARCHAR(50) COMMENT '学院代码（预留）',
    sync_time DATETIME NOT NULL COMMENT '同步时间',
    last_schedule_sync_time DATETIME COMMENT '上次课表同步时间',
    sync_source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_API' COMMENT '同步来源',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_college (college_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CDSP 同步的教师主数据';

-- ============================================================================
-- 3. 教师-学生绑定（链式拉取衍生关联）
-- ============================================================================
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

-- ============================================================================
-- 4. Feature Toggle / 学院链式配置 seed
-- ============================================================================
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'ops.school_data_chain_fetch', 'false', '运维端按学院链式拉取功能开关', 0),
('feature_toggle', 'student.lazy_data_sync', 'true', '学生端按需 Lazy 同步四六级/课表', 0),
('feature_toggle', 'sso.auto_grant_student', 'false', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'false', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0),
('school_data', 'sync_college_chain_cron', '0 30 3 * * ?', '按学院链式刷新 cron 表达式', 0),
('school_data', 'sync_college_chain_colleges', '外国语学院', '链式刷新白名单（逗号分隔学院名）', 0),
('school_data', 'sync_college_chain_match_mode', 'eq', '学院名匹配模式（eq=精确 / contains=包含）', 0);

-- ============================================================================
-- 5. SSO 角色映射补充：ou=People（IMAU CAS 回退 containerId 格式）
--   CAS 不返回 memberOf 时回退使用 containerId，值如 ou=2000001,ou=People
--   ou=People 是全校师生通用组，默认映射为 STUDENT（校园环境大多数人是学生）
--   英文 key 含 '=' 无法写入 YAML default-rules（Spring Boot 吞 '='），故用数据库
-- ============================================================================
INSERT IGNORE INTO sso_config (config_key, config_value, config_type, remark) VALUES
('sso.role_mapping.ou=People', 'STUDENT', 'ROLE_MAPPING', 'CAS containerId 通用组 → STUDENT（大多数人是学生）');

-- ============================================================================
-- 6. level_exam_scores 修复：student_name 可为空 + 字段映射补全
--   CDSP 等级考试成绩端点不返回学生姓名，仅返回 学号/考试科目/考试日期/分数
--   原 student_id NOT NULL + student_name NOT NULL 导致 100% INSERT 失败
-- ============================================================================
ALTER TABLE level_exam_scores MODIFY COLUMN student_name VARCHAR(100) COMMENT '学生姓名（CDSP 不提供，可为空）';
ALTER TABLE level_exam_scores MODIFY COLUMN exam_type VARCHAR(50) COMMENT '考试类型（CDSP 不提供，从 examName 推断）';
ALTER TABLE level_exam_scores MODIFY COLUMN is_passed BOOLEAN COMMENT '是否通过（CDSP 不提供，从 score 推断）';

-- 补充 xm → studentName 字段映射（CDSP 未来可能返回姓名字段时自动生效）
INSERT IGNORE INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('level_exam_scores', 'xm', 'studentName', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学生姓名（CDSP 可能不返回）');
