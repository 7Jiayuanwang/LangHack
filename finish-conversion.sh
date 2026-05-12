#!/bin/bash
# 完成剩余 4 个存储过程的转换

input="docker/mysql/init/04-apply-all-migrations-new.sql"
output="docker/mysql/init/04-apply-all-migrations-final.sql"

# 复制到第 467 行
sed -n '1,467p' "$input" > "$output"

# 替换 _add_idx_featured_priority (468-479)
cat >> "$output" << 'EOF'
-- 推荐优先级复合索引 [原 V10]
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'texts' AND index_name = 'idx_texts_featured_priority');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_texts_featured_priority ON texts (is_featured, featured_priority DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

EOF

# 复制 480-556
sed -n '480,556p' "$input" >> "$output"

# 替换 _add_col_media_paths (557-568)
cat >> "$output" << 'EOF'
-- 媒体表路径列 [原 V8]
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='file_path');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE media ADD COLUMN file_path VARCHAR(500) COMMENT ''stored media file path''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='media' AND column_name='thumbnail_path');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE media ADD COLUMN thumbnail_path VARCHAR(500) COMMENT ''stored thumbnail file path''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

EOF

# 复制 569-572
sed -n '569,572p' "$input" >> "$output"

# 替换 _add_col_sm2 (573-590)
cat >> "$output" << 'EOF'
-- SM-2 算法列 [原 V11]
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='repetition');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE vocabulary_books ADD COLUMN repetition INT DEFAULT 0 COMMENT ''SM-2 重复次数''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='interval_days');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE vocabulary_books ADD COLUMN interval_days INT DEFAULT 0 COMMENT ''SM-2 间隔天数''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='easiness_factor');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE vocabulary_books ADD COLUMN easiness_factor DOUBLE DEFAULT 2.5 COMMENT ''SM-2 简易因子''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema=DATABASE() AND table_name='vocabulary_books' AND column_name='next_review_time');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE vocabulary_books ADD COLUMN next_review_time DATETIME COMMENT ''SM-2 下次复习时间''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

EOF

# 复制 591-2485
sed -n '591,2485p' "$input" >> "$output"

# 替换 add_index_if_not_exists (2486-2514)
cat >> "$output" << 'EOF'
-- 幂等添加索引（V25 相似度矩阵索引）
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'text_similarity_matrix' AND index_name = 'idx_similar_text_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE text_similarity_matrix ADD INDEX idx_similar_text_id (similar_text_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'text_similarity_matrix_tmp' AND index_name = 'idx_similar_text_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE text_similarity_matrix_tmp ADD INDEX idx_similar_text_id (similar_text_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'user_similarity_matrix' AND index_name = 'idx_similar_user_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE user_similarity_matrix ADD INDEX idx_similar_user_id (similar_user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE table_schema = DATABASE() AND table_name = 'user_similarity_matrix_tmp' AND index_name = 'idx_similar_user_id');
SET @sql = IF(@idx_exists = 0, 'ALTER TABLE user_similarity_matrix_tmp ADD INDEX idx_similar_user_id (similar_user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

EOF

# 复制剩余部分（2515到文件末尾）
sed -n '2515,$p' "$input" >> "$output"

echo "转换完成: $output"
echo "验证无存储过程："
grep -c "DROP PROCEDURE" "$output" || echo "0 (成功)"
