-- ============================================================================
-- Flyway Migration V25 — 相似度矩阵补 similar_*_id 索引
-- ============================================================================
-- 背景：V24 只给了 text_id/user_id 单向索引，双向查询时 WHERE similar_text_id = X
-- 会走全表扫描。Item-CF / User-CF 在服务层按两侧分别查，没有索引代价放大 ~2 倍。
-- 同步给 _tmp 表补上，保持 Shadow Table 两边结构一致。
-- ============================================================================

USE `english_learning`;

-- 幂等添加索引（如果索引已存在则跳过）
DELIMITER $$

CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_column_name VARCHAR(64)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = p_table_name
      AND index_name = p_index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD INDEX `', p_index_name, '` (`', p_column_name, '`)');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_index_if_not_exists('text_similarity_matrix', 'idx_similar_text_id', 'similar_text_id');
CALL add_index_if_not_exists('text_similarity_matrix_tmp', 'idx_similar_text_id', 'similar_text_id');
CALL add_index_if_not_exists('user_similarity_matrix', 'idx_similar_user_id', 'similar_user_id');
CALL add_index_if_not_exists('user_similarity_matrix_tmp', 'idx_similar_user_id', 'similar_user_id');

DROP PROCEDURE IF EXISTS add_index_if_not_exists;
