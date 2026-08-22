#!/usr/bin/env bash
# ============================================================================
# 诊断 teacher_courses 拉取量过大的根因：CDSP 到底认不认 学年(xn)/学期(xq) 过滤参数？
# 直连 CDSP，对比不同过滤组合返回的条数（@api.count / value 长度），不落库。
#
# 结论指引：
#   若 带 xn/xq 的返回条数 << 不带时 → CDSP 认服务端过滤 → 直接加 xn/xq 参数即可
#   若 带 xn/xq 与不带 条数相同/依旧全表 → CDSP 忽略过滤 → 必须客户端按学年学期再筛
#
# 用法:
#   bash scripts/diag_teacher_courses_term.sh
#   XN=2026 XQ=秋 SAMPLE_KCDM=1363220 bash scripts/diag_teacher_courses_term.sh
#   APP_ID=xxx APP_SECRET=yyy bash scripts/diag_teacher_courses_term.sh   # 手动传鉴权
# ============================================================================
set -e

TC_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497"   # teacher_courses 教师任课

XN="${XN:-2026}"              # 学年：单起始年份，如 2026 表示 2026-2027 学年
XQ="${XQ:-秋}"                # 学期：秋=第一学期(上学期), 春=第二学期(下学期)
SAMPLE_KCDM="${SAMPLE_KCDM:-1363220}"   # 外国语学院一门课的课程代码，用于叠加 kcdm 测试

# 1. 读取认证信息（容器 env → .env 文件 → 环境变量）
ENV_DUMP=$(docker exec langhack-backend printenv 2>/dev/null || true)
APP_ID=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_ID=" | cut -d= -f2-)
APP_SECRET=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_SECRET=" | cut -d= -f2-)
if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  for f in /data/LangHack/docker/.env /data/LangHack/.env ./docker/.env ./.env; do
    [ -f "$f" ] || continue
    APP_ID=$(grep -E "^SCHOOL_API_APP_ID=" "$f" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
    APP_SECRET=$(grep -E "^SCHOOL_API_APP_SECRET=" "$f" | cut -d= -f2- | tr -d '"'"'"'' | tr -d '\r')
    [ -n "$APP_ID" ] && break
  done
fi
APP_ID="${APP_ID:-$SCHOOL_API_APP_ID}"; APP_SECRET="${APP_SECRET:-$SCHOOL_API_APP_SECRET}"
if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  echo "✗ 未找到 appId/appSecret，请用 APP_ID=xxx APP_SECRET=yyy bash $0"; exit 1
fi
echo "appId=${APP_ID}  appSecret=${APP_SECRET:0:4}****  xn=${XN}  xq=${XQ}  sampleKcdm=${SAMPLE_KCDM}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }
urlenc() { python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"; }
call() {  # $1=完整URL
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "$1" -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}
# 打印返回条数 + 学年学期分布样本，用于判断过滤是否生效
show_count() { python3 -c "
import sys,json,collections
try:
    d=json.load(sys.stdin); v=d.get('value',[])
    cnt=d.get('@api.count') or d.get('@odata.count')
    print('  本页返回条数:', len(v), '| @count:', cnt)
    terms=collections.Counter()
    for r in v[:200]:
        xn=r.get('xn') or r.get('XN') or r.get('学年')
        xq=r.get('xq') or r.get('XQ') or r.get('学期')
        terms[(str(xn),str(xq))]+=1
    print('  样本(前200条)学年学期分布:', dict(terms))
except Exception as e:
    print('  解析失败/非JSON:', e)
" 2>/dev/null; }

echo
echo "===== 基线: 不带任何过滤，\$top=200（看正常一页返回什么） ====="
call "${TC_URL}?\$top=200" | show_count

echo
echo "===== 测试A: 只带 xn=${XN}（\$top=200） ====="
call "${TC_URL}?xn=${XN}&\$top=200" | show_count

echo
echo "===== 测试B: 带 xn=${XN} + xq=${XQ}（\$top=200） ====="
call "${TC_URL}?xn=${XN}&xq=$(urlenc "$XQ")&\$top=200" | show_count

echo
echo "===== 测试C: OData \$filter 学年 eq ${XN}（\$top=200） ====="
call "${TC_URL}?\$filter=$(urlenc "学年 eq ${XN}")&\$top=200" | show_count

echo
echo "===== 测试D: kcdm=${SAMPLE_KCDM} + xn=${XN} + xq=${XQ}（\$top=200） ====="
call "${TC_URL}?kcdm=${SAMPLE_KCDM}&xn=${XN}&xq=$(urlenc "$XQ")&\$top=200" | show_count

echo
echo "===== 测试E: \$filter 组合 学年 eq ${XN} and 学期 eq '${XQ}'（\$top=200，这是最终要用的过滤串） ====="
call "${TC_URL}?\$filter=$(urlenc "学年 eq ${XN} and 学期 eq '${XQ}'")&\$top=200" | show_count

echo
echo "===== 测试F: \$filter 组合再叠加课程代码 课程代码 eq ${SAMPLE_KCDM}（能否进一步收窄到单门课） ====="
call "${TC_URL}?\$filter=$(urlenc "学年 eq ${XN} and 学期 eq '${XQ}' and 课程代码 eq ${SAMPLE_KCDM}")&\$top=200" | show_count

echo
echo "===== 判读 ====="
echo " 关键看 测试E：若分布只剩 (${XN},${XQ}) 且条数明显收窄 → 用 \$filter=学年 eq ${XN} and 学期 eq '${XQ}'"
echo " 测试F 若也能返回且只剩该 kcdm → 还能叠加 课程代码，把每次请求压到单门课（最优）"
echo " 测试F 若返回 0/报错 → 课程代码字段名不对或不支持叠加，退回只按学年学期过滤 + 客户端按 kcdm 筛"
