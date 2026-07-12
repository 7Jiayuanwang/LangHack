#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 调 CDSP 学校数据 API，拉取学生原始字段，检查是否有毕业/学籍状态
# GET 优先，IY1103 自动降级 POST+JSON（与后端 SchoolDataApiClientImpl 一致）
#
# 用法:
#   ./check-graduation-from-cdsp.sh                   # 拉前 5 条，展示所有字段
#   ./check-graduation-from-cdsp.sh --sample 20       # 拉前 20 条
#   ./check-graduation-from-cdsp.sh --id 2024122125031 # 查某个学号
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

SAMPLE=5
STUDENT_ID=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --sample) SAMPLE="${2:-5}"; shift 2 ;;
        --id)     STUDENT_ID="${2:-}"; shift 2 ;;
        -h|--help) sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo -e "${RED}未知参数: $1${NC}"; exit 2 ;;
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

# ─── 0. 预检 ─────────────────────────────
echo -e "${YELLOW}======================================${NC}"
echo -e "${YELLOW}CDSP 学生基本信息字段检测${NC}"
echo -e "${YELLOW}======================================${NC}"

if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    echo -e "${RED}错误: 容器 ${MYSQL_CONTAINER} 未运行${NC}"; exit 1
fi

# ─── 1. 读取 CDSP 认证 ─────────────────────
echo -e "${YELLOW}1. 读取 CDSP 认证...${NC}"
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
echo -e "   ${GREEN}✓ 已配置${NC} (appId: ${APP_ID:0:4}...${APP_ID: -2})"

# ─── 2. 签名 ─────────────────────────────
cdsp_sign() {
    local ts="$1"
    local raw="${APP_ID}${APP_SECRET}${ts}"
    local md5hex
    md5hex=$(echo -n "${raw}" | md5sum | cut -d' ' -f1)
    echo -n "${md5hex}" | base64 -w0 2>/dev/null || echo -n "${md5hex}" | base64
}

