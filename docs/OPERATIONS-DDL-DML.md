# 系统运维域 DDL/DML 提取文档

> **提取范围**: 系统运维 + 配置 + SSO 域 Flyway 迁移  
> **来源文件**: V4/V5/V6/V7/V13/V16/V37/V39/V40/V46/V47  
> **提取日期**: 2026-06-19

---

## 一、CREATE TABLE DDL

### 1.1 backup_records (V4)

```sql
CREATE TABLE IF NOT EXISTS backup_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    filename VARCHAR(255) NOT NULL COMMENT '备份文件名',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小 (字节)',
    backup_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '备份类型：MANUAL/SCHEDULED',
    backup_mode VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT '备份模式：FULL/INCREMENTAL',
    category VARCHAR(50) DEFAULT 'SYSTEM' COMMENT '备份分类: system_config/user_data/business_data/school_data',
    base_backup_id BIGINT DEFAULT NULL COMMENT '增量备份的基准全量备份 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS/SUCCESS/FAILED',
    storage_path VARCHAR(500) NOT NULL COMMENT '存储路径',
    created_by VARCHAR(100) COMMENT '创建者',
    error_message TEXT COMMENT '错误信息',
    database_size BIGINT DEFAULT 0 COMMENT '数据库大小 (字节)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    completed_time DATETIME COMMENT '完成时间',
    INDEX idx_backup_type (backup_type),
    INDEX idx_backup_mode (backup_mode),
    INDEX idx_status (status),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库备份记录表';
```

**演化**: V4 通过存储过程动态添加 `category` 列（兼容旧部署）

---

### 1.2 backup_operation_logs (V4)

```sql
CREATE TABLE IF NOT EXISTS backup_operation_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL COMMENT '操作人',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型: BACKUP_CREATE/DELETE/RESTORE/DOWNLOAD/CLEANUP/SCHEDULED',
    target_id BIGINT COMMENT '目标备份记录 ID',
    target_name VARCHAR(255) COMMENT '目标备份文件名',
    detail VARCHAR(1000) COMMENT '操作详情',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果: SUCCESS/FAILED',
    ip_address VARCHAR(50) COMMENT '操作 IP 地址',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_operator (operator),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='备份操作日志表';
```

---

### 1.3 backup_config (V4)

```sql
CREATE TABLE IF NOT EXISTS backup_config (
    id INT NOT NULL DEFAULT 1,
    auto_backup_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '自动备份开关',
    backup_cron VARCHAR(100) NOT NULL DEFAULT '0 0 2 * * ?' COMMENT 'cron 表达式',
    backup_hour INT NOT NULL DEFAULT 2 COMMENT '备份小时 (0-23)',
    backup_minute INT NOT NULL DEFAULT 0 COMMENT '备份分钟 (0-59)',
    retention_days INT NOT NULL DEFAULT 30 COMMENT '保留天数',
    max_count INT NOT NULL DEFAULT 50 COMMENT '最大备份数量',
    incremental_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '增量备份开关',
    full_backup_interval_days INT NOT NULL DEFAULT 7 COMMENT '全量备份间隔天数',
    updated_by VARCHAR(50) DEFAULT NULL COMMENT '最后更新人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (id),
    CONSTRAINT chk_single_row CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份配置表 (单行)';
```

---

### 1.4 update_config (V4 → V39 → V40)

**原始定义 (V4)**:
```sql
CREATE TABLE IF NOT EXISTS update_config (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    cron_expr            VARCHAR(64) NOT NULL DEFAULT '0 0 3 * * ?' COMMENT 'Spring cron expression',
    enabled              TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether scheduled self-update is enabled',
    git_branch           VARCHAR(64) NOT NULL DEFAULT 'master' COMMENT 'tracked git branch',
    backup_before_update TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'whether to back up the database before update',
    last_run_at          DATETIME DEFAULT NULL COMMENT 'last execution time',
    last_run_status      VARCHAR(16) DEFAULT 'NEVER' COMMENT 'NEVER/RUNNING/SUCCESS/FAILED'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='self-update configuration';
```

