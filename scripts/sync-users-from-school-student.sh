#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 从 school_student 批量补建 users 行
# 用法:
#   ./sync-users-from-school-student.sh                # dry-run (默认)
#   ./sync-users-from-school-student.sh --apply        # 交互确认后 INSERT
#   ./sync-users-from-school-student.sh --apply --yes  # 跳过交互 (慎用)
#
# 数据安全护栏:
#   - 只 INSERT，从不 UPDATE / DELETE 已存在的 users 行
#   - LEFT JOIN 匹配所有同名 users (含软删) → 有则跳过，不撞 UNIQUE
#   - SQL 里 ON DUPLICATE KEY UPDATE 防并发 SSO 首登造成回滚
#   - --apply 前自动 mysqldump users 表到 backups/ (压缩)
#   - 执行前后校验 users 增量 == 预统计
#   - 结束打印回滚 SQL
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

APPLY=0
YES=0
for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=1 ;;
        --yes|-y) YES=1 ;;
        -h|--help)
            sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *) echo -e "${RED}未知参数: $arg${NC}"; exit 2 ;;
    esac
done

MIN_GRADE="2021"
MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_DB="english_learning"

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}同步 school_student → users${NC}"
echo -e "${YELLOW}模式: $([ "$APPLY" = 1 ] && echo apply || echo dry-run)   最低年级: ${MIN_GRADE}${NC}"
echo -e "${YELLOW}======================================${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups"

# {{APPEND_ENV_AND_PREFLIGHT}}

if [ ! -f "${REPO_ROOT}/docker/.env" ]; then
    echo -e "${RED}错误: ${REPO_ROOT}/docker/.env 不存在${NC}"; exit 1
fi
MYSQL_ROOT_PASSWORD=$(grep -E '^MYSQL_ROOT_PASSWORD=' "${REPO_ROOT}/docker/.env" | head -1 | cut -d'=' -f2-)
if [ -z "${MYSQL_ROOT_PASSWORD}" ]; then
    echo -e "${RED}错误: MYSQL_ROOT_PASSWORD 未设置${NC}"; exit 1
fi

run_sql() {
    docker exec -i "${MYSQL_CONTAINER}" \
        mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DB}" "$@"
}

# ─── 0. 预检 ─────────────────────────────
echo -e "${YELLOW}0. 预检...${NC}"
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi
if ! run_sql -sN <<<'SELECT 1;' >/dev/null 2>&1; then
    echo -e "${RED}错误: 数据库连不上${NC}"; exit 1
fi
for tbl in users school_student; do
    n=$(run_sql -sN <<EOF
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='${MYSQL_DB}' AND table_name='${tbl}';
EOF
)
    if [ "${n}" != "1" ]; then
        echo -e "${RED}错误: 表 ${tbl} 不存在${NC}"; exit 1
    fi
done
echo -e "   ${GREEN}✓ 容器 / 连接 / 表 均正常${NC}"

# {{APPEND_STATS_AND_APPLY}}

# ─── 1. 统计 ─────────────────────────────
echo -e "${YELLOW}1. 统计...${NC}"
USERS_BEFORE=$(run_sql -sN <<<'SELECT COUNT(*) FROM users;')
TOTAL_ROSTER=$(run_sql -sN <<<'SELECT COUNT(*) FROM school_student;')
PENDING=$(run_sql -sN <<EOF
SELECT COUNT(*)
FROM school_student ss
LEFT JOIN users u ON u.username = ss.student_id COLLATE utf8mb4_unicode_ci
WHERE u.id IS NULL
  AND CHAR_LENGTH(ss.student_id) >= 4
  AND LEFT(ss.student_id, 4) >= '${MIN_GRADE}';
EOF
)
SOFTDEL_HIT=$(run_sql -sN <<EOF
SELECT COUNT(*)
FROM school_student ss
JOIN users u ON u.username = ss.student_id COLLATE utf8mb4_unicode_ci AND u.deleted = 1
WHERE CHAR_LENGTH(ss.student_id) >= 4
  AND LEFT(ss.student_id, 4) >= '${MIN_GRADE}';
EOF
)
echo -e "   users 当前行数:       ${USERS_BEFORE}"
echo -e "   school_student 总数:  ${TOTAL_ROSTER}"
echo -e "   待补建 (会 INSERT):   ${GREEN}${PENDING}${NC}"
echo -e "   已软删同名 (会跳过):  ${SOFTDEL_HIT}"

if [ "${PENDING}" -eq 0 ]; then
    echo -e "${GREEN}✓ 无需补建，退出${NC}"; exit 0
fi

