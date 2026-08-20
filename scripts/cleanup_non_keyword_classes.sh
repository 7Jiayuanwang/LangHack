#!/usr/bin/env bash
# 清理“用户上传内容”课程(ID 999)下 课程名不含指定关键词 的学校同步班级
#
# 背景：「一键接入英语教师」按“教师名下有含英语的课”接入教师，随后会建立该教师
#       当学期的全部教学班，导致其名下的德语/通识(智慧树)/专业课等被连带建班。
#       本脚本按课程名关键词（默认“英语”）清理这些被误建的非英语班。
#
# 判定：course_id=999 且 school_class_id 非空（仅学校同步班，排除手工建班）
#       且 班级名 NOT LIKE %关键词% → 软删除(deleted=1，可恢复)
#
# 安全设计：
#   - 默认 dry-run（只统计+列出，不改数据）
#   - 加 --apply 才真正执行软删除
#   - 只清主库(langhack-mysql-master)，从库由复制自动同步
#
# 用法：
#   bash scripts/cleanup_non_keyword_classes.sh            # 预览（默认关键词=英语）
#   bash scripts/cleanup_non_keyword_classes.sh --apply    # 执行软删除
#   KEYWORD=英语 bash scripts/cleanup_non_keyword_classes.sh --apply

set -euo pipefail

COURSE_ID="${COURSE_ID:-999}"
KEYWORD="${KEYWORD:-英语}"
CONTAINER="${MYSQL_CONTAINER:-langhack-mysql-master}"
DB="${DB_NAME:-english_learning}"
APPLY=0

for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数: $arg（可用: --apply）"; exit 1 ;;
  esac
done

PWD_ENV=$(docker exec "$CONTAINER" sh -c 'echo "$MYSQL_ROOT_PASSWORD"')
if [ -z "$PWD_ENV" ]; then
  echo "无法从容器 $CONTAINER 读取 MYSQL_ROOT_PASSWORD"; exit 1
fi

runsql() {
  docker exec -i "$CONTAINER" sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" '"$DB"' 2>/dev/null' <<SQL
$1
SQL
}

echo "=========================================="
echo " 清理非「$KEYWORD」班  course_id=$COURSE_ID  容器=$CONTAINER"
echo " 模式: $([ "$APPLY" = 1 ] && echo '★ 执行软删除 (--apply)' || echo '预览 (dry-run)')"
echo "=========================================="

echo -e "\n【将要清理的班级（课名不含「$KEYWORD」的学校同步班）】"
runsql "
SELECT SUBSTRING_INDEX(c.name,' - ',1) AS course, COUNT(*) cnt,
       SUM((SELECT COUNT(*) FROM student_classes sc WHERE sc.class_id=c.id)) stu
FROM classes c
WHERE c.course_id=$COURSE_ID AND c.deleted=0
  AND c.school_class_id IS NOT NULL AND c.school_class_id != ''
  AND c.name NOT LIKE '%$KEYWORD%'
GROUP BY course ORDER BY cnt DESC;
"

echo -e "\n【将删班级总数】"
runsql "
SELECT COUNT(*) AS total_to_delete FROM classes c
WHERE c.course_id=$COURSE_ID AND c.deleted=0
  AND c.school_class_id IS NOT NULL AND c.school_class_id != ''
  AND c.name NOT LIKE '%$KEYWORD%';
"

if [ "$APPLY" = 1 ]; then
  echo -e "\n【执行软删除…】"
  runsql "
UPDATE classes c
SET c.deleted=1, c.updated_time=NOW()
WHERE c.course_id=$COURSE_ID AND c.deleted=0
  AND c.school_class_id IS NOT NULL AND c.school_class_id != ''
  AND c.name NOT LIKE '%$KEYWORD%';
"
  echo -e "\n【清理后 course_id=$COURSE_ID 活跃班级数】"
  runsql "SELECT COUNT(*) AS active_cls FROM classes WHERE course_id=$COURSE_ID AND deleted=0;"
  echo -e "\n✅ 完成。已软删除(deleted=1)，如需恢复："
  echo "   UPDATE classes SET deleted=0 WHERE course_id=$COURSE_ID AND deleted=1 AND name NOT LIKE '%$KEYWORD%';"
else
  echo -e "\n（dry-run，未改动任何数据。加 --apply 执行软删除。）"
fi
