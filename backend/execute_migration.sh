#!/bin/bash
# 执行数据库迁移脚本，添加weight字段

echo "正在执行数据库迁移，添加weight字段..."

# 请修改以下数据库连接信息
DB_USER="root"
DB_PASSWORD=""
DB_NAME="english_learning"
MIGRATION_FILE="src/main/resources/db/migration/add_assignment_weight.sql"

# 如果提供了密码，使用-p参数
if [ -z "$DB_PASSWORD" ]; then
    mysql -u "$DB_USER" "$DB_NAME" < "$MIGRATION_FILE"
else
    mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" < "$MIGRATION_FILE"
fi

if [ $? -eq 0 ]; then
    echo "✅ 迁移成功！weight字段已添加到assignments表"
else
    echo "❌ 迁移失败，请检查数据库连接信息和SQL脚本"
    exit 1
fi

