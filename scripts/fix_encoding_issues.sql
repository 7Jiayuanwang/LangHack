-- ═══════════════════════════════════════════════════════════════
-- SQL 脚本乱码修复补丁
-- 生成日期: 2026-05-05
-- 用途: 修复 V12/V13/V14 迁移脚本中的字符编码问题
--
-- 使用方法:
--   1. 备份数据库: mysqldump -u root -p english_learning > backup.sql
--   2. 执行此脚本: mysql -u root -p english_learning < fix_encoding_issues.sql
--   3. 验证修复: SELECT * FROM field_mappings WHERE remark LIKE '%????%';
-- ═══════════════════════════════════════════════════════════════

USE english_learning;
SET NAMES utf8mb4;

-- ═══════════════════════════════════════════════════════════════
-- Part 1: 修复 V12 field_mappings 乱码
-- ═══════════════════════════════════════════════════════════════

-- 1.1 删除乱码的 field_mappings 记录
DELETE FROM field_mappings
WHERE data_source = 'administrative_class'
  AND (source_field LIKE '%?%' OR remark LIKE '%?%');

-- 1.2 重新插入正确的字段映射（学院代码相关）
INSERT INTO field_mappings (data_source, source_field, target_field, source_type, target_type, converter, converter_config, default_value, is_required, sort_order, is_active, remark) VALUES
('administrative_class', 'YXDM',       'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（英文缩写）'),
('administrative_class', '学院代码',   'college_code', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 4, 1, '学院代码（中文）'),
('administrative_class', 'YXMC',       'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（英文缩写）'),
('administrative_class', '学院名称',   'college_name', 'STRING', 'STRING', 'NONE', NULL, NULL, 0, 5, 1, '学院名称（中文）')
ON DUPLICATE KEY UPDATE
  remark = VALUES(remark),
  is_active = VALUES(is_active);

-- ═══════════════════════════════════════════════════════════════
-- Part 2: 修复 V13 system_config 功能开关描述乱码
-- ═══════════════════════════════════════════════════════════════

-- 2.1 修复学生端 Lazy 选课记录同步开关描述
UPDATE `system_config`
SET `description` = '学生端 Lazy 选课记录同步（陈旧度阈值 1 天）'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'student.lazy_course_selections'
  AND (`description` LIKE '%?%' OR `description` = '');

-- 2.2 修复教师端学校数据功能开关描述
UPDATE `system_config`
SET `description` = '教师端学校数据功能（课表/选课/成绩）'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` = 'teacher.school_data_enabled'
  AND (`description` LIKE '%?%' OR `description` = '');

-- 2.3 如果记录不存在，则插入（幂等）
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'student.lazy_course_selections', 'true', '学生端 Lazy 选课记录同步（陈旧度阈值 1 天）', 0),
('feature_toggle', 'teacher.school_data_enabled', 'true', '教师端学校数据功能（课表/选课/成绩）', 0);

-- ═══════════════════════════════════════════════════════════════
-- Part 3: 验证修复结果
-- ═══════════════════════════════════════════════════════════════

-- 3.1 检查是否还有乱码的 field_mappings
SELECT
    '检查 field_mappings 乱码' AS check_item,
    COUNT(*) AS issue_count,
    CASE WHEN COUNT(*) = 0 THEN '✅ 通过' ELSE '❌ 仍有问题' END AS status
FROM field_mappings
WHERE source_field LIKE '%?%' OR remark LIKE '%?%';

-- 3.2 检查是否还有乱码的 system_config
SELECT
    '检查 system_config 乱码' AS check_item,
    COUNT(*) AS issue_count,
    CASE WHEN COUNT(*) = 0 THEN '✅ 通过' ELSE '❌ 仍有问题' END AS status
FROM system_config
WHERE description LIKE '%?%';

-- 3.3 显示修复后的 field_mappings（学院相关）
SELECT
    data_source,
    source_field,
    target_field,
    remark,
    is_active
FROM field_mappings
WHERE data_source = 'administrative_class'
  AND target_field IN ('college_code', 'college_name')
ORDER BY sort_order;

-- 3.4 显示修复后的 system_config（功能开关）
SELECT
    config_group,
    config_key,
    config_value,
    description
FROM system_config
WHERE config_key IN ('student.lazy_course_selections', 'teacher.school_data_enabled');

-- ═══════════════════════════════════════════════════════════════
-- Part 4: 修复建议和后续行动
-- ═══════════════════════════════════════════════════════════════

/*
修复完成后的后续行动：

1. 【立即】验证学校数据同步功能
   - 测试行政班数据同步：POST /api/admin/sync/administrative-class
   - 检查字段映射是否正确应用
   - 验证中文字段名和英文缩写都能正确识别

2. 【立即】验证功能开关显示
   - 登录运维面板：/ops/feature-toggles
   - 检查功能开关描述是否正确显示
   - 确认无乱码

3. 【短期】修复源文件编码
   - 使用 UTF-8 编码重新保存以下文件：
     * backend/src/main/resources/db/migration/V12__school_data_and_videos.sql
     * backend/src/main/resources/db/migration/V13__feature_toggles_and_ai_config.sql
     * backend/src/main/resources/db/migration/V14__enhancements.sql
   - 同步更新 docker/mysql/init/04-apply-all-migrations.sql

4. 【短期】配置 Git 正确处理 UTF-8
   git config core.quotepath false
   git config core.autocrlf false
   echo "*.sql text eol=lf encoding=utf-8" >> .gitattributes

5. 【中期】添加 CI 检查
   - 在 GitHub Actions 中添加 SQL 文件编码检查
   - 使用 file -i 或 iconv 验证所有 SQL 文件为 UTF-8

6. 【长期】文档更新
   - 更新 docs/DATABASE_COLLABORATION_GUIDE.md
   - 添加 SQL 文件编码规范
   - 记录此次问题和修复方案到 memory/lessons-learned.md
*/

-- ═══════════════════════════════════════════════════════════════
-- 修复完成
-- ═══════════════════════════════════════════════════════════════

SELECT '✅ SQL 脚本乱码修复补丁执行完成' AS status,
       '请查看上方验证结果，确认所有检查项都通过' AS next_step;
