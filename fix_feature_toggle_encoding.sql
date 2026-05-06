-- 修复功能开关中文描述乱码问题
-- 问题：数据库连接使用 utf8 而表定义使用 utf8mb4，导致中文字符编码错误
-- 解决：重新插入正确的中文描述

USE english_learning;

-- 备份当前数据（可选）
-- CREATE TABLE system_config_backup_20260505 AS SELECT * FROM system_config WHERE config_group = 'feature_toggle';

-- 修复学生端页面开关描述
UPDATE system_config SET description = '学生端-课程学习页（开发中）' WHERE config_group = 'feature_toggle' AND config_key = 'page_courses';
UPDATE system_config SET description = '学生端-等级考试成绩页' WHERE config_group = 'feature_toggle' AND config_key = 'page_scores';
UPDATE system_config SET description = '学生端-我的选课页' WHERE config_group = 'feature_toggle' AND config_key = 'page_course_selections';
UPDATE system_config SET description = '学生端-课文学习子页' WHERE config_group = 'feature_toggle' AND config_key = 'page_text_learning';
UPDATE system_config SET description = '学生端-我的课表页' WHERE config_group = 'feature_toggle' AND config_key = 'page_schedule';
UPDATE system_config SET description = '学生端-视频学习页' WHERE config_group = 'feature_toggle' AND config_key = 'page_videos';
UPDATE system_config SET description = '学生端-视频播放器页' WHERE config_group = 'feature_toggle' AND config_key = 'page_video_player';
UPDATE system_config SET description = '学生端-交互式课文页' WHERE config_group = 'feature_toggle' AND config_key = 'page_interactive_text';
UPDATE system_config SET description = '学生端-词汇学习页' WHERE config_group = 'feature_toggle' AND config_key = 'page_vocabulary';
UPDATE system_config SET description = '学生端-AI问答页' WHERE config_group = 'feature_toggle' AND config_key = 'page_ai_qa';
UPDATE system_config SET description = '学生端-文章阅读页' WHERE config_group = 'feature_toggle' AND config_key = 'page_articles';
UPDATE system_config SET description = '学生端-我的课文页' WHERE config_group = 'feature_toggle' AND config_key = 'page_user_texts';

-- 修复教师端页面开关描述
UPDATE system_config SET description = '教师端-仪表盘' WHERE config_group = 'feature_toggle' AND config_key = 'page_teacher_dashboard';
UPDATE system_config SET description = '教师端-课程管理' WHERE config_group = 'feature_toggle' AND config_key = 'page_teacher_courses';
UPDATE system_config SET description = '教师端-我的课程' WHERE config_group = 'feature_toggle' AND config_key = 'page_teacher_my_courses';
UPDATE system_config SET description = '教师端-班级管理' WHERE config_group = 'feature_toggle' AND config_key = 'page_teacher_classes';

-- 修复管理员模块开关描述
UPDATE system_config SET description = '管理员-仪表盘' WHERE config_group = 'feature_toggle' AND config_key = 'admin.dashboard';
UPDATE system_config SET description = '管理员-课程管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.courses';
UPDATE system_config SET description = '管理员-班级管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.classes';
UPDATE system_config SET description = '管理员-用户管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.users';
UPDATE system_config SET description = '管理员-单元管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.units';
UPDATE system_config SET description = '管理员-单词管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.words';
UPDATE system_config SET description = '管理员-课文管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.texts';
UPDATE system_config SET description = '管理员-作业管理' WHERE config_group = 'feature_toggle' AND config_key = 'admin.assignments';
UPDATE system_config SET description = '管理员-SSO 审批' WHERE config_group = 'feature_toggle' AND config_key = 'admin.sso_approval';

-- 修复运维端功能开关描述
UPDATE system_config SET description = '运维端学校数据浏览功能（行政班、课程、教学班等）' WHERE config_group = 'feature_toggle' AND config_key = 'ops.school_data_browser';
UPDATE system_config SET description = '运维端学校数据源配置功能（API/ETL切换、连接测试）' WHERE config_group = 'feature_toggle' AND config_key = 'ops.school_data_config';

-- 验证修复结果
SELECT config_key, description, CHAR_LENGTH(description) as len, LENGTH(description) as bytes
FROM system_config
WHERE config_group = 'feature_toggle'
ORDER BY config_key;
