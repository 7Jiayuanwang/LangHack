#!/usr/bin/env bash
# OData $filter 测试 - 关键修正: 空格必须用 %20 而非 +
# OData 里 + 是加号运算符, 会导致 "Edm.Boolean/Decimal not compatible" 错误
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"

ENV_FILE="/data/LangHack/docker/.env"
[ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"; APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
echo "appId = ${APP_ID}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }

# 用 python 做严格 URL 编码(空格→%20, 单引号保留或编码),然后直接拼到 URL
# filter_expr 传原始表达式如: 课程班号 eq 256726307
enc() { python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"; }

# 直接构造完整 URL(参数已编码), 不让 curl 再编码
call_raw() {
  local full_url="$1" ts sign
  ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s "$full_url" \
    -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}
show() { python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); v=d.get('value')
    if v is None: print('  错误:', json.dumps(d,ensure_ascii=False)[:250])
    else:
        print('  @api.count=%s, 返回 %d 条' % (d.get('@api.count'), len(v)))
        for r in v[:6]: print('   ', json.dumps(r,ensure_ascii=False))
except Exception as e: print('  解析失败:', e)
"; }

echo
echo "===== 测试1: 课程班号 eq 数字 (空格=%20) ====="
F=$(enc "课程班号 eq 256726307")
echo "  编码后 filter = $F"
call_raw "${SEL_URL}?\$filter=${F}" | show

echo
echo "===== 测试2: 课程班号 eq 带引号 ====="
F=$(enc "课程班号 eq '256726307'")
call_raw "${SEL_URL}?\$filter=${F}" | show

echo
echo "===== 测试3: 学号 eq ====="
F=$(enc "学号 eq '2021112010808'")
call_raw "${SEL_URL}?\$filter=${F}" | show

echo
echo "===== 测试4: \$count=true ====="
call_raw "${SEL_URL}?\$count=true&\$top=1" | python3 -c "import sys,json;d=json.load(sys.stdin);print('  @api.count =',d.get('@api.count'))" 2>/dev/null

echo
echo "===== 测试5: \$orderby 课程班号 desc ====="
O=$(enc "课程班号 desc")
call_raw "${SEL_URL}?\$orderby=${O}&\$top=5" | show

echo
echo "===== 测试6: 课程班号 gt 范围过滤 ====="
F=$(enc "课程班号 gt 256000000")
call_raw "${SEL_URL}?\$filter=${F}&\$top=5" | show

echo
echo "============================================="
echo "判读: 若测试1返回256726307的学生 → \$filter可用,方案彻底解决"
