#!/bin/bash
# 执行 04-apply-all-migrations.sql
# 文件使用 $$ 作为存储过程分隔符，但无 DELIMITER 指令。
# 此脚本用 awk 动态插入 DELIMITER $$ / DELIMITER ; 包装后再执行。
set -e

SQL_FILE="/opt/langhack/04-apply-all-migrations.sql"
DB_NAME="${MYSQL_DATABASE:-english_learning}"

echo "[migrate] 开始执行增量迁移脚本..."
echo "[migrate] 时间: $(date '+%Y-%m-%d %H:%M:%S')"

# awk 规则：
#   - 空行和注释行不改变 delimiter 状态
#   - DROP PROCEDURE IF EXISTS 开始一个过程组（首次出现时插入 DELIMITER $$）
#   - CREATE PROCEDURE 标记进入过程体（in_proc=1，内部 ; 不截断语句）
#   - END$$ 标记退出过程体（in_proc=0）
#   - CALL ...$$  仍属于过程组，不关闭 delimiter
#   - 其他非空非注释行：若在过程组外则插入 DELIMITER ; 关闭过程组
awk '
BEGIN { in_proc = 0; delim_set = 0 }

/^[[:space:]]*$/ || /^[[:space:]]*--/ { print; next }

/^[[:space:]]*DROP[[:space:]]+PROCEDURE[[:space:]]+IF[[:space:]]+EXISTS/ {
    if (!delim_set) { print "DELIMITER $$"; delim_set = 1 }
    print; next
}

/^[[:space:]]*CREATE[[:space:]]+PROCEDURE/ { in_proc = 1; print; next }

/^[[:space:]]*END\$\$$/ { in_proc = 0; print; next }

/^[[:space:]]*CALL[[:space:]].*\$\$$/ { print; next }

{
    if (delim_set && !in_proc) { print "DELIMITER ;"; delim_set = 0 }
    print
}

END { if (delim_set) print "DELIMITER ;" }
' "$SQL_FILE" | mysql -u root -p"${MYSQL_ROOT_PASSWORD}" \
    --default-character-set=utf8mb4 \
    "$DB_NAME"

echo "[migrate] 增量迁移完成 ✓"
