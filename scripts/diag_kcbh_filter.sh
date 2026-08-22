#!/usr/bin/env bash
# ============================================================================
# 诊断 chain fetch studentCount=0 的根因：teacher_courses.kcbh 与 course_selection.课程班号 是否同一套编号
# 直连 CDSP，完整复现后端的 $filter 查询（课程班号 eq <值>），不落库
# 用法: bash scripts/diag_kcbh_filter.sh
# ============================================================================
set -e

SEL_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"   # course_selection 学生选课
TC_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497"    # teacher_courses 教师任课

BAD_KCBH="219181189"    # 后端从 teacher_courses 拿到、过滤后 0 命中的值
GOOD_KCBH="256726307"   # 你手动测 $filter 成功过滤的值
SAMPLE_KCDM="1363220"   # 外国语学院一门课的课程代码(kcdm)，用于看 teacher_courses 真实 kcbh

# 1. 读取认证信息
ENV_DUMP=$(docker exec langhack-backend printenv 2>/dev/null || true)
APP_ID=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_ID=" | cut -d= -f2-)
APP_SECRET=$(echo "$ENV_DUMP" | grep -E "^SCHOOL_API_APP_SECRET=" | cut -d= -f2-)
if [ -z "$APP_ID" ] || [ -z "$APP_SECRET" ]; then
  for f in /data/LangHack/docker/.env /data/LangHack/.env; do
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
echo "appId=${APP_ID}  appSecret=${APP_SECRET:0:4}****"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }
# urlencode：空格->%20，中文按字节 %XX（复现后端 encodeODataValue）
urlenc() { python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"; }
call() {  # $1=完整URL
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "$1" -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}
# 提取返回里前 N 条的"课程班号"字段值
show_kcbh() { python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); v=d.get('value',[])
    print('  返回条数:', len(v), '| @api.count:', d.get('@api.count') or d.get('@odata.count'))
    seen=set()
    for r in v[:8]:
        k=r.get('课程班号') or r.get('KCBH') or r.get('kcbh')
        seen.add(str(k))
    print('  返回记录里的课程班号样本:', sorted(seen)[:8])
except Exception as e:
    print('  解析失败/非JSON:', e); 
" 2>/dev/null; }

echo
echo "===== 测试1: \$filter 用【失败值】课程班号 eq ${BAD_KCBH} (复现后端) ====="
F1="课程班号 eq ${BAD_KCBH}"
call "${SEL_URL}?\$filter=$(urlenc "$F1")&\$top=8" | show_kcbh

echo
echo "===== 测试2: \$filter 用【成功值】课程班号 eq ${GOOD_KCBH} (你手测成功过的) ====="
F2="课程班号 eq ${GOOD_KCBH}"
call "${SEL_URL}?\$filter=$(urlenc "$F2")&\$top=8" | show_kcbh

echo
echo "===== 测试3: teacher_courses 按 kcdm=${SAMPLE_KCDM} 实际返回的 kcbh 长什么样 ====="
call "${TC_URL}?kcdm=${SAMPLE_KCDM}&\$top=8" | python3 -c "
import sys,json
try:
    d=json.load(sys.stdin); v=d.get('value',[])
    print('  返回条数:', len(v))
    for r in v[:8]:
        print('   kcdm=%s  kcbh=%s  rkjsxm=%s' % (r.get('kcdm'), r.get('kcbh'), r.get('rkjsxm')))
except Exception as e:
    print('  解析失败:', e)
" 2>/dev/null

echo
echo "===== 判读 ====="
echo " 测试1 返回该班号的选课(课程班号都=${BAD_KCBH}) → B: 后端编码/probe 有问题，我改代码"
echo " 测试1 返回一堆别的班号/全表     → A: ${BAD_KCBH} 在选课表里按'课程班号'查不到 → 两套编号对不上"
echo " 对比测试3 的 kcbh 值 与 测试2 的 ${GOOD_KCBH} 格式(位数/前缀) → 看是不是同一体系"
