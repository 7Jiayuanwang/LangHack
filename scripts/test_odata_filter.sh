#!/usr/bin/env bash
# 用官方 OData 语法测试选课接口过滤(中文字段名 + $filter)
# 官方文档明确支持: $filter=字段 eq '值', $count=true, $orderby
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"  # 学生选课

ENV_FILE="/data/LangHack/docker/.env"
[ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"; APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
echo "appId = ${APP_ID}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }

# URL 编码(用 python)
urlenc() { python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))" "$1"; }

# 发起请求:参数已编码好的完整 query string
call() {
  local qs="$1" ts sign
  ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -G "${SEL_URL}" --data-urlencode "$qs" \
    -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}
show() { python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); v=d.get('value')
    if v is None: print('  错误:', json.dumps(d,ensure_ascii=False)[:250])
    else:
        cnt=d.get('@api.count')
        print('  @api.count=%s, 返回 %d 条' % (cnt, len(v)))
        for r in v[:5]: print('   ', json.dumps(r,ensure_ascii=False))
except Exception as e: print('  解析失败:', e)
"; }

echo
echo "===== 测试1: \$filter 按课程班号过滤 (中文字段名) ====="
echo "  \$filter=课程班号 eq '256726307'"
call "\$filter=课程班号 eq '256726307'" | show

echo
echo "===== 测试2: \$filter 课程班号用数字(不带引号,因为是numeric) ====="
call "\$filter=课程班号 eq 256726307" | show

echo
echo "===== 测试3: \$filter 按学号过滤 ====="
call "\$filter=学号 eq '2021112010808'" | show

echo
echo "===== 测试4: \$count=true 拿全表总数 ====="
call "\$count=true" | python3 -c "
import sys,json
d=json.load(sys.stdin)
print('  @api.count(全表总数) =', d.get('@api.count'))
" 2>/dev/null

echo
echo "===== 测试5: \$orderby 课程班号 降序 —— 拿最大的课程班号(可能是最新的) ====="
call "\$orderby=课程班号 desc&\$top=5" | show

echo
echo "============================================="
echo "判读:"
echo " - 测试1或2 返回 256726307 的学生 → \$filter 按课程班号可用!方案彻底解决!"
echo " - 测试3 返回该学号选课 → 按学号可用"
echo " - 测试4 拿到总数 → 确认全表规模"
echo " - 测试5 若课程班号能降序 → 新数据可用 orderby 直接定位"
