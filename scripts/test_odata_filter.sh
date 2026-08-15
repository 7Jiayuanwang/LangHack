#!/usr/bin/env bash
# 用官方 OData 语法测试选课接口过滤 - 修正版
# 关键修正: 每个 $ 参数单独用 --data-urlencode 传, 避免 & 粘连和空格变+
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"  # 学生选课

ENV_FILE="/data/LangHack/docker/.env"
[ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"; APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
echo "appId = ${APP_ID}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }

# 用 curl -G + 多个 --data-urlencode, 每个参数独立编码
# 传参: call "参数1=值1" "参数2=值2" ...
call() {
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  local args=()
  for kv in "$@"; do
    args+=(--data-urlencode "$kv")
  done
  curl -s -G "${SEL_URL}" "${args[@]}" \
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
echo "===== 测试1: 课程班号 eq (数字,不带引号) ====="
call "\$filter=课程班号 eq 256726307" | show

echo
echo "===== 测试2: 课程班号 eq (带引号) ====="
call "\$filter=课程班号 eq '256726307'" | show

echo
echo "===== 测试3: 学号 eq ====="
call "\$filter=学号 eq '2021112010808'" | show

echo
echo "===== 测试4: \$count=true (单独) ====="
call "\$count=true" "\$top=1" | python3 -c "import sys,json;d=json.load(sys.stdin);print('  @api.count =',d.get('@api.count'))" 2>/dev/null

echo
echo "===== 测试5: \$orderby 课程班号 desc (单独参数) ====="
call "\$orderby=课程班号 desc" "\$top=5" | show

echo
echo "===== 测试6: 课程班号 gt (大于, 试范围过滤) ====="
call "\$filter=课程班号 gt 256000000" "\$top=5" | show

echo
echo "============================================="
echo "判读: 哪个测试返回了 value 数组且是目标数据 → 该语法可用"
