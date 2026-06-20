#!/bin/bash
# 执行 04-apply-all-migrations.sql（V0-V19 整合版）
# 存储过程格式：
#   DROP PROCEDURE IF EXISTS _name;   <- 开始组
#   CREATE PROCEDURE _name()
#   BEGIN ... END;
#   CALL _name();
#   DROP PROCEDURE IF EXISTS _name;   <- 关闭组
#
# 认证优先级：
#   1. MYSQL_ROOT_PASSWORD (容器 init 阶段，root 账户)
#   2. MYSQL_PWD + DB_USERNAME (Java 备份还原后调用，使用应用账户)
set -e

SQL_FILE="/opt/langhack/04-apply-all-migrations.sql"
DB_NAME="${MYSQL_DATABASE:-english_learning}"

# 决定连接用户和密码
if [ -n "${MYSQL_ROOT_PASSWORD}" ]; then
    MYSQL_USER="root"
    MYSQL_PASS="${MYSQL_ROOT_PASSWORD}"
elif [ -n "${MYSQL_PWD}" ]; then
    MYSQL_USER="${DB_USERNAME:-root}"
    MYSQL_PASS="${MYSQL_PWD}"
else
    echo "[migrate] 错误: 需要 MYSQL_ROOT_PASSWORD 或 MYSQL_PWD 环境变量" >&2
    exit 1
fi

echo "[migrate] 开始执行增量迁移脚本..."
echo "[migrate] 时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "[migrate] 数据库: ${DB_NAME}, 用户: ${MYSQL_USER}"

# awk 状态机：
#   state=0  普通 SQL 区
#   state=1  过程组已开始（第一个 DROP PROCEDURE 之后）
#   state=2  正在过程体内（CREATE PROCEDURE 到 END; 之间）
#   state=3  已看到 CALL（等待关闭 DROP）
awk -v db="${DB_NAME}" '
BEGIN { state = 0 }

# 替换 USE 占位符（仅前10行）
NR <= 10 { gsub(/\$\{MYSQL_DATABASE\}/, db) }

# 注释和空行直接透传（不改变状态）
/^[[:space:]]*$/ || /^[[:space:]]*--/ { print; next }

# DROP PROCEDURE IF EXISTS
/^[[:space:]]*DROP[[:space:]]+PROCEDURE[[:space:]]+IF[[:space:]]+EXISTS/ {
    if (state == 0) {
        # 开始新的过程组
        print "DELIMITER $$"
        state = 1
        sub(/;[[:space:]]*$/, "$$")
        print
    } else if (state == 3) {
        # 关闭过程组的 DROP
        sub(/;[[:space:]]*$/, "$$")
        print
        print "DELIMITER ;"
        state = 0
    } else {
        # state==1 或 state==2 不应该出现，直接透传
        print
    }
    next
}

# CREATE PROCEDURE: 进入过程体
/^[[:space:]]*CREATE[[:space:]]+PROCEDURE/ {
    in_proc = 1
    state = 2
    print; next
}

# END; 且在过程体内（行首 END;）
/^END;[[:space:]]*$/ && state == 2 {
    print "END$$"
    state = 1
    next
}

# CALL xxx(); 在过程组内
/^[[:space:]]*CALL[[:space:]]/ && (state == 1 || state == 2) {
    sub(/;[[:space:]]*$/, "$$")
    print
    state = 3
    next
}

# 默认：state==2 内部行（IF/ALTER/SELECT 等，含 END IF; 等）直接透传
{ print }
' "$SQL_FILE" \
  | MYSQL_PWD="${MYSQL_PASS}" mysql -u "${MYSQL_USER}" \
    -h "${MYSQL_HOST:-127.0.0.1}" \
    -P "${MYSQL_PORT:-3306}" \
    --default-character-set=utf8mb4

echo "[migrate] 增量迁移完成 ✓"
