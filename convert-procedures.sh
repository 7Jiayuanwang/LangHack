#!/bin/bash
# 将存储过程中的 ALTER TABLE 提取为动态 SQL

input="docker/mysql/init/04-apply-all-migrations.sql"
output="docker/mysql/init/04-apply-all-migrations-new.sql"

# 复制文件头（到第一个存储过程之前）
sed -n '1,391p' "$input" > "$output"

# 添加 texts 表的 18 个列（动态 SQL 模式）
cat >> "$output" << 'EOF'
-- 2. 课文表扩展 — 统一批量添加所有列 [原 V2 + V10 + V17]
-- 改为动态 SQL 以避免存储过程限制
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN chinese_parallel_content TEXT COMMENT ''中英对照版本内容''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='markdown_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN markdown_content LONGTEXT COMMENT ''Markdown格式的课文内容'' AFTER content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='content_type');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN content_type ENUM(''TEXT'',''MARKDOWN'') DEFAULT ''TEXT'' COMMENT ''内容类型：TEXT-纯文本，MARKDOWN-Markdown格式'' AFTER markdown_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_markdown_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN cet4_markdown_content LONGTEXT COMMENT ''CET-4难度的Markdown版本'' AFTER chinese_parallel_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_markdown_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN cet6_markdown_content LONGTEXT COMMENT ''CET-6难度的Markdown版本'' AFTER cet4_markdown_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_markdown_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN academic_markdown_content LONGTEXT COMMENT ''学术版的Markdown版本'' AFTER cet6_markdown_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='chinese_parallel_markdown_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN chinese_parallel_markdown_content LONGTEXT COMMENT ''中英对照的Markdown版本'' AFTER academic_markdown_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_featured');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN is_featured TINYINT(1) DEFAULT 0 COMMENT ''是否推荐''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='featured_priority');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN featured_priority INT NOT NULL DEFAULT 0 COMMENT ''推荐优先级，值越大越靠前显示（0=默认）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet4_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN cet4_content LONGTEXT COMMENT ''CET-4难度版本内容'' AFTER content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='cet6_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN cet6_content LONGTEXT COMMENT ''CET-6难度版本内容'' AFTER cet4_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='academic_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN academic_content LONGTEXT COMMENT ''学术版版本内容'' AFTER cet6_content', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='uploaded_by');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN uploaded_by BIGINT DEFAULT NULL COMMENT ''上传者用户ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='tags');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN tags VARCHAR(500) DEFAULT NULL COMMENT ''标签，逗号分隔''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='view_count');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN view_count INT DEFAULT 0 COMMENT ''浏览次数''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='like_count');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN like_count INT DEFAULT 0 COMMENT ''点赞次数''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='is_public');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN is_public TINYINT(1) DEFAULT 1 COMMENT ''是否公开（0=否，1=是）''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='texts' AND column_name='status');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE texts ADD COLUMN status VARCHAR(20) DEFAULT ''PUBLISHED'' COMMENT ''状态：DRAFT/PUBLISHED/ARCHIVED''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

EOF

# 跳过原存储过程（392-461行），继续复制后续内容
sed -n '462,$p' "$input" >> "$output"

echo "转换完成: $output"