# ─── 3. CDSP API 调用（GET → IY1103 降级 POST+JSON） ──
# $1=URL, $2=JSON body, 结果写 $CDSP_OUT, 返回 HTTP code
cdsp_call() {
    local url="$1" json="$2" ts sign qs code

    ts=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    sign=$(cdsp_sign "${ts}")

    # GET
    qs=$(echo "${json}" | python3 -c "import json,sys,urllib.parse;print(urllib.parse.urlencode(json.load(sys.stdin)))" 2>/dev/null)
    code=$(curl -s -o "${CDSP_OUT}" -w '%{http_code}' \
        --connect-timeout 10 --max-time 30 \
        -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" \
        "${url}?${qs}")

    # IY1103 → POST
    if [ "${code}" = "400" ] && head -c 200 "${CDSP_OUT}" 2>/dev/null | grep -q 'IY1103'; then
        ts=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
        sign=$(cdsp_sign "${ts}")
        code=$(curl -s -o "${CDSP_OUT}" -w '%{http_code}' \
            --connect-timeout 10 --max-time 30 \
            -H "Content-Type: application/json" \
            -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" \
            -d "${json}" "${url}")
    fi
    echo "${code}"
}

CDSP_OUT=$(mktemp)
trap "rm -f ${CDSP_OUT}" EXIT

# ─── 4. 拉取样本 ─────────────────────────────
echo ""
echo -e "${YELLOW}2. 调 CDSP 拉取 ${SAMPLE} 条样本...${NC}"
CODE=$(cdsp_call "${CDSP_URL}" "{\"page\":\"1\",\"pageSize\":\"${SAMPLE}\"}")

if [ "${CODE}" != "200" ]; then
    echo -e "${RED}API 失败 HTTP ${CODE}${NC}"
    python3 -m json.tool < "${CDSP_OUT}" 2>/dev/null || cat "${CDSP_OUT}"
    exit 1
fi
echo -e "   ${GREEN}✓ 成功${NC}"

# ─── 5. 展示全部字段 ─────────────────────────────
echo ""
echo -e "${YELLOW}3. 原始 JSON 结构（前 800 字符）:${NC}"
head -c 800 "${CDSP_OUT}"
echo ""

FIRST=$(python3 -c "
import json, sys

with open('${CDSP_OUT}') as f:
    d = json.load(f)

# CDSP 返回格式多样，尝试所有已知形状提取 rows
rows = None

# Shape A: 直接是数组 [{...}, ...]
if isinstance(d, list):
    rows = d

# Shape B: {'data': [...]}
elif isinstance(d, dict) and 'data' in d:
    inner = d['data']
    if isinstance(inner, list):
        rows = inner
    elif isinstance(inner, dict) and 'rows' in inner:
        rows = inner['rows']

# Shape C: {'rows': [...]}
elif isinstance(d, dict) and 'rows' in d:
    rows = d['rows']

# Shape D: {'code': 200, 'result': [...]}  etc — 打印所有 keys 方便排查
if rows is None and isinstance(d, dict):
    top_keys = list(d.keys())
    print('TOP_KEYS:' + ','.join(top_keys))
    # 递归找第一个 list 值
    def find_list(obj, depth=0):
        if depth > 3: return None
        if isinstance(obj, list): return obj
        if isinstance(obj, dict):
            for v in obj.values():
                r = find_list(v, depth+1)
                if r: return r
        return None
    rows = find_list(d)

if rows is None:
    print('NO_DATA')
else:
    rows = [r for r in rows if isinstance(r, dict)]
    if rows:
        print(json.dumps(rows[0], ensure_ascii=False, indent=2))
    else:
        print('EMPTY_ROWS')
" 2>/dev/null)

if [ "${FIRST}" = "NO_DATA" ] || [ "${FIRST}" = "EMPTY_ROWS" ] || [ -z "${FIRST}" ]; then
    echo ""
    echo -e "${RED}未提取到行数据，请根据上方原始 JSON 结构手动确认${NC}"
    if echo "${FIRST}" | grep -q '^TOP_KEYS:'; then
        echo -e "${YELLOW}JSON 顶层 keys: ${FIRST#TOP_KEYS:}${NC}"
    fi
    exit 1
fi
echo ""
echo -e "${YELLOW}第一条记录:${NC}"
echo "${FIRST}"

# ─── 6. 毕业/学籍字段检测 ─────────────────────────
echo ""
echo -e "${YELLOW}4. 毕业/学籍相关字段:${NC}"

ALL_KEYS=$(echo "${FIRST}" | python3 -c "import json,sys; [print(k) for k in sorted(json.load(sys.stdin).keys())]" 2>/dev/null)
HITS=$(echo "${ALL_KEYS}" | grep -iE 'graduat|毕业|状态|status|离校|在校|学籍|byrq|xszt|xjzt|sfzx|sfby|rxny|bz' || true)

if [ -z "${HITS}" ]; then
    echo -e "   ${RED}✗ 未找到任何毕业/学籍/状态字段${NC}"
    echo ""
    echo -e "   CDSP 返回全部字段名:"
    echo "${ALL_KEYS}" | while read k; do echo "     - ${k}"; done
    echo ""
    echo -e "${YELLOW}结论: CDSP 不返回毕业/学籍状态，目前只能用学号前 4 位 < 2021 推测。${NC}"
else
    echo -e "   ${GREEN}✓ 可能相关:${NC}"
    echo "${HITS}" | while read k; do
        val=$(echo "${FIRST}" | python3 -c "import json,sys;print(json.load(sys.stdin).get('${k}',''))" 2>/dev/null)
        echo -e "     ${CYAN}${k}${NC} = ${val}"
    done
fi

# ─── 7. 按学号查 ─────────────────────────────
if [ -n "${STUDENT_ID}" ]; then
    echo ""
    echo -e "${YELLOW}5. 查询学号: ${STUDENT_ID}${NC}"
    CODE=$(cdsp_call "${CDSP_URL}" "{\"xh\":\"${STUDENT_ID}\"}")
    if [ "${CODE}" = "200" ]; then
        python3 -c "
import json
d=json.load(open('${CDSP_OUT}'))
rows=d.get('data',d).get('rows',d.get('data',[])) if isinstance(d,dict) else d
rows=rows if isinstance(rows,list) else []
r=[x for x in rows if str(x.get('xh',''))=='${STUDENT_ID}']
if r: print(json.dumps(r[0],ensure_ascii=False,indent=2))
else: print('未找到学号 ${STUDENT_ID}')
" 2>/dev/null
    else
        echo -e "${RED}查询失败 HTTP ${CODE}${NC}"
    fi
fi

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}检测完毕${NC}"
echo -e "${GREEN}======================================${NC}"
