-- 检查 texts 表结构
USE english_learning;

-- 查看表结构
DESC texts;

-- 查看当前数据
SELECT id, title, content_type, order_num, unit_id FROM texts;

-- 如果字段不存在，执行以下语句添加
-- ALTER TABLE texts ADD COLUMN content_type VARCHAR(20) DEFAULT 'ARTICLE' AFTER difficulty;
-- ALTER TABLE texts ADD COLUMN order_num INT DEFAULT 1 AFTER content_type;
