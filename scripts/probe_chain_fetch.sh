#!/usr/bin/env bash
# 探测链式拉取能否拉到当前学期选课数据（persist=false 不落库）
# 用法: bash probe_chain_fetch.sh [学院名] [管理员账号] [密码]
# 默认: 计算机与信息工程学院 / admin / admin123
set -e

COLLEGE="${1:-计算机与信息工程学院}"
ADMIN_USER="${2:-admin}"
ADMIN_PASS="${3:-admin123}"
BASE="http://localhost:8080"

echo "############ 链式拉取探测 ############"
echo "学院: ${COLLEGE}"
echo "账号: ${ADMIN_USER}"
echo "模式: persist=false（只探测，不落库）"
echo

# 1. 登录拿 token
echo "[1] 登录获取 token ..."
LOGIN_RESP=$(curl -s -X POST "${BASE}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")

TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "登录失败，响应："
  echo "$LOGIN_RESP"
  exit 1
fi
echo "token 获取成功: ${TOKEN:0:20}..."
echo

# 2. 触发链式拉取（persist=false）
echo "[2] 触发链式拉取（可能耗时较长，请耐心等待）..."
COLLEGE_ENC=$(printf '%s' "$COLLEGE" | jq -sRr @uri 2>/dev/null || python3 -c "import urllib.parse,sys;print(urllib.parse.quote(sys.stdin.read().strip()))" <<<"$COLLEGE")

RESP=$(curl -s -X POST "${BASE}/api/admin/sync/chain/students-by-college?collegeName=${COLLEGE_ENC}&matchMode=eq&persist=false" \
  -H "Authorization: Bearer ${TOKEN}")

echo
echo "[3] 链式拉取返回结果："
echo "$RESP" | python3 -m json.tool 2>/dev/null || echo "$RESP"

echo
echo "############ 探测完成 ############"
echo "重点看返回 JSON 里的字段："
echo " - courseCount / studentCount        → 匹配课程数 / 拉到的学生数（studentCount>0 说明链路通了）"
echo " - upstreamKcbhSupported              → CDSP 是否支持 KCBH 过滤（false=降级客户端过滤）"
echo " - stepStats.schoolCourse.matchedCount        → 学院匹配到多少课程"
echo " - stepStats.teacherCourses.upstreamRowCount  → teacher_courses 拉了多少行"
echo " - stepStats.teacherCourses.matchedClassCount → 得到多少个课程班号(kcbh)"
echo " - stepStats.courseSelection.upstreamRowCount → 选课接口返回多少行"
echo " - stepStats.courseSelection.acceptedRowCount → 接受多少行学生"
echo " - stepStats.courseSelection.filteredOutCount → KCBH 降级过滤掉多少"
echo " - warnings / errors                  → 关键问题提示"
