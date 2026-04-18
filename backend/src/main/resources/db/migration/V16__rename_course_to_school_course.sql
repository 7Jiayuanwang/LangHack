-- ============================================================================
-- Flyway Migration Script: 重命名course为school_course匹配实体表名
-- Description: SchoolCourse实体@TableName("school_course")，但V9创建的表名是course
-- ============================================================================

-- 重命名course → school_course (如果course存在且school_course不存在)
-- 使用存储过程确保幂等性
DROP PROCEDURE IF EXISTS rename_course_to_school_course;
DELIMITER //
CREATE PROCEDURE rename_course_to_school_course()
BEGIN
    DECLARE course_exists INT DEFAULT 0;
    DECLARE school_course_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO course_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course';

    SELECT COUNT(*) INTO school_course_exists
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'school_course';

    IF course_exists = 1 AND school_course_exists = 0 THEN
        RENAME TABLE `course` TO `school_course`;
    ELSEIF course_exists = 0 AND school_course_exists = 0 THEN
        -- 全新环境（如CI），直接创建school_course表
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
    END IF;
END //
DELIMITER ;

CALL rename_course_to_school_course();
DROP PROCEDURE IF EXISTS rename_course_to_school_course;