**字段演化历程**:
| 版本 | 变更类型 | 说明 |
|------|---------|------|
| V4   | CREATE  | 初始创建，含 `backup_before_update` 字段 |
| V39  | ADD COLUMN | 新增零停机热部署字段：`hot_deploy_enabled/cron_expr/last_run_at/last_run_status` |
| V40  | DROP COLUMN | 删除 `backup_before_update` + 热部署专用列（已合并到主字段） |

**最终形态 (V40 后)**:
```sql
update_config 表字段:
- id (BIGINT AUTO_INCREMENT PRIMARY KEY)
- cron_expr (VARCHAR(64)) — 热部署 cron 表达式
- enabled (TINYINT(1)) — 是否启用定时零停机热部署
- git_branch (VARCHAR(64)) — 追踪分支
- last_run_at (DATETIME) — 最近一次热部署执行时间
- last_run_status (VARCHAR(20)) — 最近一次热部署状态
```

---

### 1.5 system_config (V6)

```sql
CREATE TABLE IF NOT EXISTS `system_config` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `config_group` VARCHAR(50) NOT NULL COMMENT '配置分组: ai_translate, ai_article, ai_chat',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` TEXT COMMENT '配置值',
    `description` VARCHAR(255) COMMENT '配置说明（运维面板显示）',
    `is_secret` TINYINT DEFAULT 0 COMMENT '是否敏感（前端脱敏显示）',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50),
    UNIQUE KEY `uk_group_key` (`config_group`, `config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
```

**配置分组命名规范**:
| config_group | 用途 |
|-------------|------|
| `ai_translate` | AI 翻译服务配置（早期，已迁移到 ai_provider） |
| `ai_config` | AI 服务商凭证（HiAgent/OpenRouter 等） |
| `feature_toggle` | 功能开关控制 |
| `content_limits` | 内容大小限制（V46 新增） |

---

### 1.6 ai_provider (V6 + V13)

```sql
CREATE TABLE IF NOT EXISTS `ai_provider` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `service` VARCHAR(50) NOT NULL COMMENT 'translate / article / chat',
    `name` VARCHAR(100) NOT NULL COMMENT 'display name',
    `api_url` VARCHAR(500) NOT NULL COMMENT 'provider endpoint',
    `api_key` TEXT NOT NULL COMMENT 'provider api key',
    `app_id` VARCHAR(200) NULL COMMENT 'App ID（HiAgent 等服务商必填）',
    `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible' COMMENT '协议类型：openai_compatible | hiagent',
    `model` VARCHAR(200) NOT NULL COMMENT 'default model',
    `weight` INT NOT NULL DEFAULT 1 COMMENT 'weighted routing priority',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT 'whether provider is enabled',
    `timeout_ms` INT NOT NULL DEFAULT 30000 COMMENT 'request timeout in ms',
    `remark` VARCHAR(255) COMMENT 'operator note',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `updated_by` VARCHAR(50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI provider configuration';
```

**演化**: V13 新增 `protocol` 列，区分 OpenAI 兼容协议与 HiAgent 专有协议

---

### 1.7 feature_toggles 相关表 (V7)

**注意**: 实际使用 `system_config` 表存储 toggle，而非独立的 `feature_toggles` 表。独立表为：

#### feature_override (用户级覆盖)

```sql
CREATE TABLE IF NOT EXISTS feature_override (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    scope        VARCHAR(16)  NOT NULL COMMENT 'tag 或 user',
    scope_value  VARCHAR(128) NOT NULL COMMENT 'tag 名称 或 userId 字符串',
    feature_key  VARCHAR(128) NOT NULL,
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    description  VARCHAR(255) DEFAULT NULL,
    updated_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by   VARCHAR(64)  DEFAULT NULL,
    UNIQUE KEY uk_scope_key (scope, scope_value, feature_key),
    INDEX idx_scope_value (scope, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### user_tags (用户标签)

```sql
CREATE TABLE IF NOT EXISTS user_tags (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    tag_name     VARCHAR(64) NOT NULL,
    created_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(64) DEFAULT NULL,
    UNIQUE KEY uk_user_tag (user_id, tag_name),
    INDEX idx_tag_name (tag_name),
    INDEX idx_user_id  (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

### 1.8 monitoring/access tables (V5 - 实为 V23)

**注意**: 任务中指定 V5，但实际内容为 V23（访问监控整合）

#### access_log

```sql
CREATE TABLE IF NOT EXISTS access_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT DEFAULT NULL COMMENT '用户 ID',
    username VARCHAR(50) DEFAULT NULL COMMENT '用户名',
    method VARCHAR(10) NOT NULL COMMENT 'HTTP 方法',
    uri VARCHAR(500) NOT NULL COMMENT '请求 URI',
    query_string VARCHAR(1000) DEFAULT NULL COMMENT '查询参数',
    status_code INT NOT NULL COMMENT 'HTTP 状态码',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间 (毫秒)',
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'IP 地址',
    user_agent VARCHAR(500) DEFAULT NULL COMMENT 'User-Agent',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_created_time (created_time),
    INDEX idx_username (username),
    INDEX idx_uri (uri(191)),
    INDEX idx_status_code (status_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访问日志表';
```

#### access_stats_hourly

```sql
CREATE TABLE IF NOT EXISTS access_stats_hourly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_hour DATETIME NOT NULL COMMENT '统计小时 (整点)',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间 (毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间 (毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数 (4xx+5xx)',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_hour (stat_hour)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每小时访问统计';
```

#### access_stats_daily

```sql
CREATE TABLE IF NOT EXISTS access_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    total_requests INT NOT NULL DEFAULT 0 COMMENT '总请求数',
    unique_users INT NOT NULL DEFAULT 0 COMMENT '独立用户数',
    peak_concurrent INT NOT NULL DEFAULT 0 COMMENT '峰值并发数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间 (毫秒)',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间 (毫秒)',
    error_count INT NOT NULL DEFAULT 0 COMMENT '错误数',
    top_pages TEXT DEFAULT NULL COMMENT '热门页面 JSON',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='每日访问统计';
```

#### ai_call_log

```sql
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称：HIAGENT/OPENROUTER/TENCENT_TRANSLATE/TENCENT_ASR/BILIBILI',
    method_name VARCHAR(100) DEFAULT NULL COMMENT '调用方法名',
    success TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否成功',
    response_time_ms BIGINT NOT NULL DEFAULT 0 COMMENT '响应时间 (毫秒)',
    error_message VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    input_tokens INT DEFAULT NULL COMMENT '输入 token 数',
    output_tokens INT DEFAULT NULL COMMENT '输出 token 数',
    model_name VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
    triggered_by VARCHAR(100) DEFAULT NULL COMMENT '触发用户',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_service_name (service_name),
    INDEX idx_created_time (created_time),
    INDEX idx_success (success)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日志表';
```

#### ai_call_stats_daily

```sql
CREATE TABLE IF NOT EXISTS ai_call_stats_daily (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stat_date DATE NOT NULL COMMENT '统计日期',
    service_name VARCHAR(50) NOT NULL COMMENT '服务名称',
    total_calls INT NOT NULL DEFAULT 0 COMMENT '总调用次数',
    success_calls INT NOT NULL DEFAULT 0 COMMENT '成功次数',
    failed_calls INT NOT NULL DEFAULT 0 COMMENT '失败次数',
    avg_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '平均响应时间',
    max_response_time_ms INT NOT NULL DEFAULT 0 COMMENT '最大响应时间',
    total_input_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输入 token',
    total_output_tokens BIGINT NOT NULL DEFAULT 0 COMMENT '总输出 token',
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_date_service (stat_date, service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 调用日统计';
```

---

## 二、ALTER TABLE 语句

### 2.1 users 表角色扩展 (V23)

```sql
ALTER TABLE users MODIFY COLUMN role ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER';
```

**说明**: 新增 OPS 运维角色

---

### 2.2 ai_provider protocol 字段 (V13)

```sql
-- 检查字段是否存在（幂等）
SET @ai_provider_protocol_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_provider' AND COLUMN_NAME = 'protocol'
);
SET @ai_provider_protocol_sql = IF(
    @ai_provider_protocol_exists = 0,
    'ALTER TABLE `ai_provider` ADD COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT ''openai_compatible'' AFTER `service`',
    'SELECT 1'
);
PREPARE stmt FROM @ai_provider_protocol_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 统一字段定义
ALTER TABLE `ai_provider`
    MODIFY COLUMN `protocol` VARCHAR(30) NOT NULL DEFAULT 'openai_compatible'
        COMMENT '协议类型：openai_compatible | hiagent';
```

---

## 三、INSERT IGNORE Seed 数据

### 3.1 backup_config (V4)

```sql
INSERT IGNORE INTO backup_config (id, auto_backup_enabled, backup_cron, backup_hour, backup_minute,
    retention_days, max_count, incremental_enabled, full_backup_interval_days)
VALUES (1, 1, '0 0 2 * * ?', 2, 0, 30, 50, 0, 7);
```

---

### 3.2 update_config (V4)

```sql
INSERT IGNORE INTO update_config (id, cron_expr, enabled, git_branch, backup_before_update, last_run_status)
VALUES (1, '0 0 3 * * ?', 0, 'master', 1, 'NEVER');
```

---

### 3.3 system_config - AI 翻译配置 (V6)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('ai_translate', 'api_url', 'https://api.siliconflow.cn/v1/chat/completions', 'API 地址（OpenAI 兼容）', 0),
('ai_translate', 'api_key', '', 'API 密钥', 1),
('ai_translate', 'model', 'Qwen/Qwen2.5-7B-Instruct', '模型名称', 0),
('ai_translate', 'enabled', 'true', '是否启用', 0),
('ai_translate', 'timeout', '30000', '超时时间 (ms)', 0);
```

---

### 3.4 system_config - feature_toggle 学生端页面 (V7)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'page_dashboard', 'true', '学生端 - 发现页', 0),
('feature_toggle', 'page_courses', 'false', '学生端 - 课程页（开发中）', 0),
('feature_toggle', 'page_classes', 'true', '学生端 - 班级/社区页', 0),
('feature_toggle', 'page_assignments', 'true', '学生端 - 任务页', 0),
('feature_toggle', 'page_articles', 'true', '学生端 - 文章页', 0),
('feature_toggle', 'page_vocabulary', 'true', '学生端 - 生词本页', 0),
('feature_toggle', 'page_my_study', 'true', '学生端 - 我的学习页', 0),
('feature_toggle', 'page_ai_video', 'false', '学生端-AI 视频字幕页（未完善）', 0),
('feature_toggle', 'page_ai_qa', 'true', '学生端-AI 问答页', 0),
('feature_toggle', 'page_user_texts', 'true', '学生端 - 我的上传页', 0);
```

---

### 3.5 system_config - feature_toggle 学生端补充页面 (V13)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_scores',            'true',  '学生端 - 等级考试成绩页',         0),
  ('feature_toggle', 'page_course_selections', 'true',  '学生端 - 我的选课页',             0),
  ('feature_toggle', 'page_schedule',          'true',  '学生端 - 我的课表页',             0),
  ('feature_toggle', 'page_word_recite',       'true',  '学生端 - 背单词页',                0);
```

**注**: V13 还重复插入了 page_courses（已在 V7 seed），以及 page_videos/page_interactive_text 等，后被 V47 清理

---

### 3.6 system_config - feature_toggle 管理员模块 (V13)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'admin.dashboard',    'true', '管理员 - 仪表盘',     0),
  ('feature_toggle', 'admin.courses',      'true', '管理员 - 课程管理',   0),
  ('feature_toggle', 'admin.classes',      'true', '管理员 - 班级管理',   0),
  ('feature_toggle', 'admin.users',        'true', '管理员 - 用户管理',   0),
  ('feature_toggle', 'admin.units',        'true', '管理员 - 单元管理',   0),
  ('feature_toggle', 'admin.words',        'true', '管理员 - 单词管理',   0),
  ('feature_toggle', 'admin.texts',        'true', '管理员 - 课文管理',   0),
  ('feature_toggle', 'admin.assignments',  'true', '管理员 - 作业管理',   0),
  ('feature_toggle', 'admin.sso_approval', 'true', '管理员-SSO 审批',   0);
```

---

### 3.7 system_config - feature_toggle 教师端 (V13)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_teacher_dashboard',   'true', '教师端 - 仪表盘',         0),
  ('feature_toggle', 'page_teacher_courses',     'true', '教师端 - 课程管理',       0),
  ('feature_toggle', 'page_teacher_my_courses',  'true', '教师端 - 我的课程',       0),
  ('feature_toggle', 'page_teacher_classes',     'true', '教师端 - 班级管理',       0),
  ('feature_toggle', 'page_teacher_assignments', 'true', '教师端 - 作业管理',       0);
```

---

### 3.8 system_config - feature_toggle 运维端 (V13)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('feature_toggle', 'page_ops_monitoring',           'true', '运维端 - 系统监控',           0),
  ('feature_toggle', 'page_ops_ai_providers',         'true', '运维端-AI 提供商管理',       0),
  ('feature_toggle', 'page_ops_feature_toggles',      'true', '运维端 - 功能开关管理',       0),
  ('feature_toggle', 'page_ops_school_data_config',   'true', '运维端 - 学校数据配置',       0),
  ('feature_toggle', 'page_ops_school_data_browser',  'true', '运维端 - 学校数据浏览器',     0),
  ('feature_toggle', 'page_ops_update',               'true', '运维端 - 系统更新',           0);
```

---

### 3.9 system_config - SSO 自动授权 (V16)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
('feature_toggle', 'sso.auto_grant_student', 'true', 'SSO 学号匹配 school_student 自动授权 STUDENT', 0),
('feature_toggle', 'sso.auto_grant_teacher', 'true', 'SSO 工号匹配 school_teacher 自动授权 TEACHER', 0);
```

---

### 3.10 system_config - ai_config HiAgent 凭证 (V13)

```sql
INSERT IGNORE INTO `system_config` (`config_group`, `config_key`, `config_value`, `description`, `is_secret`) VALUES
  ('ai_config', 'hiagent_api_url',    '', 'HiAgent API Base URL（留空则使用环境变量 HIAGENT_API_URL）',         0),
  ('ai_config', 'hiagent_api_key',    '', 'HiAgent 改写应用 API Key（留空则使用环境变量 HIAGENT_API_KEY）',      1),
  ('ai_config', 'hiagent_app_id',     '', 'HiAgent 改写应用 App ID（留空则使用环境变量 HIAGENT_APP_ID）',        0),
  ('ai_config', 'hiagent_qa_api_key', '', 'HiAgent 问答应用 API Key（必须与改写凭证不同；留空则用 HIAGENT_QA_API_KEY）', 1),
  ('ai_config', 'hiagent_qa_app_id',  '', 'HiAgent 问答应用 App ID（必须与改写凭证不同；留空则用 HIAGENT_QA_APP_ID）',  0);
```

---

### 3.11 system_config - learner_profile (V37)

```sql
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description)
VALUES
  ('feature_toggle', 'admin.persona_analytics',         'true', '管理员 - 画像分析'),
  ('feature_toggle', 'admin.class_cohort',              'true', '管理员 - 班级画像分析'),
  ('feature_toggle', 'student.learner_profile_enabled', 'true', '学生端 - 我的画像');
```

---

### 3.12 system_config - content_limits (V46)

```sql
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  ('content_limits', 'upload.content_size.image',              '10MB',  '图片上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.video',              '512MB', '视频上传大小限制（全局默认）', 0),
  ('content_limits', 'upload.content_size.excel_import',       '10MB',  'Excel 批量导入大小限制',        0);
```

---

### 3.13 system_config - V47 补充缺失种子

```sql
INSERT IGNORE INTO system_config (config_group, config_key, config_value, description, is_secret) VALUES
  ('feature_toggle', 'student.video_upload_enabled', 'true', '学生端 - 视频上传功能（全局关闭后仅标签内用户可上传）', 0),
  ('feature_toggle', 'teacher.learner_profile_enabled', 'true', '教师端 - 学生学习画像查看功能', 0);
```

---

### 3.14 ai_provider 默认数据 (V6)

```sql
INSERT IGNORE INTO `ai_provider`
    (`id`, `service`, `name`, `api_url`, `api_key`, `model`, `weight`, `enabled`, `timeout_ms`, `remark`)
SELECT
    1,
    'translate',
    'SiliconFlow (default)',
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_url'),
             'https://api.siliconflow.cn/v1/chat/completions'),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='api_key'), ''),
    COALESCE((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='model'),
             'Qwen/Qwen2.5-7B-Instruct'),
    1,
    1,
    COALESCE(CAST((SELECT config_value FROM system_config WHERE config_group='ai_translate' AND config_key='timeout') AS UNSIGNED),
             30000),
    'migrated from system_config'
WHERE NOT EXISTS (SELECT 1 FROM ai_provider WHERE service = 'translate');
```

---

## 四、UPDATE 语句

### 4.1 修复 description 乱码 (V7)

```sql
UPDATE `system_config` SET `description` = '学生端 - 发现页'              WHERE `config_key` = 'page_dashboard'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 课程页（开发中）'    WHERE `config_key` = 'page_courses'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 班级/社区页'         WHERE `config_key` = 'page_classes'     AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 任务页'              WHERE `config_key` = 'page_assignments' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 文章页'              WHERE `config_key` = 'page_articles'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 生词本页'            WHERE `config_key` = 'page_vocabulary' AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 我的学习页'          WHERE `config_key` = 'page_my_study'   AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI 视频字幕页（未完善）' WHERE `config_key` = 'page_ai_video'  AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端-AI 问答页'            WHERE `config_key` = 'page_ai_qa'      AND `config_group` = 'feature_toggle';
UPDATE `system_config` SET `description` = '学生端 - 我的上传页'          WHERE `config_key` = 'page_user_texts' AND `config_group` = 'feature_toggle';
```

---

### 4.2 SSO 自动授权启用 (V16)

```sql
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key` IN ('sso.auto_grant_student', 'sso.auto_grant_teacher');
```

---

### 4.3 恢复 page_ai_qa (V13)

```sql
UPDATE `system_config`
SET `config_value` = 'true'
WHERE `config_group` = 'feature_toggle'
  AND `config_key`   = 'page_ai_qa';
```

---

### 4.4 演示账号维护 (V23)

```sql
-- 确保 teacher/student 账号存在
INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'teacher',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'TEACHER', '演示教师', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'teacher');

INSERT INTO users (username, password, role, real_name, status, deleted, created_time, updated_time)
SELECT 'student',
       (SELECT password FROM users WHERE username = 'admin' LIMIT 1),
       'STUDENT', '演示学生', 'ACTIVE', 0, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'student');

-- 同步密码 + 确保 ACTIVE 状态
UPDATE users SET
    password = (SELECT pw FROM (SELECT password AS pw FROM users WHERE username = 'admin' LIMIT 1) tmp),
    status = 'ACTIVE',
    deleted = 0
WHERE username IN ('teacher', 'student');
```

---

### 4.5 V39 hot_deploy 字段合并逻辑 (V40)

```sql
-- 将 hot_deploy_enabled → enabled（如果旧 enabled 列存在则先删除）
IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE()
             AND TABLE_NAME = 'update_config'
             AND COLUMN_NAME = 'hot_deploy_enabled') THEN

    -- 用热部署字段的值覆盖普通更新字段
    UPDATE update_config
    SET enabled        = COALESCE(hot_deploy_enabled, 0),
        cron_expr      = COALESCE(hot_deploy_cron_expr, '0 0 2 ? * SUN'),
        last_run_at    = hot_deploy_last_run_at,
        last_run_status = COALESCE(hot_deploy_last_run_status, 'NEVER');
END IF;
```

---

## 五、DELETE 语句

### 5.1 feature_toggle 孤立种子清理 (V47)

```sql
DELETE FROM system_config WHERE config_group = 'feature_toggle' AND config_key IN (
  -- 学生端：路由无 featureKey 守卫
  'page_vocabulary',        -- 消费点改用 page_word_recite
  'page_videos',            -- 无 featureKey
  'page_video_player',      -- 无 featureKey
  'page_interactive_text',  -- 无对应路由
  'page_text_learning',     -- 纯 redirect
  -- 管理员端：实际在 /ops/ 下
  'page_admin_backups',
  'page_admin_monitoring',
  -- 运维端：ops 路由均无 featureKey 守卫
  'page_ops_monitoring',
  'page_ops_ai_providers',
  'page_ops_feature_toggles',
  'page_ops_school_data_config',
  'page_ops_school_data_browser',
  'page_ops_update',
  -- 教师端：teacher 路由均无 featureKey 守卫
  'page_teacher_dashboard',
  'page_teacher_courses',
  'page_teacher_my_courses',
  'page_teacher_classes',
  'page_teacher_assignments',
  -- 行为开关：曾规划但无代码消费
  'student.lazy_data_sync',
  'student.lazy_course_selection'
);
```

---

### 5.2 feature_override 禁用规则清理 (V13)

```sql
DELETE FROM `feature_override` WHERE `feature_key` = 'page_ai_qa';
```

---

## 六、存储过程包裹模式

### 6.1 V39: add_hot_deploy_fields

**目的**: 条件性添加零停机热部署字段，避免重复添加报错

```sql
DROP PROCEDURE IF EXISTS v39_add_hot_deploy_fields;
DELIMITER $$
CREATE PROCEDURE v39_add_hot_deploy_fields()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'update_config'
                     AND COLUMN_NAME = 'hot_deploy_enabled') THEN
        ALTER TABLE update_config
            ADD COLUMN hot_deploy_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用定时零停机热部署',
            ADD COLUMN hot_deploy_cron_expr VARCHAR(100) NULL COMMENT '热部署 cron 表达式',
            ADD COLUMN hot_deploy_last_run_at DATETIME NULL COMMENT '最近一次热部署执行时间',
            ADD COLUMN hot_deploy_last_run_status VARCHAR(20) NULL DEFAULT 'NEVER' COMMENT '最近一次热部署状态';
    END IF;
END$$
DELIMITER ;
CALL v39_add_hot_deploy_fields();
DROP PROCEDURE IF EXISTS v39_add_hot_deploy_fields;
```

---

### 6.2 V40: cleanup_update_config

**目的**: 条件性清理字段，支持从 V39→V40 的平滑迁移

```sql
DROP PROCEDURE IF EXISTS v40_cleanup_update_config;
DELIMITER $$
CREATE PROCEDURE v40_cleanup_update_config()
BEGIN
    -- 将 hot_deploy_enabled → enabled（如果旧 enabled 列存在则先删除）
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'hot_deploy_enabled') THEN

        UPDATE update_config
        SET enabled        = COALESCE(hot_deploy_enabled, 0),
            cron_expr      = COALESCE(hot_deploy_cron_expr, '0 0 2 ? * SUN'),
            last_run_at    = hot_deploy_last_run_at,
            last_run_status = COALESCE(hot_deploy_last_run_status, 'NEVER');

        ALTER TABLE update_config
            DROP COLUMN hot_deploy_enabled,
            DROP COLUMN hot_deploy_cron_expr,
            DROP COLUMN hot_deploy_last_run_at,
            DROP COLUMN hot_deploy_last_run_status;
    END IF;

    -- 删除普通更新专用列（backup_before_update）
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'update_config'
                 AND COLUMN_NAME = 'backup_before_update') THEN
        ALTER TABLE update_config DROP COLUMN backup_before_update;
    END IF;
END$$
DELIMITER ;
CALL v40_cleanup_update_config();
DROP PROCEDURE IF EXISTS v40_cleanup_update_config;
```

---

### 6.3 V4: dynamic_add_category

**目的**: 为 backup_records 表动态添加 category 列

```sql
SET @backup_category_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'backup_records'
      AND COLUMN_NAME = 'category'
);
SET @backup_category_sql = IF(
    @backup_category_exists = 0,
    'ALTER TABLE backup_records ADD COLUMN category VARCHAR(50) DEFAULT ''SYSTEM'' COMMENT ''备份分类：system_config/user_data/business_data/school_data'' AFTER backup_mode',
    'SELECT 1'
);
PREPARE stmt FROM @backup_category_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
```

---

## 七、关键业务配置摘要

### 7.1 feature_toggle 命名规范

**层级结构**: `{role}[.{module}].[feature|page]_[feature_name]`

| 前缀 | 角色 | 示例 |
|-----|------|------|
| `page_*` | 学生端页面 | `page_dashboard`, `page_ai_qa` |
| `admin.*` | 管理员模块 | `admin.dashboard`, `admin.sso_approval` |
| `teacher.*` | 教师端 | `teacher.school_data_enabled`, `teacher.learner_profile_enabled` |
| `student.*` | 学生端功能 | `student.video_upload_enabled`, `student.learner_profile_enabled` |
| `ops.*` | 运维端 | `ops.school_data_config`（专用 key，非 `page_ops_*`） |

### 7.2 content_limits 命名规范

```
upload.content_size.<media_type>
```

| 配置键 | 默认值 | 用途 |
|-------|--------|------|
| `upload.content_size.image` | 10MB | 图片上传限制 |
| `upload.content_size.video` | 512MB | 视频上传限制 |
| `upload.content_size.excel_import` | 10MB | Excel 批量导入限制 |

### 7.3 update_config 最终字段含义

| 字段 | 含义 | 默认值 |
|-----|------|-------|
| `enabled` | 是否启用定时零停机热部署 | 0（关闭） |
| `cron_expr` | 热部署 cron 表达式 | '0 0 2 ? * SUN'（周日凌晨 2 点） |
| `git_branch` | 追踪分支 | 'master' |
| `last_run_at` | 最近一次执行时间 | NULL |
| `last_run_status` | 最近一次状态 | 'NEVER' |

---

## 八、文件索引与来源

| SQL 类型 | 源文件 | 行数 |
|--------|--------|------|
| backup_records | V4__backup_and_update_system.sql:10-29 | 30 |
| backup_operation_logs | V4__backup_and_update_system.sql:50-63 | 14 |
| backup_config | V4__backup_and_update_system.sql:68-86 | 19 |
| update_config | V4__backup_and_update_system.sql:91-102 | 12 |
| system_config (create) | V6__system_config_and_ai_providers.sql:11-21 | 11 |
| system_config (seed) | V6/V7/V13/V16/V37/V46/V47 | ~90 rows |
| ai_provider | V6__system_config_and_ai_providers.sql:34-67 | 34 |
| feature_toggles (via system_config) | V7/V13/V16/V37/V47 | ~50 rows |
| access_log | V23 (误标为 V5):9-25 | 17 |
| access_stats_hourly | V23:30-40 | 11 |
| access_stats_daily | V23:45-57 | 13 |
| ai_call_log | V23:62-77 | 16 |
| ai_call_stats_daily | V23:82-95 | 14 |
| users role alter | V23:100 | 1 |
| feature_override | V7:66-77 | 12 |
| user_tags | V7:55-64 | 10 |

---

**文档结束**

> 本 extracting 仅供查阅参考，**不可直接用于生产**。如需修改请编辑原 Flyway 迁移文件。
