ALTER TABLE texts
    ADD COLUMN article_type ENUM('INNER', 'OUTER') DEFAULT 'OUTER' COMMENT '文章类型：课内教材课文/课外读物' AFTER unit_id,
    ADD COLUMN book_volume VARCHAR(50) DEFAULT NULL COMMENT '教材册号，例如综合教程1' AFTER article_type,
    ADD COLUMN unit_num INT DEFAULT NULL COMMENT '教材单元编号' AFTER book_volume;

CREATE INDEX idx_texts_article_filter ON texts (article_type, book_volume, unit_num);
