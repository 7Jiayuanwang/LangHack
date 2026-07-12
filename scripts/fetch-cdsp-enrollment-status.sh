#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 分页拉取 CDSP DS0104，把每个学生的学籍状态 UPSERT 到 cdsp_student_probe
#
# 只写这张探测表，不动 users / school_student / 任何生产表
# 拉完后可以在 MySQL 里 SELECT 该表验证字段值分布
#
# 用法:
#   ./fetch-cdsp-enrollment-status.sh                # dry-run，只拉 2 页
#   ./fetch-cdsp-enrollment-status.sh --apply        # 全量拉，UPSERT
#
# 卸载:
#   DROP TABLE cdsp_student_probe;
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

APPLY=0
PAGE_SIZE=500
SLEEP=0.2
for arg in "$@"; do
    case "$arg" in
        --apply) APPLY=1 ;;
        --page-size=*) PAGE_SIZE="${arg#*=}" ;;
        --sleep=*) SLEEP="${arg#*=}" ;;
        -h|--help) sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo -e "${RED}未知参数: $arg${NC}"; exit 2 ;;
    esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [ ! -f "${REPO_ROOT}/docker/.env" ]; then
    echo -e "${RED}错误: ${REPO_ROOT}/docker/.env 不存在${NC}"; exit 1
fi
MYSQL_ROOT_PASSWORD=$(grep -E '^MYSQL_ROOT_PASSWORD=' "${REPO_ROOT}/docker/.env" | head -1 | cut -d'=' -f2-)
MYSQL_CONTAINER="langhack-mysql-master"
MYSQL_DB="english_learning"
CDSP_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/DS0104"

run_sql() {
    docker exec -i "${MYSQL_CONTAINER}" \
        mysql --default-character-set=utf8mb4 \
        -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DB}" "$@"
}

# {{APPEND_PREFLIGHT_AND_TABLE}}

echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}CDSP 学籍状态探测拉取${NC}"
echo -e "${YELLOW}模式: $([ "$APPLY" = 1 ] && echo apply || echo 'dry-run (只拉 2 页)')${NC}"
echo -e "${YELLOW}pageSize=${PAGE_SIZE}  sleep=${SLEEP}s${NC}"
echo -e "${YELLOW}======================================${NC}"

# ─── 0. 预检 ─────────────────────────────
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi
for bin in curl python3 md5sum base64; do
    command -v "$bin" >/dev/null || { echo -e "${RED}错误: 缺少 $bin${NC}"; exit 1; }
done

# ─── 1. 读取 CDSP 认证 ─────────────────────
APP_ID=$(run_sql -sN <<'EOF'
SELECT config_value FROM system_config
WHERE config_group = 'school_data' AND config_key = 'api_app_id';
EOF
)
APP_SECRET=$(run_sql -sN <<'EOF'
SELECT config_value FROM system_config
WHERE config_group = 'school_data' AND config_key = 'api_app_secret';
EOF
)
if [ -z "${APP_ID}" ] || [ -z "${APP_SECRET}" ]; then
    echo -e "${RED}错误: CDSP API 认证未配置${NC}"; exit 1
fi
echo -e "${GREEN}✓ CDSP 认证已加载${NC}"

