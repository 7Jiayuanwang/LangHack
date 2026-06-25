#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 3 ]; then
    echo "Usage: $0 <input-sql> <output-sql> [db-name]" >&2
    exit 1
fi

input_sql="$1"
output_sql="$2"
db_name="${3:-${DB_NAME:-english_learning}}"

if [ ! -f "$input_sql" ]; then
    echo "Input SQL file not found: $input_sql" >&2
    exit 1
fi

# 智能处理 DELIMITER：只有 DROP PROCEDURE 后面紧跟 CREATE PROCEDURE 才添加 DELIMITER 标记。
# 否则独立的 DROP PROCEDURE（用于清理过程组结束后的临时过程）会被错误地纳入过程组，
# 导致紧随其后的 ALTER/CREATE TABLE 语句出现 "DELIMITER ;ALTER ..." 这种粘连的语法错误。
awk -v db="$db_name" '
BEGIN {
    in_proc = 0; delim_set = 0
    print "SET NAMES utf8mb4;"
}

NR <= 10 { gsub(/\$\{MYSQL_DATABASE\}/, db) }

/^[[:space:]]*$/ || /^[[:space:]]*--/ { print; next }

/^[[:space:]]*DROP[[:space:]]+PROCEDURE[[:space:]]+IF[[:space:]]+EXISTS/ {
    drop_line = $0
    if ((getline next_line) <= 0) {
        if (delim_set) { print "DELIMITER ;"; delim_set = 0 }
        print drop_line
        next
    }
    while (next_line ~ /^[[:space:]]*$/ || next_line ~ /^[[:space:]]*--/) {
        print drop_line
        drop_line = next_line
        if ((getline next_line) <= 0) {
            if (delim_set) { print "DELIMITER ;"; delim_set = 0 }
            print drop_line
            exit
        }
    }
    if (next_line !~ /^[[:space:]]*CREATE[[:space:]]+PROCEDURE/) {
        if (delim_set) { print "DELIMITER ;"; delim_set = 0 }
        print drop_line
        # 回放下一行以便正常规则继续处理
        $0 = next_line
    } else {
        if (!delim_set) { print "DELIMITER $$"; delim_set = 1 }
        print drop_line
        print next_line
        in_proc = 1
        next
    }
}

/^[[:space:]]*CREATE[[:space:]]+PROCEDURE/ {
    if (!delim_set) { print "DELIMITER $$"; delim_set = 1 }
    in_proc = 1; print; next
}

/^[[:space:]]*END\$\$$/ { in_proc = 0; print; next }

/^[[:space:]]*END;[[:space:]]*$/ {
    sub(/;[[:space:]]*$/, "$$")
    in_proc = 0; print; next
}

/^[[:space:]]*CALL[[:space:]].*\$\$$/ { print; next }

{
    if (delim_set && !in_proc) { print "DELIMITER ;"; delim_set = 0 }
    print
}

END { if (delim_set) print "DELIMITER ;" }
' "$input_sql" > "$output_sql"
