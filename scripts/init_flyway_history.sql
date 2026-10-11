-- ============================================================
-- Flyway 历史表初始化脚本
-- 用途：在已运行的数据库上首次启用 Flyway
-- 使用方法：
--   docker cp scripts/init_flyway_history.sql langhack-mysql-master:/tmp/
--   docker exec langhack-mysql-master bash -c 'mysql -u root -p"$MYSQL_ROOT_PASSWORD" english_learning < /tmp/init_flyway_history.sql'
--
-- 执行前提：数据库中不存在 flyway_schema_history 表
-- 生成时间：2026-09-07
-- ============================================================

CREATE TABLE IF NOT EXISTS `flyway_schema_history` (
    `installed_rank` INT NOT NULL,
    `version`        VARCHAR(50),
    `description`    VARCHAR(200) NOT NULL,
    `type`           VARCHAR(20) NOT NULL,
    `script`         VARCHAR(1000) NOT NULL,
    `checksum`       INT,
    `installed_by`   VARCHAR(100) NOT NULL,
    `installed_on`   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `execution_time` INT NOT NULL,
    `success`        TINYINT(1) NOT NULL,
    PRIMARY KEY (`installed_rank`),
    INDEX `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELETE FROM `flyway_schema_history`;

INSERT INTO `flyway_schema_history`
  (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`)
VALUES
  (1, '0', '<< Flyway Baseline >>', 'BASELINE', '<< Flyway Baseline >>', NULL, 'admin', NOW(), 0, 1);

INSERT INTO `flyway_schema_history`
  (`installed_rank`, `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`)
