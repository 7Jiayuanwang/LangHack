-- 给 texts 表添加新字段
USE english_learning;

-- 添加内容类型字段（ARTICLE-课文, EXERCISE-练习题）
ALTER TABLE texts
ADD COLUMN content_type VARCHAR(20) DEFAULT 'ARTICLE' COMMENT '内容类型：ARTICLE-课文，EXERCISE-练习题' AFTER difficulty;

-- 添加排序字段
ALTER TABLE texts
ADD COLUMN order_num INT DEFAULT 1 COMMENT '排序序号' AFTER content_type;

-- 查看修改后的表结构
DESC texts;

-- 验证数据
SELECT id, title, content_type, order_num FROM texts LIMIT 5;
