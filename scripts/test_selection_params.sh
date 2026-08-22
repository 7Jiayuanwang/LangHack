#!/usr/bin/env bash
# 测试选课接口到底支持哪些原生查询参数
# 文档说 course_selection 原生参数是 XH(学号),之前用 KCBH 测错了
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"

ENV_FILE="/data/LangHack/docker/.env"
[ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"; APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
echo "appId = ${APP_ID}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }
call() {
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "$1" -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}
show() { python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); v=d.get('value')
    if v is None:
        print('  错误/无value:', json.dumps(d,ensure_ascii=False)[:300])
    else:
        print('  返回 %d 条' % len(v))
        for r in v[:8]: print('   ', json.dumps(r,ensure_ascii=False))
except Exception as e: print('  解析失败', e)
"; }

echo
echo "===== 测试1: XH(大写学号) 过滤 —— 文档说的原生参数 ====="
echo "  URL: ?XH=071515587"
call "${SEL_URL}?XH=071515587" | show

echo
echo "===== 测试2: xh(小写学号) 过滤 ====="
call "${SEL_URL}?xh=071515587" | show

echo
echo "===== 测试3: 学号(中文参数名) 过滤 ====="
call "${SEL_URL}?$(python3 -c "import urllib.parse;print('学号='+urllib.parse.quote('071515587'))")" | show

echo
echo "===== 测试4: 换个近期学号试试 (2023级) XH=2023112101479 ====="
call "${SEL_URL}?XH=2023112101479" | show

echo
echo "============================================="
echo "判读:"
echo " - 哪个测试返回了对应学号的选课记录 → 那个参数名可用"
echo " - 若都返回全表第一页(学号071515587那批) → 参数被忽略,确实只能全量"
echo " - 若测试4能返回2023级学生的选课 → 可按学号精确拉取,方案彻底可行"
