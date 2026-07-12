#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 基于 cdsp_student_probe，把 users 表里已毕业/已离校的学生软删
#
# 前置: 必须先跑过 ./fetch-cdsp-enrollment-status.sh --apply 填充探测表
#
# 剔除规则:
#   - 只碰 sync-users 脚本插入的行 (password='!NOLOGIN_SSO_ONLY' + sso_bound=0)
#     真人管理员 / 已经 SSO 登过的用户完全不动
#   - probe 表 是否在校='否' 或 是否毕业生='是' → 软删 (deleted=1)
#   - 软删而非物理删，出错可 UPDATE deleted=0 复活
#
# 用法:
#   ./remove-graduated-users.sh                # dry-run
#   ./remove-graduated-users.sh --apply        # 交互确认后软删
#   ./remove-graduated-users.sh --apply --yes  # 跳过交互
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

APPLY=0
YES=0
for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=1 ;;
        --yes|-y) YES=1 ;;
        -h|--help) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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

# {{APPEND_PREFLIGHT_AND_STATS}}

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}剔除已毕业用户 (软删)${NC}"
echo -e "${YELLOW}模式: $([ "$APPLY" = 1 ] && echo apply || echo dry-run)${NC}"
echo -e "${YELLOW}======================================${NC}"

# ─── 0. 预检 ─────────────────────────────
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi

PROBE_EXISTS=$(run_sql -sN <<EOF
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='${MYSQL_DB}' AND table_name='cdsp_student_probe';
EOF
)
if [ "${PROBE_EXISTS}" != "1" ]; then
    echo -e "${RED}错误: cdsp_student_probe 表不存在${NC}"
    echo -e "${YELLOW}请先跑: ./fetch-cdsp-enrollment-status.sh --apply${NC}"
    exit 1
fi

PROBE_COUNT=$(run_sql -sN <<<'SELECT COUNT(*) FROM cdsp_student_probe;')
if [ "${PROBE_COUNT}" -lt 100 ]; then
    echo -e "${RED}错误: cdsp_student_probe 只有 ${PROBE_COUNT} 条，明显不完整${NC}"
    echo -e "${YELLOW}请先跑: ./fetch-cdsp-enrollment-status.sh --apply${NC}"
    exit 1
fi
echo -e "${GREEN}✓ 探测表就绪 (${PROBE_COUNT} 条)${NC}"

# ─── 1. 显示 probe 表值域分布 ─────────────────
echo ""
echo -e "${YELLOW}1. probe 表值域 (前 10 组):${NC}"
run_sql <<'SQL'
SELECT is_on_campus AS 是否在校,
       is_graduated AS 是否毕业生,
       enrollment_status AS 学籍状态,
       COUNT(*) AS n
FROM cdsp_student_probe
GROUP BY is_on_campus, is_graduated, enrollment_status
ORDER BY n DESC
LIMIT 10;
SQL

# ─── 2. 统计将要软删的行数 ─────────────────
echo ""
echo -e "${YELLOW}2. 匹配统计...${NC}"
USERS_TOTAL=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=0;')
USERS_SCRIPT_INSERTED=$(run_sql -sN <<<"SELECT COUNT(*) FROM users WHERE deleted=0 AND password='!NOLOGIN_SSO_ONLY' AND sso_bound=0;")
TO_REMOVE=$(run_sql -sN <<'SQL'
SELECT COUNT(*)
FROM users u
JOIN cdsp_student_probe p
  ON p.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND u.password = '!NOLOGIN_SSO_ONLY'
  AND u.sso_bound = 0
  AND (p.is_on_campus = '否' OR p.is_graduated = '是');
SQL
)
NO_PROBE_DATA=$(run_sql -sN <<'SQL'
SELECT COUNT(*)
FROM users u
LEFT JOIN cdsp_student_probe p
  ON p.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND u.password = '!NOLOGIN_SSO_ONLY'
  AND u.sso_bound = 0
  AND p.student_id IS NULL;
SQL
)

echo -e "   users 表 deleted=0 总行数:         ${USERS_TOTAL}"
echo -e "   由 sync-users 插入的行:            ${USERS_SCRIPT_INSERTED}"
echo -e "   ${RED}将软删 (毕业/离校):${NC}                ${RED}${TO_REMOVE}${NC}"
echo -e "   ${YELLOW}CDSP 里找不到 (保留，不动):${NC}      ${YELLOW}${NO_PROBE_DATA}${NC}"

if [ "${TO_REMOVE}" -eq 0 ]; then
    echo -e "${GREEN}✓ 没有匹配到毕业生，无需处理${NC}"; exit 0
fi

# {{APPEND_SAMPLES_AND_APPLY}}

# ─── 3. 样例：将要软删的 5 条 ─────────────────
echo ""
echo -e "${YELLOW}3. 将要软删的样例 (前 5 条):${NC}"
run_sql <<'SQL'
SELECT u.username AS 学号,
       u.real_name AS 姓名,
       p.is_on_campus AS 是否在校,
       p.is_graduated AS 是否毕业生,
       p.enrollment_status AS 学籍状态