# ─── 2. 建表 ─────────────────────────────
run_sql <<'SQL'
CREATE TABLE IF NOT EXISTS cdsp_student_probe (
    student_id VARCHAR(50) NOT NULL PRIMARY KEY,
    student_name VARCHAR(100),
    grade VARCHAR(20),
    is_graduated VARCHAR(10) COMMENT '是否毕业生: 是/否',
    is_on_campus VARCHAR(10) COMMENT '是否在校: 是/否',
    enrollment_status VARCHAR(50) COMMENT '学籍状态: 毕业/有学籍/…',
    current_status VARCHAR(50) COMMENT '目前状况',
    enrollment_date VARCHAR(50) COMMENT '入学日期原值',
    fetched_at DATETIME NOT NULL,
    INDEX idx_on_campus (is_on_campus),
    INDEX idx_graduated (is_graduated),
    INDEX idx_enrollment_status (enrollment_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='CDSP DS0104 学籍状态探测缓存';
SQL
echo -e "${GREEN}✓ 探测表 cdsp_student_probe 就位${NC}"

# {{APPEND_CDSP_CALL_AND_LOOP}}

# ─── 3. CDSP 调用 (GET → IY1103 降级 POST) ────
cdsp_sign() {
    local ts="$1" raw md5hex
    raw="${APP_ID}${APP_SECRET}${ts}"
    md5hex=$(echo -n "${raw}" | md5sum | cut -d' ' -f1)
    echo -n "${md5hex}" | base64 -w0 2>/dev/null || echo -n "${md5hex}" | base64
}
CDSP_OUT=$(mktemp)
trap "rm -f ${CDSP_OUT}" EXIT

cdsp_call() {
    local url="$1" json="$2" ts sign qs code
    ts=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    sign=$(cdsp_sign "${ts}")
    qs=$(echo "${json}" | python3 -c "import json,sys,urllib.parse;print(urllib.parse.urlencode(json.load(sys.stdin)))" 2>/dev/null)
    code=$(curl -s -o "${CDSP_OUT}" -w '%{http_code}' \
        --connect-timeout 10 --max-time 60 \
        -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" \
        "${url}?${qs}")
    if [ "${code}" = "400" ] && head -c 200 "${CDSP_OUT}" 2>/dev/null | grep -q 'IY1103'; then
        ts=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
        sign=$(cdsp_sign "${ts}")
        code=$(curl -s -o "${CDSP_OUT}" -w '%{http_code}' \
            --connect-timeout 10 --max-time 60 \
            -H "Content-Type: application/json" \
            -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" \
            -d "${json}" "${url}")
    fi
    echo "${code}"
}

# ─── 4. 分页拉取循环 ─────────────────────────────
PAGE=1
TOTAL=0
MAX_PAGES=$([ "$APPLY" = 1 ] && echo 500 || echo 2)

while [ "${PAGE}" -le "${MAX_PAGES}" ]; do
    printf "  page=%3d ... " "${PAGE}"
    CODE=$(cdsp_call "${CDSP_URL}" "{\"page\":\"${PAGE}\",\"pageSize\":\"${PAGE_SIZE}\"}")
    if [ "${CODE}" != "200" ]; then
        echo -e "${RED}HTTP ${CODE}${NC}"
        python3 -m json.tool < "${CDSP_OUT}" 2>/dev/null || cat "${CDSP_OUT}"
        exit 1
    fi

# {{APPEND_UPSERT_AND_SUMMARY}}

    # Python 解析 value[] → 生成 UPSERT SQL → 管道到 mysql
    # 输出 "RAW FILTERED" 两个数：RAW 判分页结束，FILTERED 是本页命中过滤要 UPSERT 的行数
    STATS=$(python3 <<PY
import json, sys, io

with open("${CDSP_OUT}") as f:
    d = json.load(f)
rows = d.get("value") if isinstance(d, dict) else None
if not isinstance(rows, list):
    # 结构异常时打印 raw JSON 前 300 字给排错
    sys.stderr.write("[RAW_HEAD] " + open("${CDSP_OUT}").read()[:300] + "\n")
    print("0 0")
    sys.exit(0)

def esc(v):
    if v is None or v == "":
        return "NULL"
    return "'" + str(v).replace("\\\\", "\\\\\\\\").replace("'", "''") + "'"

with open("${CDSP_OUT}.sql", "w") as out:
    out.write("INSERT INTO cdsp_student_probe (student_id, student_name, grade, "
              "is_graduated, is_on_campus, enrollment_status, current_status, "
              "enrollment_date, fetched_at) VALUES\n")
    parts = []
    for r in rows:
        xh = r.get("学号")
        if not xh: continue
        # 只保留学号前 4 位 >= 2021 的，与 sync-users 的过滤一致
        if len(xh) < 4 or xh[:4] < "2021": continue
        parts.append("(" + ",".join([
            esc(xh),
            esc(r.get("姓名")),
            esc(r.get("年级")),
            esc(r.get("是否毕业生")),
            esc(r.get("是否在校")),
            esc(r.get("学籍状态")),
            esc(r.get("目前状况")),
            esc(r.get("入学日期")),
            "NOW()",
        ]) + ")")
    if not parts:
        print(f"{len(rows)} 0"); sys.exit(0)
    out.write(",\n".join(parts))
    out.write("\nON DUPLICATE KEY UPDATE "
              "student_name=VALUES(student_name), "
              "grade=VALUES(grade), "
              "is_graduated=VALUES(is_graduated), "
              "is_on_campus=VALUES(is_on_campus), "
              "enrollment_status=VALUES(enrollment_status), "
              "current_status=VALUES(current_status), "
              "enrollment_date=VALUES(enrollment_date), "
              "fetched_at=VALUES(fetched_at);\n")
print(f"{len(rows)} {len(parts)}")
PY
)

    RAW="${STATS%% *}"
    N="${STATS##* }"

    if [ -s "${CDSP_OUT}.sql" ]; then
        run_sql < "${CDSP_OUT}.sql"
        rm -f "${CDSP_OUT}.sql"
    fi
    TOTAL=$((TOTAL + N))
    echo -e "${GREEN}raw=${RAW} 命中=${N} (累计 ${TOTAL})${NC}"

    # 只有 CDSP 这页真的少于 pageSize (或 0)，才是数据结束
    if [ "${RAW}" -lt "${PAGE_SIZE}" ]; then
        echo -e "${GREEN}CDSP 返回 ${RAW} < ${PAGE_SIZE}，到达末页${NC}"
        break
    fi
    PAGE=$((PAGE + 1))
    sleep "${SLEEP}"
done

# ─── 5. 汇总 ─────────────────────────────
echo ""
echo -e "${YELLOW}汇总 (按 是否在校 / 是否毕业生 / 学籍状态):${NC}"
run_sql <<'SQL'
SELECT is_on_campus, is_graduated, enrollment_status, COUNT(*) AS n
FROM cdsp_student_probe
GROUP BY is_on_campus, is_graduated, enrollment_status
ORDER BY n DESC;
SQL

# {{APPEND_HINT}}

echo ""
if [ "${APPLY}" -eq 1 ]; then
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}拉取完成，共 ${TOTAL} 条${NC}"
    echo -e "${GREEN}======================================${NC}"
    cat <<HINT

下一步:
  # 1. 抽一批 2024 学号确认在读侧值 (期望: 是否在校='是')
  docker exec -it ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
      -uroot -p ${MYSQL_DB} \\
      -e "SELECT student_id, is_on_campus, is_graduated, enrollment_status
          FROM cdsp_student_probe
          WHERE student_id LIKE '2024%' LIMIT 5;"

  # 2. 抽一批老学号确认毕业侧值 (期望: 是否在校='否')
  docker exec -it ${MYSQL_CONTAINER} mysql --default-character-set=utf8mb4 \\
      -uroot -p ${MYSQL_DB} \\
      -e "SELECT student_id, is_on_campus, is_graduated, enrollment_status
          FROM cdsp_student_probe
          WHERE student_id LIKE '2018%' LIMIT 5;"

  # 3. 确认后再改 sync-users-from-school-student.sql 加过滤条件
HINT
else
    echo -e "${YELLOW}dry-run 完毕 (共 ${TOTAL} 条)。确认无异常后：${GREEN}$0 --apply${NC}"
fi
