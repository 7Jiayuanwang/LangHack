#!/usr/bin/env bash
# 直连 CDSP 测试：验证选课接口能否拉到 2026 秋的数据
# 从后端容器环境变量读取 appId/appSecret，复现官方签名算法
# 用法: bash test_cdsp_selection.sh [课程班号]
# 默认课程班号 256726307 (教师20160066 的 2026秋 网络安全)
set -e

KCBH="${1:-256726307}"
SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"  # 学生选课

# 1. 从后端容器读取认证信息
echo "[1] 读取后端容器的 appId / appSecret ..."
ENV_DUMP=$(docker exec langhack-backend printenv 2>/dev/null || true)
APP_ID=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_ID=" | cut -d= -f2-)
APP_SECRET=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_SECRET=" | cut -d= -f2-)

if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  echo "  容器环境变量没读到，尝试从 docker/.env 读取 ..."
  ENV_FILE="/data/LangHack/docker/.env"
  [ -f "$ENV_FILE" ] || ENV_FILE="/data/LangHack/.env"
  if [ -f "$ENV_FILE" ]; then
    APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
    APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$ENV_FILE" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
  fi
fi

if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  echo "  ✗ 仍未找到 appId/appSecret。请手动设置后重试："
  echo "    APP_ID=xxx APP_SECRET=yyy bash scripts/test_cdsp_selection.sh $KCBH"
  APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"
  APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
  [ -z "$APP_ID" ] && exit 1
fi
echo "  appId = ${APP_ID}"
echo "  appSecret = ${APP_SECRET:0:4}****"

# 2. 生成签名: sign = Base64( hex(MD5(appId+appSecret+timestamp)) )
gen_sign() {
  local ts="$1"
  local raw="${APP_ID}${APP_SECRET}${ts}"
  local md5hex
  md5hex=$(printf '%s' "$raw" | md5sum | cut -d' ' -f1)   # 32位小写hex
  printf '%s' "$md5hex" | base64 -w0                       # 对hex字符串做base64
}

# 通用请求函数
call_cdsp() {
  local url="$1"
  local ts sign
  ts=$(date +%s%3N)
  sign=$(gen_sign "$ts")
  curl -s -X GET "$url" \
    -H "appId: ${APP_ID}" \
    -H "timestamp: ${ts}" \
    -H "sign: ${sign}" \
    -H "Accept: application/json"
}

echo
echo "============================================="
echo "[2] 测试A：不带任何参数，拉第一页选课数据（看接口通不通）"
echo "============================================="
RESP_A=$(call_cdsp "${SEL_URL}?\$top=5")
echo "$RESP_A" | python3 -m json.tool 2>/dev/null | head -40 || echo "$RESP_A" | head -c 2000

echo
echo "============================================="
echo "[3] 测试B：带课程班号 KCBH=${KCBH} 过滤（预期可能报 400 IY1103）"
echo "============================================="
RESP_B=$(call_cdsp "${SEL_URL}?KCBH=${KCBH}")
echo "$RESP_B" | python3 -m json.tool 2>/dev/null | head -40 || echo "$RESP_B" | head -c 2000

echo
echo "============================================="
echo "[4] 测试C：拿到接口总数 (@api.count) —— 判断全表到底多少条"
echo "============================================="
RESP_C=$(call_cdsp "${SEL_URL}?\$top=1")
echo "$RESP_C" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin)
    cnt=d.get('@api.count') or d.get('@odata.count') or '未返回count字段'
    print('接口返回总记录数 @api.count =', cnt)
    vals=d.get('value',[])
    if vals:
        print('第一条样例:', json.dumps(vals[0], ensure_ascii=False))
except Exception as e:
    print('解析失败:', e)
" 2>/dev/null || echo "$RESP_C" | head -c 1000

echo
echo "============================================="
echo "判读："
echo " - 测试A 有 value 数组 → 接口通、认证OK"
echo " - 测试B 若返回该班号的学生 → CDSP 支持 KCBH 过滤(和之前结论相反,重大发现)"
echo " - 测试B 若报 400 IY1103 → 确认不支持过滤,只能全量拉"
echo " - 测试C 的 @api.count → 全表总条数,决定要拉多少页"
