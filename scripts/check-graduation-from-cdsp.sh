#!/bin/bash
# ═══════════════════════════════════════════════════════════════════════
# 调 CDSP 学校数据接口，拉取学生原始字段，检查是否有毕业/学籍状态
#
# 用法:
#   ./check-graduation-from-cdsp.sh                    # 拉前 5 条，展示所有字段
#   ./check-graduation-from-cdsp.sh --sample 20        # 拉前 20 条
#   ./check-graduation-from-cdsp.sh --id 2024122125031 # 查某个学号的原始数据
#   ./check-graduation-from-cdsp.sh --ids ids.txt      # 批量查导入批次学号
# ═══════════════════════════════════════════════════════════════════════

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

SAMPLE=5
STUDENT_ID=""
IDS_FILE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --sample) SAMPLE="${2:-5}"; shift 2 ;;
        --id)     STUDENT_ID="${2:-}"; shift 2 ;;
        --ids)    IDS_FILE="${2:-}"; shift 2 ;;
        -h|--help)
            sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
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

# ─── 1. 读取 CDSP 认证信息 ─────────────────────
echo -e "${YELLOW}1. 读取 CDSP 认证配置...${NC}"
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
    echo -e "${RED}错误: CDSP API 认证未配置 (api_app_id / api_app_secret)${NC}"
    exit 1
fi
echo -e "   ${GREEN}✓ 认证已配置${NC} (appId: ${APP_ID:0:4}...${APP_ID: -2})"

# ─── 2. 签名函数 ─────────────────────────────
cdsp_sign() {
    local ts="$1"
    local raw="${APP_ID}${APP_SECRET}${ts}"
    local md5hex
    md5hex=$(echo -n "${raw}" | md5sum | cut -d' ' -f1)
    echo -n "${md5hex}" | base64 -w0
}

# ─── 3. 调 CDSP API ─────────────────────────
echo ""
echo -e "${YELLOW}2. 调用 CDSP student_basic_info 接口...${NC}"
TS=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
SIGN=$(cdsp_sign "${TS}")

API_RESPONSE=$(mktemp)
HTTP_CODE=$(curl -s -o "${API_RESPONSE}" -w '%{http_code}' \
    --connect-timeout 10 --max-time 30 \
    -H "appId: ${APP_ID}" \
    -H "timestamp: ${TS}" \
    -H "sign: ${SIGN}" \
    "${CDSP_URL}?page=1&pageSize=5")

if [ "${HTTP_CODE}" != "200" ]; then
    echo -e "${RED}API 调用失败 HTTP ${HTTP_CODE}${NC}"
    echo "--- 响应内容 ---"
    cat "${API_RESPONSE}" | python3 -m json.tool 2>/dev/null || cat "${API_RESPONSE}"
    rm -f "${API_RESPONSE}"
    exit 1
fi

echo -e "   ${GREEN}✓ API 调用成功${NC}"

# ─── 4. 解析并展示字段 ─────────────────────────
echo ""
echo -e "${YELLOW}3. CDSP 返回的原始字段（全部）:${NC}"
echo ""