VALUES
  ( 2, '1',  'user text learning',                             'SQL', 'V1__user_text_learning.sql',                             NULL, 'admin', NOW(), 0, 1),
  ( 3, '2',  'media annotations',                              'SQL', 'V2__media_annotations.sql',                              NULL, 'admin', NOW(), 0, 1),
  ( 4, '3',  'backup update system',                           'SQL', 'V3__backup_update_system.sql',                           NULL, 'admin', NOW(), 0, 1),
  ( 5, '4',  'monitoring access',                              'SQL', 'V4__monitoring_access.sql',                              NULL, 'admin', NOW(), 0, 1),
  ( 6, '5',  'system config ai providers',                     'SQL', 'V5__system_config_ai_providers.sql',                     NULL, 'admin', NOW(), 0, 1),
  ( 7, '6',  'feature toggles',                                'SQL', 'V6__feature_toggles.sql',                                NULL, 'admin', NOW(), 0, 1),
  ( 8, '7',  'sso cas',                                        'SQL', 'V7__sso_cas.sql',                                        NULL, 'admin', NOW(), 0, 1),
  ( 9, '8',  'school data cdsp',                               'SQL', 'V8__school_data_cdsp.sql',                               NULL, 'admin', NOW(), 0, 1),
  (10, '9',  'media transcoding',                              'SQL', 'V9__media_transcoding.sql',                              NULL, 'admin', NOW(), 0, 1),
  (11, '10', 'ai quiz',                                        'SQL', 'V10__ai_quiz.sql',                                       NULL, 'admin', NOW(), 0, 1),
  (12, '11', 'ai conversation feedback',                       'SQL', 'V11__ai_conversation_feedback.sql',                      NULL, 'admin', NOW(), 0, 1),
  (13, '12', 'ai qa',                                          'SQL', 'V12__ai_qa.sql',                                         NULL, 'admin', NOW(), 0, 1),
  (14, '13', 'learner profile',                                'SQL', 'V13__learner_profile.sql',                               NULL, 'admin', NOW(), 0, 1),
  (15, '14', 'tag management',                                 'SQL', 'V14__tag_management.sql',                                NULL, 'admin', NOW(), 0, 1),
  (16, '15', 'reserved vocabulary',                            'SQL', 'V15__reserved_vocabulary.sql',                            NULL, 'admin', NOW(), 0, 1),
  (17, '16', 'reserved school data',                           'SQL', 'V16__reserved_school_data.sql',                           NULL, 'admin', NOW(), 0, 1),
  (18, '17', 'reserved ai model routing',                      'SQL', 'V17__reserved_ai_model_routing.sql',                     NULL, 'admin', NOW(), 0, 1),
  (19, '18', 'reserved content ops',                           'SQL', 'V18__reserved_content_ops.sql',                           NULL, 'admin', NOW(), 0, 1),
  (20, '19', 'reserved general',                               'SQL', 'V19__reserved_general.sql',                               NULL, 'admin', NOW(), 0, 1),
  (21, '20', 'campaign system',                                'SQL', 'V20__campaign_system.sql',                                NULL, 'admin', NOW(), 0, 1),
  (22, '21', 'campaign repeatable tasks and enrollment time',  'SQL', 'V21__campaign_repeatable_tasks_and_enrollment_time.sql',  NULL, 'admin', NOW(), 0, 1),
  (23, '22', 'word ai explanations',                           'SQL', 'V22__word_ai_explanations.sql',                           NULL, 'admin', NOW(), 0, 1),
  (24, '32', 'extend school student varchar',                  'SQL', 'V32__extend_school_student_varchar.sql',                  NULL, 'admin', NOW(), 0, 1),
  (25, '33', 'create videos and content tags',                 'SQL', 'V33__create_videos_and_content_tags.sql',                 NULL, 'admin', NOW(), 0, 1),
  (26, '34', 'create unit words',                              'SQL', 'V34__create_unit_words.sql',                              NULL, 'admin', NOW(), 0, 1),
  (27, '35', 'campaign weekly scores',                         'SQL', 'V35__campaign_weekly_scores.sql',                         NULL, 'admin', NOW(), 0, 1),
  (28, '36', 'campaign incentive system',                      'SQL', 'V36__campaign_incentive_system.sql',                      NULL, 'admin', NOW(), 0, 1),
  (29, '37', 'user study stats fields',                        'SQL', 'V37__user_study_stats_fields.sql',                        NULL, 'admin', NOW(), 0, 1),
  (30, '38', 'article likes and collect count',                'SQL', 'V38__article_likes_and_collect_count.sql',                NULL, 'admin', NOW(), 0, 1),
  (31, '39', 'teacher class sync fields',                      'SQL', 'V39__teacher_class_sync_fields.sql',                      NULL, 'admin', NOW(), 0, 1),
  (32, '40', 'word associations',                              'SQL', 'V40__word_associations.sql',                              NULL, 'admin', NOW(), 0, 1),
  (33, '41', 'user follows',                                   'SQL', 'V41__user_follows.sql',                                   NULL, 'admin', NOW(), 0, 1),
  (34, '42', 'teaching class course sequence',                 'SQL', 'V42__teaching_class_course_sequence.sql',                 NULL, 'admin', NOW(), 0, 1),
  (35, '43', 'article classification fields',                  'SQL', 'V43__article_classification_fields.sql',                  NULL, 'admin', NOW(), 0, 1),
  (36, '44', 'past paper library',                             'SQL', 'V44__past_paper_library.sql',                             NULL, 'admin', NOW(), 0, 1),
  (37, '45', 'notifications',                                  'SQL', 'V45__notifications.sql',                                  NULL, 'admin', NOW(), 0, 1),
  (38, '46', 'text unit many to many',                         'SQL', 'V46__text_unit_many_to_many.sql',                         NULL, 'admin', NOW(), 0, 1),
  (39, '47', 'text content section',                           'SQL', 'V47__text_content_section.sql',                           NULL, 'admin', NOW(), 0, 1),
  (40, '48', 'past paper audio media',                         'SQL', 'V48__past_paper_audio_media.sql',                         NULL, 'admin', NOW(), 0, 1),
  (41, '49', 'sentence favorites',                             'SQL', 'V49__sentence_favorites.sql',                             NULL, 'admin', NOW(), 0, 1),
  (42, '50', 'task plan system',                               'SQL', 'V50__task_plan_system.sql',                               NULL, 'admin', NOW(), 0, 1),
  (43, '51', 'task plan enhancements',                         'SQL', 'V51__task_plan_enhancements.sql',                         NULL, 'admin', NOW(), 0, 1),
  (44, '52', 'past paper attempts',                            'SQL', 'V52__past_paper_attempts.sql',                            NULL, 'admin', NOW(), 0, 1),
  (45, '53', 'assignment templates',                           'SQL', 'V53__assignment_templates.sql',                           NULL, 'admin', NOW(), 0, 1),
  (46, '54', 'learning agent context',                         'SQL', 'V54__learning_agent_context.sql',                         NULL, 'admin', NOW(), 0, 1),
  (47, '55', 'fix duplicate words',                            'SQL', 'V55__fix_duplicate_words.sql',                            NULL, 'admin', NOW(), 0, 1),
  (48, '56', 'learner review tasks',                           'SQL', 'V56__learner_review_tasks.sql',                           NULL, 'admin', NOW(), 0, 1),
  (49, '57', 'article learning insights',                      'SQL', 'V57__article_learning_insights.sql',                      NULL, 'admin', NOW(), 0, 1),
  (50, '58', 'xiaoya external learning',                       'SQL', 'V58__xiaoya_external_learning.sql',                       NULL, 'admin', NOW(), 0, 1),
  (51, '59', 'past paper ai grading',                          'SQL', 'V59__past_paper_ai_grading.sql',                          NULL, 'admin', NOW(), 0, 1),
  (52, '60', 'fix past paper data',                            'SQL', 'V60__fix_past_paper_data.sql',                            NULL, 'admin', NOW(), 0, 1),
  (53, '61', 'targeted reading assignments',                   'SQL', 'V61__targeted_reading_assignments.sql',                   NULL, 'admin', NOW(), 0, 1),
  (54, '62', 'repair notification tables',                     'SQL', 'V62__repair_notification_tables.sql',                     NULL, 'admin', NOW(), 0, 1),
  (55, '63', 'course exemption application',                   'SQL', 'V63__course_exemption_application.sql',                   NULL, 'admin', NOW(), 0, 1);

SELECT CONCAT('✅ Flyway 历史表初始化完成，共 ', COUNT(*), ' 条记录（含 baseline）') AS result
FROM `flyway_schema_history`;
