#!/usr/bin/env bash
# 探测选课接口全表规模 + 确认末尾是否有新数据（无需全量拉取）
# 用二分/大步 $skip 探测总量，避免逐页拉 90 万条
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"

ENV_FILE="/data/LangHack/docker/.env"
[ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"
APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
echo "appId = ${APP_ID}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }

# 拉取 skip 处的一批数据，返回行数
fetch_at() {
  local skip="$1" top="${2:-1}"
  local ts sign
  ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "${SEL_URL}?\$top=${top}&\$skip=${skip}" \
    -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}

# 返回某个 skip 处是否有数据 (1=有, 0=空)
has_rows() {
  local resp; resp=$(fetch_at "$1" 1)
  echo "$resp" | python3 -c "import sys,json;d=json.load(sys.stdin);print(1 if d.get('value') else 0)" 2>/dev/null || echo 0
}

echo
echo "===== [1] 指数探测上界（找到第一个空位置）====="
lo=0; hi=1000
while [ "$(has_rows $hi)" = "1" ]; do
  lo=$hi
  hi=$((hi*2))
  echo "  skip=$hi 仍有数据，继续翻倍 ... (已确认 >= $lo 条)"
  if [ "$hi" -gt 5000000 ]; then echo "  超过500万，停止"; break; fi
done
echo "  上界锁定: 有数据的最大约在 $lo, 空位置在 $hi 之间"

echo
echo "===== [2] 二分逼近精确总量 ====="
while [ $((hi-lo)) -gt 500 ]; do
  mid=$(((lo+hi)/2))
  if [ "$(has_rows $mid)" = "1" ]; then lo=$mid; else hi=$mid; fi
done
echo "  ≈ 全表总记录数: $lo ~ $hi 条"
echo "  → 需要页数(每页1000): 约 $(( (hi/1000)+1 )) 页"

echo
echo "===== [3] 看末尾数据样例（最后100条里抽10条，确认新旧）====="
TAIL_SKIP=$((lo>100 ? lo-100 : 0))
fetch_at "$TAIL_SKIP" 10 | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d.get('value',[]):
    print('  学号=%s 课程班号=%s' % (r.get('学号'), r.get('课程班号')))
" 2>/dev/null

echo
echo "===== [4] 抽查中段数据（skip=全表一半）====="
MID_SKIP=$((lo/2))
fetch_at "$MID_SKIP" 5 | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d.get('value',[]):
    print('  学号=%s 课程班号=%s' % (r.get('学号'), r.get('课程班号')))
" 2>/dev/null

echo
echo "============================================="
echo "判读："
echo " - [2] 全表总量 → 确认1200页(120万条)上限是否够用"
echo " - [3][4] 学号若出现 2023/2024/2025 级(如23xxx/24xxx开头) → 新数据在表的中后段"
echo "   这能证明:当前学期数据在接口里存在,只是被10万条上限挡在了后面"
