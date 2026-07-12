#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 软删 users 表中 2021/2022 级用户，例外：
#   - 学号长度 = 8 位的保留
#   - 专业或学院含"动物医学"的保留
#
# 只软删 (deleted=1)，可以 UPDATE deleted=0 复活。
#
# 用法:
#   ./remove-users-2021-2022.sh              # dry-run
#   ./remove-users-2021-2022.sh --apply      # 交互确认后软删
#   ./remove-users-2021-2022.sh --apply --yes
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

APPLY=0
YES=0
for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=1 ;;
        --yes|-y) YES=1 ;;
        -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo -e "${RED}未知参数: $arg${NC}"; exit 2 ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/backups"

if [ ! -f "${REPO_ROOT}/docker/.env" ]; then
    echo -e "${RED}错误: ${REPO_ROOT}/docker/.env 不存在${NC}"; exit 1
fi
MYSQL_ROOT_PASSWORD=$(grep -E '^MYSQL_ROOT_PASSWORD=' "${REPO_ROOT}/docker/.env" | head -1 | cut -d'=' -f2-)
MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_DB="english_learning"

run_sql() {
    docker exec -i "${MYSQL_CONTAINER}" \
        mysql --default-character-set=utf8mb4 \
        -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DB}" "$@"
}

# {{APPEND_STATS}}

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}软删 2021/2022 级用户 (保留 8 位学号 + 动物医学)${NC}"
echo -e "${YELLOW}模式: $([ "$APPLY" = 1 ] && echo apply || echo dry-run)${NC}"
echo -e "${YELLOW}======================================${NC}"

# ─── 0. 预检 ─────────────────────────────
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi

# ─── 1. 匹配的 SQL 用一个 CTE 定义，多处复用 ─────
#
# 匹配条件：
#   - users.deleted=0
#   - 学号前 4 位 IN (2021, 2022)
#   - 学号长度 != 8
#   - LEFT JOIN school_student 后，专业/学院都不含"动物医学"
#     (关联不到 school_student 的也视为非动物医学 → 删)
#
# 用 CTE 之所以稳当：DELETE / UPDATE ... JOIN 里再重复写这一坨条件容易漏改
MATCH_CTE=$(cat <<'SQL'
WITH victim AS (
    SELECT u.id AS user_id,
           u.username,
           u.real_name,
           ss.major_name,
           ss.college_name,
           ss.class_name
    FROM users u
    LEFT JOIN school_student ss
      ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
    WHERE u.deleted = 0
      AND LEFT(u.username, 4) IN ('2021','2022')
      AND CHAR_LENGTH(u.username) <> 8
      AND (
            ss.student_id IS NULL
         OR (COALESCE(ss.major_name,'')   NOT LIKE '%动物医学%'
             AND COALESCE(ss.college_name,'') NOT LIKE '%动物医学%')
      )
)
SQL
)

# ─── 2. 统计 ─────────────────────────────
echo -e "${YELLOW}1. 统计...${NC}"
USERS_ACTIVE_BEFORE=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=0;')
TO_REMOVE=$(run_sql -sN <<EOF
${MATCH_CTE}
SELECT COUNT(*) FROM victim;
EOF
)
KEEP_ANIMAL_MED=$(run_sql -sN <<'SQL'
SELECT COUNT(*)
FROM users u
JOIN school_student ss
  ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND LEFT(u.username, 4) IN ('2021','2022')
  AND (ss.major_name LIKE '%动物医学%' OR ss.college_name LIKE '%动物医学%');
SQL
)
KEEP_8DIGIT=$(run_sql -sN <<'SQL'
SELECT COUNT(*)
FROM users
WHERE deleted = 0
  AND LEFT(username, 4) IN ('2021','2022')
  AND CHAR_LENGTH(username) = 8;
SQL
)

echo -e "   deleted=0 总数:                    ${USERS_ACTIVE_BEFORE}"
echo -e "   ${RED}将软删 (2021/2022 且 非动物医学 且 学号≠8位):${NC} ${RED}${TO_REMOVE}${NC}"
echo -e "   ${GREEN}保留 - 动物医学 (2021/2022):${NC}       ${GREEN}${KEEP_ANIMAL_MED}${NC}"
echo -e "   ${GREEN}保留 - 8 位学号 (2021/2022):${NC}       ${GREEN}${KEEP_8DIGIT}${NC}"

if [ "${TO_REMOVE}" -eq 0 ]; then
    echo -e "${GREEN}✓ 没有匹配的行，无需处理${NC}"; exit 0
fi

# {{APPEND_SAMPLES}}

# ─── 3. 样本：将删 ─────────────────────────────
echo ""
echo -e "${YELLOW}2. 将软删的样例 (前 5 条):${NC}"
run_sql <<EOF
${MATCH_CTE}
SELECT username AS 学号,
       real_name AS 姓名,
       COALESCE(major_name, '(无)') AS 专业,
       COALESCE(college_name, '(无)') AS 学院,
       COALESCE(class_name, '(无)') AS 班级
