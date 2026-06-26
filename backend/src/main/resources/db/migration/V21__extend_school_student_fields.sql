-- ═══════════════════════════════════════════════════════════════
-- V21: 扩展 school_student 字段长度
-- 修复 2026-06-22 生产日志中的 DataIntegrityViolationException:
--   - counselor_name VARCHAR(50) → VARCHAR(100)  (2808 次超长错误)
--   - phone VARCHAR(50) → VARCHAR(100)          (32 次超长错误)
--
-- 根因分析:
--   1. phone 字段注释说"CDSP 返回 MD5 哈希 32 字符"，但实际可能返回
--      更长的格式（如带分隔符的多个号码："13800138000,18900189000"）
--   2. counselor_name 可能返回多个辅导员姓名拼接（"张三,李四"）
--      或包含职务信息（"张三（兼职辅导员）"）
--
-- 修复策略:
--   - phone: 50 → 100 (容纳多个号码/格式)
--   - counselor_name: 50 → 100 (容纳复合姓名)
-- ═══════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS _v21_extend_student_fields;
CREATE PROCEDURE _v21_extend_student_fields()
BEGIN
    -- 扩展 phone 字段长度
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'phone'
                 AND CHARACTER_MAXIMUM_LENGTH = 50) THEN
        ALTER TABLE school_student
            MODIFY COLUMN phone VARCHAR(100) NULL COMMENT '联系电话（CDSP 可能返回多个号码拼接）';
    END IF;

    -- 扩展 counselor_name 字段长度
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'school_student'
                 AND COLUMN_NAME = 'counselor_name'
                 AND CHARACTER_MAXIMUM_LENGTH = 50) THEN
        ALTER TABLE school_student
            MODIFY COLUMN counselor_name VARCHAR(100) NULL COMMENT '辅导员姓名（可能包含多个辅导员或职务）';
    END IF;
END;

CALL _v21_extend_student_fields();
DROP PROCEDURE IF EXISTS _v21_extend_student_fields;
