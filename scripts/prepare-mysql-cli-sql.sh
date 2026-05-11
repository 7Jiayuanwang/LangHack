#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <input-sql> <output-sql>" >&2
    exit 1
fi

input_sql="$1"
output_sql="$2"

if [ ! -f "$input_sql" ]; then
    echo "Input SQL file not found: $input_sql" >&2
    exit 1
fi

# 在存储过程块前后插入 DELIMITER $$ / DELIMITER ;
# 支持两种格式：
#   1. 过程体使用 ; 终止符（Flyway 迁移文件）→ END; 转换为 END$$
#   2. 过程体使用 $$ 终止符（04-apply-all-migrations.sql）→ 直接添加 DELIMITER 包装
awk '
BEGIN {
    in_proc = 0; delim_set = 0
    print "SET NAMES utf8mb4;"
}

# 空行和注释行不改变 delimiter 状态
/^[[:space:]]*$/ || /^[[:space:]]*--/ { print; next }

# DROP PROCEDURE IF EXISTS：开始或延续一个过程组
/^[[:space:]]*DROP[[:space:]]+PROCEDURE[[:space:]]+IF[[:space:]]+EXISTS/ {
    if (!delim_set) { print "DELIMITER $$"; delim_set = 1 }
    print; next
}

# CREATE PROCEDURE：进入过程体
/^[[:space:]]*CREATE[[:space:]]+PROCEDURE/ {
    if (!delim_set) { print "DELIMITER $$"; delim_set = 1 }
    in_proc = 1; print; next
}

# END$$（已有 $$ 终止符）：退出过程体
/^[[:space:]]*END\$\$$/ { in_proc = 0; print; next }

# END;（原始 ; 终止符）：转换为 END$$ 并退出过程体
/^[[:space:]]*END;[[:space:]]*$/ {
    sub(/;[[:space:]]*$/, "$$")
    in_proc = 0; print; next
}

# CALL ...$$：仍属于过程组
/^[[:space:]]*CALL[[:space:]].*\$\$$/ { print; next }

# 普通 SQL：若在过程组外则关闭 DELIMITER
{
    if (delim_set && !in_proc) { print "DELIMITER ;"; delim_set = 0 }
    print
}

END { if (delim_set) print "DELIMITER ;" }
' "$input_sql" > "$output_sql"
