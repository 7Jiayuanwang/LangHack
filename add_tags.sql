-- 插入测试用标签
INSERT INTO content_tags (tag_name, tag_color, sort_order, deleted) VALUES
('日常对话', '#409EFF', 1, 0),
('科技文章', '#67C23A', 2, 0),
('新闻时事', '#E6A23C', 3, 0),
('文学作品', '#F56C6C', 4, 0),
('学术论文', '#909399', 5, 0)
ON DUPLICATE KEY UPDATE tag_name=VALUES(tag_name);