FROM victim
LIMIT 5;
EOF

# ─── 4. 样本：保留 - 动物医学 ─────────────────
echo ""
echo -e "${YELLOW}3. 保留 - 动物医学样例 (前 5 条):${NC}"
run_sql <<'SQL'
SELECT u.username AS 学号,
       u.real_name AS 姓名,
       ss.major_name AS 专业,
       ss.college_name AS 学院
FROM users u
JOIN school_student ss
  ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND LEFT(u.username, 4) IN ('2021','2022')
  AND (ss.major_name LIKE '%动物医学%' OR ss.college_name LIKE '%动物医学%')
LIMIT 5;
SQL

# ─── 5. 样本：保留 - 8 位学号 ─────────────────
echo ""
echo -e "${YELLOW}4. 保留 - 8 位学号样例 (前 5 条):${NC}"
run_sql <<'SQL'
SELECT u.username AS 学号,
       u.real_name AS 姓名,
       COALESCE(ss.major_name, '(无)') AS 专业,
       COALESCE(ss.college_name, '(无)') AS 学院
FROM users u
LEFT JOIN school_student ss
  ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND LEFT(u.username, 4) IN ('2021','2022')
  AND CHAR_LENGTH(u.username) = 8
LIMIT 5;
SQL

if [ "${APPLY}" -ne 1 ]; then
    echo ""
    echo -e "${YELLOW}dry-run 完毕。仔细看三组样例，无误后：${GREEN}$0 --apply${NC}"
    exit 0
fi

# {{APPEND_APPLY}}

# ─── 6. 交互确认 ─────────────────────────────
if [ "${YES}" -ne 1 ]; then
    echo ""
    read -rp "$(echo -e "${YELLOW}将软删 ${TO_REMOVE} 行。继续? (y/N) ${NC}")" ans
    case "${ans}" in y|Y|yes|YES) ;; *) echo "已取消"; exit 0 ;; esac
fi

# ─── 7. 备份 ─────────────────────────────
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/users-before-remove-2021-2022-$(date +%Y%m%d-%H%M%S).sql.gz"
echo -e "${YELLOW}备份 users 表 → ${BACKUP_FILE}${NC}"
docker exec -i "${MYSQL_CONTAINER}" \
    mysqldump --default-character-set=utf8mb4 \
    -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --skip-lock-tables --set-gtid-purged=OFF \
    "${MYSQL_DB}" users 2>/dev/null \
    | gzip > "${BACKUP_FILE}"
if [ ! -s "${BACKUP_FILE}" ]; then
    echo -e "${RED}错误: 备份为空，终止${NC}"; exit 1
fi
echo -e "   ${GREEN}✓ 备份完成 ($(du -h "${BACKUP_FILE}" | cut -f1))${NC}"

# ─── 8. 记录 timestamp 供回滚定位 ─────────────
STAMP_BEFORE=$(run_sql -sN <<<'SELECT NOW();')

# ─── 9. 执行软删 ─────────────────────────────
echo -e "${YELLOW}执行 UPDATE...${NC}"
run_sql <<'SQL'
UPDATE users u
LEFT JOIN school_student ss
  ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
SET u.deleted = 1,
    u.updated_time = NOW()
WHERE u.deleted = 0
  AND LEFT(u.username, 4) IN ('2021','2022')
  AND CHAR_LENGTH(u.username) <> 8
  AND (
        ss.student_id IS NULL
     OR (COALESCE(ss.major_name,'')   NOT LIKE '%动物医学%'
         AND COALESCE(ss.college_name,'') NOT LIKE '%动物医学%')
  );
SQL

# ─── 10. 校验 ─────────────────────────────
USERS_ACTIVE_AFTER=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=0;')
DELTA=$(( USERS_ACTIVE_BEFORE - USERS_ACTIVE_AFTER ))
echo -e "   deleted=0 减少: ${GREEN}-${DELTA}${NC}   预期: ${TO_REMOVE}"

if [ "${DELTA}" -eq "${TO_REMOVE}" ]; then
    echo -e "${GREEN}✓ 增量一致${NC}"
else
    echo -e "${YELLOW}⚠ 减少 ${DELTA} ≠ 预期 ${TO_REMOVE}${NC}"
fi

# ─── 11. 回滚指引 ─────────────────────────────
echo ""
echo -e "${YELLOW}如需回滚:${NC}"
cat <<HINT
  # 方案 A: 用 updated_time 定位本次软删的行，翻回来
  docker exec -i ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
      -uroot -p<PW> ${MYSQL_DB} <<'SQL'
      UPDATE users
         SET deleted = 0
       WHERE deleted = 1
         AND LEFT(username, 4) IN ('2021','2022')
         AND updated_time >= '${STAMP_BEFORE}';
  SQL

  # 方案 B: 从备份完全恢复 users 表
  gunzip -c ${BACKUP_FILE} \\
    | docker exec -i ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
        -uroot -p<PW> ${MYSQL_DB}
HINT

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}完成${NC}"
echo -e "${GREEN}======================================${NC}"
