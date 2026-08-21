#!/usr/bin/env bash
# ============================================================================
# 诊断：大学英语1(课程代码1300130) 2026秋的教学班，CDSP选课接口到底返回哪些学生？
# 重点看返回学生的「学号年份前缀」——确认是否有 2026 级新生。
# 直连 CDSP，不落库。回答“为什么库里没有2026开头学生”。
#
# 用法:
#   bash scripts/diag_daxue_ying1_students.sh
#   KCBH=256778601 bash scripts/diag_daxue_ying1_students.sh   # 指定单个课程班号
#   APP_ID=xxx APP_SECRET=yyy bash scripts/diag_daxue_ying1_students.sh
# ============================================================================
set -e

CS_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294802871926794"   # course_selection 选课
# 从 teaching_class 取几个大学英语1(2026秋)的 kcbh 做样本；也可用 KCBH 环境变量覆盖
DEFAULT_KCBH_LIST="256778601 256781014 256781527 256781665 256781690"
KCBH_LIST="${KCBH:-$DEFAULT_KCBH_LIST}"

# ---- 读取认证信息（容器 env → .env 文件 → 环境变量）----
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
echo "appId=${APP_ID}  appSecret=${APP_SECRET:0:4}****"
echo "待测大学英语1(1300130) 2026秋 课程班号: ${KCBH_LIST}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }
urlenc() { python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"; }
call() {
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "$1" -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}

for kcbh in $KCBH_LIST; do
  echo
  echo "===== 课程班号 kcbh=${kcbh} ====="
  FILTER=$(urlenc "课程班号 eq ${kcbh}")
  call "${CS_URL}?\$filter=${FILTER}&\$top=200" | python3 -c "
import sys,json,collections
try:
    d=json.load(sys.stdin); v=d.get('value',[])
except Exception as e:
    print('  解析失败/非JSON:', e); sys.exit(0)
print('  返回选课记录条数:', len(v))
if not v:
    print('  → CDSP 对该 kcbh 返回空（可能选课未生成/该班未开）'); sys.exit(0)
# 探测学号字段
def sid(r):
    for k in ('学号','studentId','XH','xh'):
        if r.get(k): return str(r[k]).strip()
    return ''
def sname(r):
    for k in ('姓名','studentName','XM','xm'):
        if r.get(k): return str(r[k]).strip()
    return ''
print('  字段名样本:', list(v[0].keys()))
yrs=collections.Counter(s[:4] for s in (sid(r) for r in v) if s)
print('  学生学号年份前缀分布:', dict(yrs))
print('  学生样本(前5):')
for r in v[:5]:
    print(f'      学号={sid(r)}  姓名={sname(r)}')
"
done

echo
echo "========== 判读 =========="
echo " - 若返回学号里有 2026 开头 → CDSP 有新生数据，是我们同步/建号漏了 → 需重拉"
echo " - 若返回全是老学号(2025/2024...) 或返回空 → CDSP 选课数据里就没有2026新生 → 是上游数据问题，非代码问题"
