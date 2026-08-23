#!/usr/bin/env bash
# 清理“用户上传内容”课程(ID 999)下没有任何学生的空班级
#
# 背景：教师班级同步(TeacherClassSyncServiceImpl)早期版本会先建班再拉学生，
#       导致 CDSP 选课接口对某些 kcbh 返回空时产生一批“空班”。
#       新版逻辑已改为“拉不到学生就不建班”，此脚本用于清理历史遗留的空班。
#
# 安全设计：
#   - 默认 dry-run（只统计+列出，不改数据）
#   - 加 --apply 才真正执行软删除（deleted=1，可恢复，不物理删除）
#   - 只处理 course_id=999 且 student_classes 无记录的班级
#   - 只清理数据库主库(langhack-mysql-master)，从库由复制自动同步
#
# 用法：
#   bash scripts/cleanup_empty_classes.sh            # 预览（dry-run）
#   bash scripts/cleanup_empty_classes.sh --apply    # 执行软删除
#   COURSE_ID=999 bash scripts/cleanup_empty_classes.sh --apply

set -euo pipefail

COURSE_ID="${COURSE_ID:-999}"
CONTAINER="${MYSQL_CONTAINER:-langhack-mysql-master}"
DB="${DB_NAME:-english_learning}"
APPLY=0

for arg in "$@"; do
  case "$arg" in
    --apply) APPLY=1 ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "未知参数: $arg（可用: --apply）"; exit 1 ;;
  esac
done

# 从容器环境读取 root 密码，不在命令行暴露
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
echo " 清理空班脚本  course_id=$COURSE_ID  容器=$CONTAINER"
echo " 模式: $([ "$APPLY" = 1 ] && echo '★ 执行软删除 (--apply)' || echo '预览 (dry-run)')"
echo "=========================================="

# 统计
echo -e "\n【当前状态】(school_sync_empty = 学校同步产生且无学生的空班，即本次实际清理目标)"
runsql "
SELECT
  COUNT(*) AS total_cls,
  SUM((SELECT COUNT(*) FROM student_classes sc WHERE sc.class_id=c.id)=0) AS empty_cls,
  SUM((SELECT COUNT(*) FROM student_classes sc WHERE sc.class_id=c.id)>0) AS non_empty,
  SUM(
    c.school_class_id IS NOT NULL AND c.school_class_id != ''
    AND (SELECT COUNT(*) FROM student_classes sc WHERE sc.class_id=c.id)=0
  ) AS school_sync_empty
FROM classes c
WHERE c.course_id=$COURSE_ID AND c.deleted=0;
"

echo -e "\n【将要清理的空班样本（前 15 条，仅学校同步班：school_class_id 非空）】"
runsql "
SELECT c.id, c.name, c.school_class_id AS kcbh, c.teacher_id
FROM classes c
WHERE c.course_id=$COURSE_ID AND c.deleted=0
  AND c.school_class_id IS NOT NULL AND c.school_class_id != ''
  AND (SELECT COUNT(*) FROM student_classes sc WHERE sc.class_id=c.id)=0
ORDER BY c.id
LIMIT 15;
"

if [ "$APPLY" = 1 ]; then
  echo -e "\n【执行软删除…（仅学校同步班，排除手工建班）】"
  runsql "
UPDATE classes c
SET c.deleted=1, c.updated_time=NOW()
WHERE c.course_id=$COURSE_ID AND c.deleted=0
  AND c.school_class_id IS NOT NULL AND c.school_class_id != ''
  AND NOT EXISTS (SELECT 1 FROM student_classes sc WHERE sc.class_id=c.id);
"
  echo -e "\n【清理后状态】"
  runsql "
SELECT
  SUM(c.deleted=0) AS active_cls,
  SUM(c.deleted=1) AS deleted_cls
FROM classes c
WHERE c.course_id=$COURSE_ID;
"
  echo -e "\n✅ 完成。空班已软删除(deleted=1)，如需恢复："
  echo "   UPDATE classes SET deleted=0 WHERE course_id=$COURSE_ID AND deleted=1 AND <条件>;"
else
  echo -e "\n（dry-run，未改动任何数据。加 --apply 执行软删除。）"
fi
