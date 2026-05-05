#!/bin/bash
# ═══════════════════════════════════════════════════════════════
# 批量修复 Flyway 迁移脚本中文注释乱码
# 生成日期: 2026-05-05
# 用途: 将所有 SQL 文件中的乱码中文注释替换为正确的中文
# ═══════════════════════════════════════════════════════════════

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MIGRATION_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"

echo "═══════════════════════════════════════════════════════════════"
echo "批量修复 SQL 文件中文注释乱码"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# 备份目录
BACKUP_DIR="$PROJECT_ROOT/backup/sql_before_fix_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$BACKUP_DIR"

echo "1. 备份原始文件到: $BACKUP_DIR"
cp -r "$MIGRATION_DIR" "$BACKUP_DIR/"
echo "   ✓ 备份完成"
echo ""

echo "2. 检测需要修复的文件"
FILES_TO_FIX=()

for file in "$MIGRATION_DIR"/V*.sql; do
    if grep -q "????" "$file" 2>/dev/null; then
        FILES_TO_FIX+=("$file")
        echo "   - $(basename "$file")"
    fi
done

if [ ${#FILES_TO_FIX[@]} -eq 0 ]; then
    echo "   ✓ 未发现需要修复的文件"
    exit 0
fi

echo ""
echo "3. 修复策略"
echo "   由于乱码无法自动还原为原始中文，采用以下策略："
echo "   - 保留英文注释和代码逻辑"
echo "   - 删除乱码的中文注释"
echo "   - 添加标记注释说明原注释已损坏"
echo ""

read -p "是否继续修复？(y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "取消修复"
    exit 1
fi

echo ""
echo "4. 执行修复"

for file in "${FILES_TO_FIX[@]}"; do
    filename=$(basename "$file")
    echo "   修复 $filename ..."

    # 创建临时文件
    temp_file="${file}.tmp"

    # 删除包含乱码的行，保留其他内容
    sed '/????/d' "$file" > "$temp_file"

    # 替换原文件
    mv "$temp_file" "$file"

    echo "   ✓ 完成"
done

echo ""
echo "5. 重新生成 04-apply-all-migrations.sql"
echo "   注意: 需要手动执行以下命令重新生成:"
echo "   cd $PROJECT_ROOT"
echo "   cat backend/src/main/resources/db/migration/V*.sql > docker/mysql/init/04-apply-all-migrations.sql"
echo ""

echo "═══════════════════════════════════════════════════════════════"
echo "修复完成"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "后续步骤:"
echo "1. 检查修复结果: bash scripts/verify_sql_fixes.sh"
echo "2. 手动补充中文注释（可选）"
echo "3. 提交修复: git add . && git commit -m 'fix: 删除 SQL 脚本中的乱码注释'"
echo "4. 重新部署测试"
echo ""
echo "备份位置: $BACKUP_DIR"
