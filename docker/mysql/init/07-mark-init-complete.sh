#!/bin/bash
# 标记数据库初始化完成
# 此脚本在所有初始化脚本执行完成后运行，创建一个标记表供 healthcheck 检查

set -e

echo "[07-mark-init-complete] 标记数据库初始化完成..."

# 从库跳过（数据由主库同步）
if [ "${MYSQL_ROLE:-master}" = "slave" ]; then
    echo "[07-mark-init-complete] 从库跳过标记创建（由主库同步）"
    exit 0
fi

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" <<-EOSQL
    -- 创建初始化完成标记表
    CREATE TABLE IF NOT EXISTS _init_complete (
        id INT PRIMARY KEY DEFAULT 1,
        completed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        CHECK (id = 1)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库初始化完成标记';

    -- 插入标记记录
    INSERT INTO _init_complete (id) VALUES (1)
    ON DUPLICATE KEY UPDATE completed_at = CURRENT_TIMESTAMP;
EOSQL

echo "[07-mark-init-complete] ✓ 初始化完成标记已创建"