FROM users u
JOIN cdsp_student_probe p
  ON p.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND u.password = '!NOLOGIN_SSO_ONLY'
  AND u.sso_bound = 0
  AND (p.is_on_campus = '否' OR p.is_graduated = '是')
LIMIT 5;
SQL

# ─── 4. 样例：将要保留的 5 条 (对照) ─────────
echo ""
echo -e "${YELLOW}4. 保留的样例 (前 5 条，用于对照):${NC}"
run_sql <<'SQL'
SELECT u.username AS 学号,
       u.real_name AS 姓名,
       COALESCE(p.is_on_campus, '(无 probe)') AS 是否在校,
       COALESCE(p.is_graduated, '(无 probe)') AS 是否毕业生,
       COALESCE(p.enrollment_status, '(无 probe)') AS 学籍状态
FROM users u
LEFT JOIN cdsp_student_probe p
  ON p.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND u.password = '!NOLOGIN_SSO_ONLY'
  AND u.sso_bound = 0
  AND (p.student_id IS NULL
       OR (p.is_on_campus != '否' AND p.is_graduated != '是'))
LIMIT 5;
SQL

if [ "${APPLY}" -ne 1 ]; then
    echo ""
    echo -e "${YELLOW}dry-run 完毕。仔细看上面两组样例，确认无误后：${GREEN}$0 --apply${NC}"
    exit 0
fi

# ─── 5. 交互确认 ─────────────────────────────
if [ "${YES}" -ne 1 ]; then
    echo ""
    read -rp "$(echo -e "${YELLOW}将软删 ${TO_REMOVE} 行 (deleted=1)。继续? (y/N) ${NC}")" ans
    case "${ans}" in y|Y|yes|YES) ;; *) echo "已取消"; exit 0 ;; esac
fi

# {{APPEND_BACKUP_AND_UPDATE}}

# ─── 6. 备份 ─────────────────────────────
mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/users-before-remove-$(date +%Y%m%d-%H%M%S).sql.gz"
echo -e "${YELLOW}6. 备份 users 表 → ${BACKUP_FILE}${NC}"
docker exec -i "${MYSQL_CONTAINER}" \
    mysqldump --default-character-set=utf8mb4 \
    -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --skip-lock-tables --set-gtid-purged=OFF \
    "${MYSQL_DB}" users 2>/dev/null \
    | gzip > "${BACKUP_FILE}"
if [ ! -s "${BACKUP_FILE}" ]; then
    echo -e "${RED}错误: 备份文件为空，终止${NC}"; exit 1
fi
echo -e "   ${GREEN}✓ 备份完成 ($(du -h "${BACKUP_FILE}" | cut -f1))${NC}"

# ─── 7. 执行软删 ─────────────────────────────
BEFORE_DEL=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=1;')
echo -e "${YELLOW}7. 执行 UPDATE ...${NC}"
run_sql <<'SQL'
UPDATE users u
JOIN cdsp_student_probe p
  ON p.student_id = u.username COLLATE utf8mb4_unicode_ci
SET u.deleted = 1,
    u.updated_time = NOW()
WHERE u.deleted = 0
  AND u.password = '!NOLOGIN_SSO_ONLY'
  AND u.sso_bound = 0
  AND (p.is_on_campus = '否' OR p.is_graduated = '是');
SQL

# ─── 8. 校验 ─────────────────────────────
AFTER_DEL=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=1;')
DELTA=$(( AFTER_DEL - BEFORE_DEL ))
USERS_ACTIVE_AFTER=$(run_sql -sN <<<'SELECT COUNT(*) FROM users WHERE deleted=0;')
echo -e "   deleted=1 增量: ${GREEN}+${DELTA}${NC}   预期: ${TO_REMOVE}"
echo -e "   users deleted=0 剩余: ${USERS_ACTIVE_AFTER}"

if [ "${DELTA}" -eq "${TO_REMOVE}" ]; then
    echo -e "${GREEN}✓ 增量一致${NC}"
else
    echo -e "${YELLOW}⚠ 增量 ${DELTA} ≠ 预期 ${TO_REMOVE}，可能期间有并发写入${NC}"
fi

# ─── 9. 回滚指引 ─────────────────────────────
echo ""
echo -e "${YELLOW}如需回滚 (恢复本次软删):${NC}"
cat <<HINT
  # 方案 A: 批量翻转 deleted=1 → 0 (依据 updated_time 定位刚刚这次的软删)
  docker exec -i ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
      -uroot -p<PW> ${MYSQL_DB} <<'SQL'
      UPDATE users
         SET deleted = 0
       WHERE deleted = 1
         AND password = '!NOLOGIN_SSO_ONLY'
         AND sso_bound = 0
         AND updated_time >= '$(date -d '-5 minutes' '+%Y-%m-%d %H:%M:%S')';
  SQL

  # 方案 B: 从备份完全恢复 users 表
  gunzip -c ${BACKUP_FILE} \\
    | docker exec -i ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
        -uroot -p<PW> ${MYSQL_DB}
HINT

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}完成！用户管理页刷新即可看到毕业生已消失${NC}"
echo -e "${GREEN}======================================${NC}"
