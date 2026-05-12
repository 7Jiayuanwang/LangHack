-- ═══════════════════════════════════════════════════════════════
-- 00-base-tables.sql 与 V0 一致性修复补丁
-- 生成日期: 2026-05-05
-- 问题: 00-base-tables.sql 的 users 表包含 SSO 字段，但 V0 不包含
--       导致 Docker 环境与 CI 环境表结构不一致
--
-- 修复策略: 从 00-base-tables.sql 删除 SSO 字段，让 V8 统一添加
-- ═══════════════════════════════════════════════════════════════

-- 此补丁应该直接修改源文件，而非在数据库中执行
-- 以下是需要在 docker/mysql/init/00-base-tables.sql 中进行的修改

/*
修改文件: docker/mysql/init/00-base-tables.sql

在 users 表定义中，删除以下 3 行（约在第 33-35 行）：

    `sso_bound` TINYINT(1) DEFAULT 0 COMMENT '是否通过SSO绑定',
    `sso_provider` VARCHAR(50) DEFAULT NULL COMMENT 'SSO提供商',
    `sso_subject` VARCHAR(255) DEFAULT NULL COMMENT 'SSO唯一标识',

修改后的 users 表定义应该是：

CREATE TABLE IF NOT EXISTS `users` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(255) NOT NULL COMMENT '密码',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `role` ENUM('USER','STUDENT','TEACHER','ADMIN','OPS') NOT NULL DEFAULT 'USER',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    `inner_uid` VARCHAR(100) DEFAULT NULL COMMENT '用户内部唯一标识符',
    `hobby` VARCHAR(100) DEFAULT NULL COMMENT '用户兴趣爱好',
    `level` VARCHAR(50) DEFAULT NULL COMMENT '英语水平等级',
    `deleted` TINYINT DEFAULT 0 COMMENT '是否删除 0-未删除 1-已删除',
    `created_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 删除了 sso_bound, sso_provider, sso_subject 三个字段
    PRIMARY KEY (`id`),
    UNIQUE KEY `username` (`username`),
    KEY `idx_username` (`username`),
    KEY `idx_role` (`role`),
    KEY `idx_status` (`status`),
    KEY `idx_deleted` (`deleted`),
    KEY `idx_inner_uid` (`inner_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

理由：
1. V8__sso_integration.sql 会通过 ALTER TABLE 添加这些字段（已有幂等检查）
2. 保持 Docker 初始化环境与 CI 测试环境的表结构一致
3. 遵循"迁移脚本是唯一真相来源"的原则

验证步骤：
1. 修改 docker/mysql/init/00-base-tables.sql
2. docker compose down -v
3. docker compose up -d
4. docker exec -it langhack-mysql-master mysql -u root -p
5. USE english_learning;
6. SHOW CREATE TABLE users;
7. 确认 sso_bound, sso_provider, sso_subject 字段存在（由 V8 添加）
*/

-- ═══════════════════════════════════════════════════════════════
-- 如果需要在现有数据库中验证一致性，可以执行以下查询
-- ═══════════════════════════════════════════════════════════════

USE english_learning;

-- 检查 users 表是否有 SSO 相关字段
SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    COLUMN_DEFAULT,
    COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'english_learning'
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('sso_bound', 'sso_provider', 'sso_subject')
ORDER BY ORDINAL_POSITION;

-- 预期结果：应该返回 3 行记录（由 V8 添加）
-- 如果返回 0 行，说明 V8 迁移未执行或失败

SELECT
    CASE
        WHEN COUNT(*) = 3 THEN '✅ SSO 字段存在（由 V8 添加）'
        WHEN COUNT(*) = 0 THEN '❌ SSO 字段缺失（V8 未执行）'
        ELSE '⚠️ SSO 字段部分存在（异常状态）'
    END AS status,
    COUNT(*) AS field_count
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'english_learning'
  AND TABLE_NAME = 'users'
  AND COLUMN_NAME IN ('sso_bound', 'sso_provider', 'sso_subject');
