#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <mysql-container> <root-password> [database]" >&2
    exit 1
fi

mysql_container="$1"
db_root_pw="$2"
db_name="${3:-english_learning}"

sql_snippet=$(docker exec "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" -N -e "
SELECT LEFT(REPLACE(REPLACE(INFO, CHAR(10), ' '), CHAR(13), ' '), 180)
FROM information_schema.PROCESSLIST
WHERE DB = DATABASE()
  AND COMMAND <> 'Sleep'
  AND INFO IS NOT NULL
  AND INFO NOT LIKE 'SELECT LEFT(REPLACE(REPLACE(INFO,%'
ORDER BY TIME DESC
LIMIT 1;
" 2>/dev/null || true)

sql_snippet="${sql_snippet#"${sql_snippet%%[![:space:]]*}"}"
sql_snippet="${sql_snippet%"${sql_snippet##*[![:space:]]}"}"

[ -z "$sql_snippet" ] && exit 0

if [[ "$sql_snippet" =~ ^ALTER[[:space:]]+TABLE[[:space:]]+\`?([A-Za-z0-9_]+)\`? ]]; then
    echo "正在更新表结构: ${BASH_REMATCH[1]}"
elif [[ "$sql_snippet" =~ ^CREATE[[:space:]]+INDEX[[:space:]]+([A-Za-z0-9_]+)[[:space:]]+ON[[:space:]]+\`?([A-Za-z0-9_]+)\`? ]]; then
    echo "正在创建索引: ${BASH_REMATCH[1]} -> ${BASH_REMATCH[2]}"
elif [[ "$sql_snippet" =~ ^CREATE[[:space:]]+PROCEDURE[[:space:]]+([A-Za-z0-9_]+) ]]; then
    echo "正在创建迁移过程: ${BASH_REMATCH[1]}"
elif [[ "$sql_snippet" =~ ^CALL[[:space:]]+([A-Za-z0-9_]+) ]]; then
    echo "正在执行迁移过程: ${BASH_REMATCH[1]}"
elif [[ "$sql_snippet" =~ ^DROP[[:space:]]+PROCEDURE[[:space:]]+IF[[:space:]]+EXISTS[[:space:]]+([A-Za-z0-9_]+) ]]; then
    echo "正在清理迁移过程: ${BASH_REMATCH[1]}"
elif [[ "$sql_snippet" =~ ^INSERT[[:space:]]+IGNORE[[:space:]]+INTO[[:space:]]+\`?system_config\`? ]]; then
    echo "正在同步系统配置种子数据"
elif [[ "$sql_snippet" =~ ^CREATE[[:space:]]+TABLE[[:space:]]+IF[[:space:]]+NOT[[:space:]]+EXISTS[[:space:]]+\`?([A-Za-z0-9_]+)\`? ]]; then
    echo "正在创建数据表: ${BASH_REMATCH[1]}"
else
    echo "正在执行数据库迁移语句"
fi