# 提取第一条记录的所有字段名
FIRST_RECORD=$(python3 -c "
import json, sys
data = json.load(open('${API_RESPONSE}'))
rows = data.get('data', data).get('rows', data.get('data', []))
if isinstance(rows, list) and len(rows) > 0:
    print(json.dumps(rows[0], ensure_ascii=False, indent=2))
elif isinstance(data, list) and len(data) > 0:
    print(json.dumps(data[0], ensure_ascii=False, indent=2))
else:
    print('NO_DATA')
" 2>/dev/null)

if [ "${FIRST_RECORD}" = "NO_DATA" ] || [ -z "${FIRST_RECORD}" ]; then
    echo -e "${RED}API 返回了空数据${NC}"
    echo "原始响应:"
    cat "${API_RESPONSE}" | python3 -m json.tool 2>/dev/null || head -5 "${API_RESPONSE}"
    rm -f "${API_RESPONSE}"
    exit 1
fi

echo "${FIRST_RECORD}"

# ─── 5. 检测毕业/学籍相关字段 ─────────────────────
echo ""
echo -e "${YELLOW}4. 毕业/学籍相关字段检测:${NC}"

ALL_KEYS=$(echo "${FIRST_RECORD}" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for k in sorted(data.keys()):
    print(k)
" 2>/dev/null)

GRAD_KEYS=$(echo "${ALL_KEYS}" | grep -iE 'graduat|毕业|状态|status|离校|在校|学籍|byrq|xszt|xjzt|sfzx|sfby|rxny|bz' || true)

if [ -z "${GRAD_KEYS}" ]; then
    echo -e "   ${RED}✗ 未找到任何毕业/学籍/状态相关字段${NC}"
    echo ""
    echo -e "   CDSP 返回的全部字段名:"
    echo "${ALL_KEYS}" | while read k; do echo "     - ${k}"; done
    echo ""
    echo -e "${YELLOW}结论: CDSP student_basic_info 接口不返回毕业状态/学籍状态字段。${NC}"
    echo -e "${YELLOW}      目前只能用学号前 4 位 < 2021 推测毕业。${NC}"
else
    echo -e "   ${GREEN}✓ 找到可能相关的字段:${NC}"
    echo "${GRAD_KEYS}" | while read k; do
        val=$(echo "${FIRST_RECORD}" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('${k}',''))" 2>/dev/null)
        echo -e "     ${CYAN}${k}${NC} = ${val}"
    done
fi

# ─── 6. 如果指定了学号，查具体学生的原始数据 ─────────────
if [ -n "${STUDENT_ID}" ]; then
    echo ""
    echo -e "${YELLOW}5. 查询指定学号: ${STUDENT_ID}${NC}"

    TS=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
    SIGN=$(cdsp_sign "${TS}")
    STUDENT_RESP=$(mktemp)

    # 按学号过滤（如果 API 支持过滤参数）
    SC=$(curl -s -w '%{http_code}' -o "${STUDENT_RESP}" \
        --connect-timeout 10 --max-time 30 \
        -H "appId: ${APP_ID}" \
        -H "timestamp: ${TS}" \
        -H "sign: ${SIGN}" \
        "${CDSP_URL}?xh=${STUDENT_ID}")

    if [ "${SC}" = "200" ]; then
        python3 -c "
import json
data = json.load(open('${STUDENT_RESP}'))
rows = data.get('data',data).get('rows', data.get('data',[])) if isinstance(data,dict) else data
if isinstance(rows, list) and len(rows) > 0:
    r = [x for x in rows if x.get('xh','') == '${STUDENT_ID}']
    if r:
        print(json.dumps(r[0], ensure_ascii=False, indent=2))
    else:
        print('学号 ${STUDENT_ID} 不在前几条返回中（或 API 不返回此学号）')
        print('尝试翻页后查找...')
else:
    print(json.dumps(data, ensure_ascii=False, indent=2)[:2000])
" 2>/dev/null
    else
        echo -e "${RED}查询失败 HTTP ${SC}${NC}"
    fi
    rm -f "${STUDENT_RESP}"
fi

# ─── 7. 批量查询导入批次学号 ───────────────────────
if [ -n "${IDS_FILE}" ] && [ -f "${IDS_FILE}" ]; then
    echo ""
    echo -e "${YELLOW}5. 批量查询文件: ${IDS_FILE}${NC}"
    TOTAL_IDS=$(wc -l < "${IDS_FILE}")
    BATCH_SIZE=5
    COUNT=0

    while IFS= read -r sid; do
        [ -z "${sid}" ] && continue
        COUNT=$((COUNT + 1))
        TS=$(date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000)))
        SIGN=$(cdsp_sign "${TS}")
        SR=$(mktemp)
        SC=$(curl -s -w '%{http_code}' -o "${SR}" \
            --connect-timeout 10 --max-time 30 \
            -H "appId: ${APP_ID}" \
            -H "timestamp: ${TS}" \
            -H "sign: ${SIGN}" \
            "${CDSP_URL}?xh=${sid}")

        RESULT=$(python3 -c "
import json
data=json.load(open('${SR}'))
rows=data.get('data',data).get('rows',data.get('data',[])) if isinstance(data,dict) else data
r=[x for x in (rows if isinstance(rows,list) else []) if x.get('xh','')=='${sid}']
if r:
    full = r[0]
    # 检查毕业相关字段
    for k,v in sorted(full.items()):
        if k.lower() in ('graduation_date','byrq','xszt','student_status','sfzx','sfby','rxny','xjzt','bz'):
            print(f'  [毕业相关] {k} = {v}')
    print('  OK | ${sid} | ' + str(full.get('xm','')) + ' | ' + str(full.get('yx','')) + ' | ' + str(full.get('nj','')) + ' | ' + str(full.get('bj','')))
else:
    print('  NOT_FOUND | ${sid}')
" 2>/dev/null)
        echo -e "   [${COUNT}/${TOTAL_IDS}] ${RESULT}"
        rm -f "${SR}"

        [ "${COUNT}" -ge 100 ] && { echo -e "   ${YELLOW}已达 100 条上限，停止批量查询${NC}"; break; }
        sleep 0.3
    done < "${IDS_FILE}"
fi

rm -f "${API_RESPONSE}"

echo ""
echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}检测完毕${NC}"
echo -e "${GREEN}======================================${NC}"