# ─── 2. 样例 ─────────────────────────────
echo -e "${YELLOW}2. 样例 (前 5 条):${NC}"
run_sql <<EOF
SELECT ss.student_id, ss.student_name, ss.college_name, ss.class_name
FROM school_student ss
LEFT JOIN users u ON u.username = ss.student_id COLLATE utf8mb4_unicode_ci
WHERE u.id IS NULL
  AND CHAR_LENGTH(ss.student_id) >= 4
  AND LEFT(ss.student_id, 4) >= '${MIN_GRADE}'
LIMIT 5;
EOF

if [ "${APPLY}" -ne 1 ]; then
    echo ""
    echo -e "${YELLOW}dry-run 完毕。确认后：${GREEN}$0 --apply${NC}"
    exit 0
fi

# {{APPEND_APPLY}}

# ─── 3. 交互确认 ─────────────────────────────
if [ "${YES}" -ne 1 ]; then
    echo ""
    read -rp "$(echo -e "${YELLOW}将向 users INSERT ${PENDING} 行。继续? (y/N) ${NC}")" ans
    case "${ans}" in
        y|Y|yes|YES) ;;
        *) echo "已取消"; exit 0 ;;
    esac
fi

# ─── 4. 备份 users 表 ─────────────────────────────
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/users-$(date +%Y%m%d-%H%M%S).sql.gz"
echo -e "${YELLOW}4. 备份 users 表 → ${BACKUP_FILE}${NC}"
docker exec -i "${MYSQL_CONTAINER}" \
    mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --skip-lock-tables --set-gtid-purged=OFF \
    "${MYSQL_DB}" users 2>/dev/null \
    | gzip > "${BACKUP_FILE}"
if [ ! -s "${BACKUP_FILE}" ]; then
    echo -e "${RED}错误: 备份文件为空，终止${NC}"; exit 1
fi
echo -e "   ${GREEN}✓ 备份完成 ($(du -h "${BACKUP_FILE}" | cut -f1))${NC}"

# ─── 5. 执行 INSERT ─────────────────────────────
echo -e "${YELLOW}5. 执行 INSERT ...${NC}"
run_sql < "${SCRIPT_DIR}/sync-users-from-school-student.sql"

# ─── 6. 增量校验 ─────────────────────────────
echo -e "${YELLOW}6. 校验...${NC}"
USERS_AFTER=$(run_sql -sN <<<'SELECT COUNT(*) FROM users;')
DELTA=$(( USERS_AFTER - USERS_BEFORE ))
REMAIN=$(run_sql -sN <<EOF
SELECT COUNT(*)
FROM school_student ss
LEFT JOIN users u ON u.username = ss.student_id COLLATE utf8mb4_unicode_ci
WHERE u.id IS NULL
  AND CHAR_LENGTH(ss.student_id) >= 4
  AND LEFT(ss.student_id, 4) >= '${MIN_GRADE}';
EOF
)
echo -e "   users 增量: ${GREEN}+${DELTA}${NC}   预期: ${PENDING}   剩余待补: ${REMAIN}"

if [ "${REMAIN}" -eq 0 ] && [ "${DELTA}" -eq "${PENDING}" ]; then
    echo -e "${GREEN}✓ 完全一致${NC}"
elif [ "${REMAIN}" -eq 0 ]; then
    echo -e "${YELLOW}⚠ 已达目标，但 delta ${DELTA} ≠ 预期 ${PENDING}${NC}"
    echo -e "${YELLOW}  常见原因: 期间有 SSO 首登抢先 INSERT 了几条，属于正常${NC}"
else
    echo -e "${RED}✗ 仍有 ${REMAIN} 条未覆盖，请人工检查${NC}"; exit 1
fi

# {{APPEND_ROLLBACK}}

# ─── 7. 回滚指引 ─────────────────────────────
echo ""
echo -e "${YELLOW}如需回滚:${NC}"
cat <<HINT
  # 方案 A (推荐): 软删本次新建的行 (以 password 占位串 + sso_bound=0 定位)
  docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p<PW> ${MYSQL_DB} <<'SQL'
      UPDATE users
         SET deleted = 1, updated_time = NOW()
       WHERE password = '!NOLOGIN_SSO_ONLY'
         AND sso_bound = 0
         AND created_time >= '$(date -d '-1 hour' '+%Y-%m-%d %H:%M:%S')';
  SQL

  # 方案 B: 从备份完全恢复 users 表
  gunzip -c ${BACKUP_FILE} \\
    | docker exec -i ${MYSQL_CONTAINER} mysql -uroot -p<PW> ${MYSQL_DB}
HINT

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}完成！用户管理页刷新即可看到新学生${NC}"
echo -e "${GREEN}======================================${NC}"
