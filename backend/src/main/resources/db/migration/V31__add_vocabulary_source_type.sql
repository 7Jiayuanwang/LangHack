-- 生词本来源标记：ARTICLE=文章阅读加入 / MANUAL=手动添加或其他来源
ALTER TABLE vocabulary_books
ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
COMMENT '来源类型：ARTICLE-文章阅读，MANUAL-手动添加或其他'
AFTER next_review_time;

-- 已有历史数据全部标记为 MANUAL（旧数据无法确定来源）
UPDATE vocabulary_books SET source_type = 'MANUAL' WHERE source_type IS NULL OR source_type = '';

-- 给 student_id + source_type 加索引，提升按来源查询的性能
CREATE INDEX idx_vocabulary_source ON vocabulary_books(student_id, source_type);