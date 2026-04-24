-- ============================================================================
-- Flyway Migration V14 — 相似度矩阵补 similar_*_id 索引
-- ============================================================================
-- 背景：V12 只给了 text_id/user_id 单向索引，双向查询时 WHERE similar_text_id = X
-- 会走全表扫描。Item-CF / User-CF 在服务层按两侧分别查，没有索引代价放大 ~2 倍。
-- 同步给 _tmp 表补上，保持 Shadow Table 两边结构一致。
-- ============================================================================

USE `english_learning`;

ALTER TABLE `text_similarity_matrix`      ADD INDEX `idx_similar_text_id` (`similar_text_id`);
ALTER TABLE `text_similarity_matrix_tmp`  ADD INDEX `idx_similar_text_id` (`similar_text_id`);
ALTER TABLE `user_similarity_matrix`      ADD INDEX `idx_similar_user_id` (`similar_user_id`);
ALTER TABLE `user_similarity_matrix_tmp`  ADD INDEX `idx_similar_user_id` (`similar_user_id`);
