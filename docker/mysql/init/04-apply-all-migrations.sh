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

# ── 决定 MySQL 连接参数 ──────────────────────────────────
# 容器 init 阶段 MySQL 以 --skip-networking 启动（port:0，仅 socket），
# 此时 -h 127.0.0.1 会失败。Java 备份还原后调用时 MySQL 已正常监听 TCP。
# MYSQL_ROOT_PASSWORD 存在 → init 阶段，用 socket（不带 -h/-P）
# 否则 → Java 还原场景，用 TCP
if [ -n "${MYSQL_ROOT_PASSWORD}" ]; then
    MYSQL_CONN_ARGS=""
    echo "[migrate] 连接模式: socket（容器 init 阶段）"
else
    MYSQL_CONN_ARGS="-h ${MYSQL_HOST:-127.0.0.1} -P ${MYSQL_PORT:-3306}"
    echo "[migrate] 连接模式: TCP（Java 还原场景）"
fi

# ── 等待 MySQL 就绪 ──────────────────────────────────────
# 03-import-backup.sh 导入大体积全量备份后，MySQL 进程会重启。
# 重启期间连接会失败（ERROR 2003 或 socket 不可用）。
# 这里加入就绪等待循环，避免迁移脚本因连接失败而中断。
MAX_WAIT=180  # 最长等待 180 秒（大备份后重启可能需要 ~140s）
WAITED=0
while [ "$WAITED" -lt "$MAX_WAIT" ]; do
    if MYSQL_PWD="${MYSQL_PASS}" mysql -u "${MYSQL_USER}" \
        ${MYSQL_CONN_ARGS} \
        --default-character-set=utf8mb4 \
        -e "SELECT 1" "${DB_NAME}" >/dev/null 2>&1; then
        if [ "$WAITED" -gt 0 ]; then
            echo "[migrate] MySQL 已就绪（等待了 ${WAITED}s）"
        fi
        break
    fi
    WAITED=$((WAITED + 2))
    if [ "$WAITED" -eq 2 ]; then
        echo "[migrate] 等待 MySQL 就绪（大备份导入后可能需要重启）..."
    fi
    sleep 2
done

if [ "$WAITED" -ge "$MAX_WAIT" ]; then
    echo "[migrate] [ERROR] 等待 MySQL 就绪超时（${MAX_WAIT}s），放弃执行迁移" >&2
    exit 1
fi

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
    ${MYSQL_CONN_ARGS} \
    --default-character-set=utf8mb4

echo "[migrate] 增量迁移完成 ✓"
