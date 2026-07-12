#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 检测毕业学生（学号前 4 位 < 指定年份，默认 2021）
# 纯只读，不做任何修改。用于确认可以安全清理的毕业生范围。
#
# 用法:
#   ./check-graduated-students.sh                # 列出所有毕业生
#   ./check-graduated-students.sh --min 2020     # 只看学号 < 2020 的
#   ./check-graduated-students.sh --role ADMIN   # 只看某角色的毕业生
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

MAX_GRADE="2021"
ROLE_FILTER=""
for arg in "$@"; do
    case "$arg" in
        --min) MAX_GRADE="${2:-2021}"; shift ;;
        --role) ROLE_FILTER="${2:-}"; shift ;;
        -h|--help)
            sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        --min=*) MAX_GRADE="${arg#*=}" ;;
        --role=*) ROLE_FILTER="${arg#*=}" ;;
        *) ;;  # 忽略未知参数，纯读脚本不需要严格报错
    esac
    shift 2>/dev/null || true
done

MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_DB="english_learning"

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}毕业学生检测  学号 < ${MAX_GRADE}${NC}"
[ -n "${ROLE_FILTER}" ] && echo -e "${YELLOW}角色过滤: ${ROLE_FILTER}${NC}"
echo -e "${YELLOW}======================================${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ ! -f "${REPO_ROOT}/docker/.env" ]; then
    echo -e "${RED}错误: ${REPO_ROOT}/docker/.env 不存在${NC}"; exit 1
fi
MYSQL_ROOT_PASSWORD=$(grep -E '^MYSQL_ROOT_PASSWORD=' "${REPO_ROOT}/docker/.env" | head -1 | cut -d'=' -f2-)
if [ -z "${MYSQL_ROOT_PASSWORD}" ]; then
    echo -e "${RED}错误: MYSQL_ROOT_PASSWORD 未设置${NC}"; exit 1
fi

run_sql() {
    docker exec -i "${MYSQL_CONTAINER}" \
        mysql --default-character-set=utf8mb4 \
        -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DB}" "$@"
}

# ─── 0. 预检 ─────────────────────────────
echo -e "${YELLOW}0. 预检...${NC}"
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi
if ! run_sql -sN <<<'SELECT 1;' >/dev/null 2>&1; then
    echo -e "${RED}错误: 数据库连不上${NC}"; exit 1
fi
echo -e "   ${GREEN}✓ 容器 / 连接 正常${NC}"

# ─── 1. 按角色分布 ─────────────────────────────
echo -e "${YELLOW}1. 毕业学生 — 按角色分布:${NC}"

ROLE_SQL="SELECT role, COUNT(*) AS cnt
FROM users
WHERE deleted = 0
  AND CHAR_LENGTH(username) >= 4
  AND LEFT(username, 4) < '${MAX_GRADE}'"
[ -n "${ROLE_FILTER}" ] && ROLE_SQL="${ROLE_SQL} AND role = '${ROLE_FILTER}'"
ROLE_SQL="${ROLE_SQL} GROUP BY role ORDER BY role;"

run_sql -t <<< "${ROLE_SQL}"

TOTAL=$(run_sql -sN <<EOF
SELECT COUNT(*)
FROM users
WHERE deleted = 0
  AND CHAR_LENGTH(username) >= 4
  AND LEFT(username, 4) < '${MAX_GRADE}'
$([ -n "${ROLE_FILTER}" ] && echo "  AND role = '${ROLE_FILTER}'");
EOF
)
echo -e "   ─────────────────────"
echo -e "   毕业生总计: ${YELLOW}${TOTAL}${NC}"

if [ "${TOTAL}" -eq 0 ]; then
    echo -e "${GREEN}✓ 无毕业学生，退出${NC}"; exit 0
fi

# ─── 2. 在名册中的覆盖率 ─────────────────────────────
echo ""
echo -e "${YELLOW}2. 名册覆盖率:${NC}"
IN_ROSTER=$(run_sql -sN <<EOF
SELECT COUNT(*)
FROM users u
JOIN school_student ss ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND CHAR_LENGTH(u.username) >= 4
  AND LEFT(u.username, 4) < '${MAX_GRADE}'
$([ -n "${ROLE_FILTER}" ] && echo "  AND u.role = '${ROLE_FILTER}'");
EOF
)
NOT_IN_ROSTER=$(( TOTAL - IN_ROSTER ))
echo -e "   在 school_student 中: ${GREEN}${IN_ROSTER}${NC}"
echo -e "   不在 school_student 中: ${YELLOW}${NOT_IN_ROSTER}${NC}"

# ─── 3. 年级分布 ─────────────────────────────
echo ""
echo -e "${YELLOW}3. 按入学年份分布:${NC}"
run_sql -t <<EOF
SELECT LEFT(username, 4) AS 入学年份, COUNT(*) AS 人数
FROM users
WHERE deleted = 0
  AND CHAR_LENGTH(username) >= 4
  AND LEFT(username, 4) < '${MAX_GRADE}'
$([ -n "${ROLE_FILTER}" ] && echo "  AND role = '${ROLE_FILTER}'")
GROUP BY 入学年份
ORDER BY 入学年份;
EOF

# ─── 4. 详细清单 ─────────────────────────────
echo ""
echo -e "${YELLOW}4. 毕业学生清单:${NC}"
run_sql -t <<EOF
SELECT u.id, u.username, u.real_name, u.role, LEFT(u.username,4) AS 年级,
       ss.college_name, ss.class_name,
       u.created_time
FROM users u
LEFT JOIN school_student ss ON ss.student_id = u.username COLLATE utf8mb4_unicode_ci
WHERE u.deleted = 0
  AND CHAR_LENGTH(u.username) >= 4
  AND LEFT(u.username, 4) < '${MAX_GRADE}'
$([ -n "${ROLE_FILTER}" ] && echo "  AND u.role = '${ROLE_FILTER}'")
ORDER BY u.username;
EOF

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}检测完毕。需要清理时运行:${NC}"
echo -e "   ${YELLOW}./scripts/purge-graduated-students.sh --apply${NC}"
echo -e "${GREEN}======================================${NC}"
