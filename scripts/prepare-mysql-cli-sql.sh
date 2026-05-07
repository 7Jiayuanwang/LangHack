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

awk '
BEGIN {
    in_proc = 0
    print "SET NAMES utf8mb4;"
}
/^[[:space:]]*CREATE[[:space:]]+PROCEDURE[[:space:]]+/ && in_proc == 0 {
    print "DELIMITER $$"
    print
    in_proc = 1
    next
}
in_proc == 1 && /^[[:space:]]*END;[[:space:]]*$/ {
    sub(/;[[:space:]]*$/, "$$")
    print
    print "DELIMITER ;"
    in_proc = 0
    next
}
{
    print
}
END {
    if (in_proc == 1) {
        exit 2
    }
}
' "$input_sql" > "$output_sql"
