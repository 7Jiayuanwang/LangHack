-- V32__extend_school_student_varchar.sql
-- 扩展 school_student 表 VARCHAR 字段长度
-- 背景: CDSP student_basic_info API 返回的 counselor_name / phone 可能是多人/多号码拼接，
--       V12 建表时为 VARCHAR(50)，V21 扩展到 VARCHAR(100)，但仍不够。
--       V32 安全扩展到 VARCHAR(500)，使用存储过程避免重复执行报错。
-- 创建日期: 2026-06-30

DROP PROCEDURE IF EXISTS _v32_extend_student_fields;
CREATE PROCEDURE _v32_extend_student_fields()
BEGIN
    -- counselor_name: CDSP 返回逗号分隔的辅导员列表，典型值如:
    --   "王明玖,石凤翎,韩国栋,娜日苏,云岚,赵金花,付和平,王成杰,王忠武,赵彦,王占义,袁帅,康萨如拉,何欢"
    -- 14 个姓名 ≈ 60 字符，极端情况可能更多，VARCHAR(500) 足够安全
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'counselor_name'
                 AND CHARACTER_MAXIMUM_LENGTH < 500) THEN
        ALTER TABLE school_student
            MODIFY COLUMN counselor_name VARCHAR(500) NULL
            COMMENT '辅导员姓名（CDSP 可能返回逗号分隔的多位辅导员）';
    END IF;

    -- phone: CDSP 现返回 SHA-256 哈希（64 字符），远超过 V12 建表的 VARCHAR(50)。
    -- V21 扩至 VARCHAR(100)，V32 扩至 VARCHAR(255) 以覆盖任意哈希长度。
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'phone'
                 AND CHARACTER_MAXIMUM_LENGTH < 255) THEN
        ALTER TABLE school_student
            MODIFY COLUMN phone VARCHAR(255) NULL
            COMMENT '联系电话（CDSP 返回加密哈希：MD5 32字符 / SHA-256 64字符）';
    END IF;
END;
CALL _v32_extend_student_fields();
DROP PROCEDURE IF EXISTS _v32_extend_student_fields;
