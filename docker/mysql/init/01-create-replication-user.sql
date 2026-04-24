-- ─────────────────────────────────────────────
-- 主库初始化：创建主从复制用户
-- 此脚本在 mysql-master 容器首次启动时自动执行
-- ─────────────────────────────────────────────

-- 复制用户创建（注意: SQL 文件不支持环境变量替换，实际执行的是同名 .sh 脚本）
-- 此文件仅作文档参考用途
CREATE USER IF NOT EXISTS 'replicator'@'172.%'
    IDENTIFIED WITH mysql_native_password
    BY 'repl123456';
GRANT REPLICATION SLAVE ON *.* TO 'replicator'@'172.%';
FLUSH PRIVILEGES;
SHOW MASTER STATUS;
