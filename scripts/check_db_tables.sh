#!/bin/bash
# 检查数据库缺失表和关键字段
# 用法: bash check_db_tables.sh

CONTAINER="langhack-mysql-master"
DB="english_learning"

echo "=============================="
echo " 数据库完整性检查"
echo "=============================="

docker exec "$CONTAINER" bash -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" $DB" <<'SQL'

-- 1. 检查缺失的表
SELECT '【缺失的表】' AS '';
SELECT t.expected_table AS '缺失表名'
FROM (
  SELECT 'access_log' AS expected_table UNION ALL
  SELECT 'access_stats_daily' UNION ALL SELECT 'access_stats_hourly' UNION ALL
  SELECT 'administrative_class' UNION ALL SELECT 'ai_call_log' UNION ALL
  SELECT 'ai_call_stats_daily' UNION ALL SELECT 'ai_conversations' UNION ALL
  SELECT 'ai_feedback' UNION ALL SELECT 'ai_messages' UNION ALL
  SELECT 'ai_provider' UNION ALL SELECT 'ai_quiz_session' UNION ALL
  SELECT 'annotation_upvotes' UNION ALL SELECT 'article_favorites' UNION ALL
  SELECT 'article_likes' UNION ALL SELECT 'assignments' UNION ALL
  SELECT 'backup_config' UNION ALL SELECT 'backup_operation_logs' UNION ALL
  SELECT 'backup_records' UNION ALL SELECT 'campaign_activities' UNION ALL
  SELECT 'campaign_manual_records' UNION ALL SELECT 'campaign_participations' UNION ALL
  SELECT 'campaign_score_logs' UNION ALL SELECT 'campaign_streak_records' UNION ALL
  SELECT 'campaign_task_completions' UNION ALL SELECT 'campaign_tasks' UNION ALL
  SELECT 'campaign_weekly_scores' UNION ALL SELECT 'classes' UNION ALL
  SELECT 'content_tags' UNION ALL SELECT 'conversation_shares' UNION ALL
  SELECT 'course_schedule' UNION ALL SELECT 'course_selection' UNION ALL
  SELECT 'courses' UNION ALL SELECT 'exercise_options' UNION ALL
  SELECT 'feature_override' UNION ALL SELECT 'field_mappings' UNION ALL
  SELECT 'learner_composite_tags' UNION ALL SELECT 'learner_profile_snapshots' UNION ALL
  SELECT 'learner_profile_summaries' UNION ALL SELECT 'learning_records' UNION ALL
  SELECT 'level_exam_scores' UNION ALL SELECT 'media' UNION ALL
  SELECT 'notification_reads' UNION ALL SELECT 'notifications' UNION ALL
  SELECT 'past_papers' UNION ALL SELECT 'recommendation_events' UNION ALL
  SELECT 'school_course' UNION ALL SELECT 'school_student' UNION ALL
  SELECT 'school_teacher' UNION ALL SELECT 'score_records' UNION ALL
  SELECT 'sentence_favorites' UNION ALL SELECT 'sso_config' UNION ALL
  SELECT 'sso_login_logs' UNION ALL SELECT 'sso_user_bindings' UNION ALL
  SELECT 'student_ai_quiz_attempts' UNION ALL SELECT 'student_ai_quiz_records' UNION ALL
  SELECT 'student_classes' UNION ALL SELECT 'student_schedule' UNION ALL
  SELECT 'sync_logs' UNION ALL SELECT 'system_config' UNION ALL
  SELECT 'tag_recalibration_log' UNION ALL SELECT 'task_plan_classes' UNION ALL
  SELECT 'task_plan_records' UNION ALL SELECT 'task_plans' UNION ALL
  SELECT 'teacher_student_binding' UNION ALL SELECT 'teaching_class' UNION ALL
  SELECT 'teaching_plan_courses' UNION ALL SELECT 'teaching_plans' UNION ALL
  SELECT 'text_annotations' UNION ALL SELECT 'text_exercises' UNION ALL
  SELECT 'text_learning_records' UNION ALL SELECT 'text_similarity_matrix' UNION ALL
  SELECT 'text_similarity_matrix_tmp' UNION ALL SELECT 'text_units' UNION ALL
  SELECT 'texts' UNION ALL SELECT 'underlines' UNION ALL
  SELECT 'unit_words' UNION ALL SELECT 'update_config' UNION ALL
  SELECT 'user_follows' UNION ALL SELECT 'user_tags' UNION ALL
  SELECT 'user_text_reads' UNION ALL SELECT 'users' UNION ALL
  SELECT 'videos' UNION ALL SELECT 'vocabulary_books' UNION ALL
  SELECT 'word_ai_explanations' UNION ALL SELECT 'word_associations' UNION ALL
  SELECT 'word_category_map' UNION ALL SELECT 'words'
) t
LEFT JOIN information_schema.TABLES it
  ON it.TABLE_SCHEMA = 'english_learning' AND it.TABLE_NAME = t.expected_table
WHERE it.TABLE_NAME IS NULL;

-- 2. 检查 task_plans 表 V51 新增字段
SELECT '【task_plans V51 字段检查】' AS '';
SELECT
  c.col AS '字段名',
  IFNULL(ic.COLUMN_TYPE, '❌ 缺失') AS '状态'
FROM (
  SELECT 'article_enabled' AS col UNION ALL
  SELECT 'word_enabled' UNION ALL
  SELECT 'paper_enabled'
) c
LEFT JOIN information_schema.COLUMNS ic
  ON ic.TABLE_SCHEMA = 'english_learning'
  AND ic.TABLE_NAME = 'task_plans'
  AND ic.COLUMN_NAME = c.col;

-- 3. 检查 task_plan_classes 表 V51 新增字段
SELECT '【task_plan_classes V51 字段检查】' AS '';
SELECT
  c.col AS '字段名',
  IFNULL(ic.COLUMN_TYPE, '❌ 缺失') AS '状态'
FROM (
  SELECT 'cycle_type_override' AS col UNION ALL
  SELECT 'semester_start_override' UNION ALL
  SELECT 'semester_end_override' UNION ALL
  SELECT 'article_enabled_override' UNION ALL
  SELECT 'word_enabled_override' UNION ALL
  SELECT 'paper_enabled_override'
) c
LEFT JOIN information_schema.COLUMNS ic
  ON ic.TABLE_SCHEMA = 'english_learning'
  AND ic.TABLE_NAME = 'task_plan_classes'
  AND ic.COLUMN_NAME = c.col;

-- 4. 检查 Flyway 迁移记录
SELECT '【Flyway 迁移记录（最近10条）】' AS '';
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 10;

SQL

echo ""
echo "=============================="
echo " 检查完成"
echo "=============================="
