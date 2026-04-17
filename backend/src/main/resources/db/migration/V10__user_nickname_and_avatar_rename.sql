-- V10: 添加 users.nickname 列，重命名 users.avatar_url → avatar
-- 原因: User 实体使用 nickname 和 avatar 字段，但数据库缺少 nickname 且头像列名为 avatar_url

-- 添加 nickname 列（幂等）
SET @dbname = DATABASE();
SET @tablename = 'users';

-- nickname
SET @colname = 'nickname';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE users ADD COLUMN nickname VARCHAR(50) DEFAULT NULL COMMENT ''昵称'' AFTER role'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 重命名 avatar_url → avatar（幂等）
SET @colname = 'avatar';
SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @colname) > 0,
    'SELECT 1',
    'ALTER TABLE users RENAME COLUMN avatar_url TO avatar'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;
