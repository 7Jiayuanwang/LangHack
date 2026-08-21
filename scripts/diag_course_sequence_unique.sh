#!/usr/bin/env bash
# ============================================================================
# 诊断：teacher_courses(教师授课)数据集里，「课序号(kxh)」是不是唯一标识？
# 只看 2026 秋季数据。直连 CDSP，分页拉全，做多维度唯一性分析，不落库。
#
# 想回答的问题：
#   老师要和教务处按「课程号 + 课序号」核对班级，那么在一个学年学期内：
#     - kxh 单独是否全局唯一？（大概率不唯一：每个课程号下都从 1 开始编）
#     - (课程代码 kcdm + 课序号 kxh) 组合是否唯一？（教务处界面就是这个粒度）
#     - kcbh(课程班号) 是否唯一？（我们现在内部用的编号）
#   哪个粒度能一一对应一个教学班，就用哪个。
#
# 用法:
#   bash scripts/diag_course_sequence_unique.sh
#   XN=2026 XQ=秋 bash scripts/diag_course_sequence_unique.sh
#   APP_ID=xxx APP_SECRET=yyy bash scripts/diag_course_sequence_unique.sh   # 手动传鉴权
# ============================================================================
set -e

TC_URL="https://cdsp.imau.edu.cn/cdsp/data-api/v2/cdsp_1715294645677801497"   # teacher_courses 教师任课
XN="${XN:-2026}"
XQ="${XQ:-秋}"
PAGE_SIZE="${PAGE_SIZE:-500}"
MAX_PAGES="${MAX_PAGES:-60}"   # 最多拉 60 页 * 500 = 3万条，够覆盖一学期

# ---- 1. 读取认证信息（容器 env → .env 文件 → 环境变量）----
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
echo "appId=${APP_ID}  appSecret=${APP_SECRET:0:4}****  学年=${XN}  学期=${XQ}  每页=${PAGE_SIZE}"

gen_sign() { printf '%s' "${APP_ID}${APP_SECRET}${1}" | md5sum | cut -d' ' -f1 | tr -d '\n' | base64 -w0; }
urlenc() { python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$1"; }
call() {
  local ts sign; ts=$(date +%s%3N); sign=$(gen_sign "$ts")
  curl -s -X GET "$1" -H "appId: ${APP_ID}" -H "timestamp: ${ts}" -H "sign: ${sign}" -H "Accept: application/json"
}

FILTER=$(urlenc "学年 eq ${XN} and 学期 eq '${XQ}'")
TMP=$(mktemp /tmp/tc_2026qiu.XXXXXX.jsonl)
trap 'rm -f "$TMP"' EXIT

# ---- 2. 分页拉全，逐页把 value 数组追加成 jsonl ----
echo -e "\n拉取 ${XN} ${XQ} teacher_courses 中..."
total=0
for ((p=0; p<MAX_PAGES; p++)); do
  skip=$((p*PAGE_SIZE))
  # 逐页把 value 数组的每个元素追加为一行 JSON
  call "${TC_URL}?\$filter=${FILTER}&\$top=${PAGE_SIZE}&\$skip=${skip}" \
    | python3 -c "
import sys,json
try:
    v=json.load(sys.stdin).get('value',[])
    for r in v: print(json.dumps(r,ensure_ascii=False))
except Exception:
    pass
" >>"$TMP"
  cur=$(wc -l < "$TMP"); page_got=$((cur-total)); total=$cur
  printf "  第%02d页 skip=%-6s 本页=%d 累计=%d\n" "$((p+1))" "$skip" "$page_got" "$total"
  [ "$page_got" -lt "$PAGE_SIZE" ] && { echo "  (本页不足整页，判定拉取结束)"; break; }
done
echo "共拉取 ${total} 条"

# ---- 3. 唯一性分析 ----
echo -e "\n========== 课序号(kxh) 唯一性分析 (${XN} ${XQ}) =========="
python3 - "$TMP" <<'PY'
import sys, json, collections
rows=[]
with open(sys.argv[1], encoding='utf-8') as f:
    for line in f:
        line=line.strip()
        if line:
            try: rows.append(json.loads(line))
            except: pass

def g(r,*keys):
    for k in keys:
        if k in r and r[k] not in (None,''): return str(r[k]).strip()
    return ''

n=len(rows)
print(f"总记录数: {n}")
if n==0:
    print("无数据，无法分析。检查学年学期或鉴权。"); sys.exit(0)

# 探测字段名（中文 or 英文别名）
sample=rows[0]
print("字段名样本:", list(sample.keys()))

def col(r):
    return dict(
        kxh   = g(r,'课序号','kxh','KXH'),
        kcdm  = g(r,'课程代码','kcdm','KCDM','课程代号'),
        kcbh  = g(r,'课程班号','kcbh','KCBH'),
        kcmc  = g(r,'课程名称','kcmc','KCMC','课程名'),
        rkjsh = g(r,'任课教师号','rkjsh','工号'),
    )
C=[col(r) for r in rows]

def uniq(keyfn, label):
    seen=collections.Counter(keyfn(c) for c in C)
    dup={k:v for k,v in seen.items() if v>1}
    print(f"\n[{label}]  不同取值数={len(seen)}  总记录={n}  "
          f"→ {'✅ 唯一' if len(seen)==n else f'❌ 不唯一（{len(dup)}组重复）'}")
    # 打印几个重复例子
    for k,v in list(dup.items())[:5]:
        print(f"    重复 {v} 次: {k}")

uniq(lambda c: c['kxh'],                         "只看 课序号(kxh)")
uniq(lambda c: (c['kcdm'], c['kxh']),            "课程代码(kcdm) + 课序号(kxh)")
uniq(lambda c: (c['kcdm'], c['kcmc'], c['kxh']), "课程代码 + 课程名 + 课序号")
uniq(lambda c: c['kcbh'],                        "课程班号(kcbh) —— 我们现在内部用的")
uniq(lambda c: (c['kcdm'], c['kxh'], c['rkjsh']),"课程代码 + 课序号 + 教师号")

# kxh 与 kcbh 的对应关系：一个 kxh 是否可能对应多个 kcbh
kxh_to_kcbh=collections.defaultdict(set)
for c in C:
    kxh_to_kcbh[c['kxh']].add(c['kcbh'])
multi=[(k,v) for k,v in kxh_to_kcbh.items() if len(v)>1]
print(f"\n[kxh→kcbh 映射] 一个课序号对应多个课程班号的情况: {len(multi)} 例（应很多，因不同课程都从1编号）")

# 举例：某个课程号下的课序号分布
print("\n[举例] 随机一个课程号下的 (课序号→课程班号) 列表:")
by_kcdm=collections.defaultdict(list)
for c in C:
    by_kcdm[c['kcdm']].append(c)
for kcdm,items in list(by_kcdm.items())[:3]:
    seqs=sorted(set((i['kxh'],i['kcbh'],i['kcmc']) for i in items))
    print(f"  课程代码 {kcdm} ({items[0]['kcmc']}): 课序号种类={sorted(set(i['kxh'] for i in items))}")
    for s in seqs[:6]:
        print(f"      课序号={s[0]}  课程班号={s[1]}")
PY
echo -e "\n========== 分析完成 =========="
echo "结论看上面："
echo " - 若「kcdm+kxh」唯一 而「kxh 单独」不唯一 → 老师核对要用 课程号+课序号 组合"
echo " - 若「kcbh」唯一 → 我们内部编号本就唯一，只是教务处不显示它，需补 kxh 供人工核对"
