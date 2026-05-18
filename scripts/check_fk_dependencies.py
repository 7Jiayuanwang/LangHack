#!/usr/bin/env python3
"""
检查 SQL 脚本中的外键依赖关系
确保被引用的表在引用它的表之前创建
"""

import re
import sys

def parse_sql_file(filepath):
    """解析 SQL 文件，提取表创建顺序和外键依赖"""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # 提取所有 CREATE TABLE 语句
    table_pattern = r'CREATE TABLE IF NOT EXISTS `?(\w+)`?\s*\('
    tables = []
    table_positions = {}

    for match in re.finditer(table_pattern, content, re.IGNORECASE):
        table_name = match.group(1)
        position = match.start()
        tables.append((position, table_name))
        table_positions[table_name] = position

    # 提取外键依赖
    fk_pattern = r'FOREIGN KEY.*?REFERENCES\s+`?(\w+)`?\s*\('
    dependencies = []

    # 找到每个表的外键
    for i, (pos, table_name) in enumerate(tables):
        # 找到下一个表的位置或文件末尾
        next_pos = tables[i+1][0] if i+1 < len(tables) else len(content)
        table_content = content[pos:next_pos]

        # 在这个表的定义中查找外键
        for fk_match in re.finditer(fk_pattern, table_content, re.IGNORECASE):
            referenced_table = fk_match.group(1)
            dependencies.append((table_name, referenced_table))

    return tables, dependencies, table_positions

def check_dependencies(tables, dependencies, table_positions):
    """检查外键依赖是否满足创建顺序"""
    errors = []

    for source_table, ref_table in dependencies:
        if source_table not in table_positions:
            errors.append(f"[!] 表 {source_table} 未找到定义")
            continue

        if ref_table not in table_positions:
            errors.append(f"[!] 表 {source_table} 引用了不存在的表 {ref_table}")
            continue

        source_pos = table_positions[source_table]
        ref_pos = table_positions[ref_table]

        if source_pos < ref_pos:
            errors.append(
                f"[!] 外键依赖顺序错误: {source_table} (行 {source_pos}) "
                f"引用了后面才创建的表 {ref_table} (行 {ref_pos})"
            )

    return errors

def main():
    if len(sys.argv) < 2:
        print("用法: python check_fk_dependencies.py <sql_file>")
        sys.exit(1)

    filepath = sys.argv[1]

    print(f"[*] 分析文件: {filepath}\n")

    tables, dependencies, table_positions = parse_sql_file(filepath)

    print(f"[+] 找到 {len(tables)} 个表定义")
    print(f"[+] 找到 {len(dependencies)} 个外键依赖\n")

    print("[*] 表创建顺序:")
    for i, (pos, table_name) in enumerate(tables[:20], 1):
        print(f"  {i:2d}. {table_name}")
    if len(tables) > 20:
        print(f"  ... 还有 {len(tables) - 20} 个表\n")
    else:
        print()

    print("[*] 外键依赖关系:")
    dep_dict = {}
    for source, ref in dependencies:
        if source not in dep_dict:
            dep_dict[source] = []
        dep_dict[source].append(ref)

    for table in sorted(dep_dict.keys())[:15]:
        refs = ', '.join(dep_dict[table])
        print(f"  {table} -> {refs}")
    if len(dep_dict) > 15:
        print(f"  ... 还有 {len(dep_dict) - 15} 个表有外键依赖\n")
    else:
        print()

    print("[*] 检查依赖顺序...")
    errors = check_dependencies(tables, dependencies, table_positions)

    if errors:
        print(f"\n[!] 发现 {len(errors)} 个问题:\n")
        for error in errors:
            print(f"  {error}")
        sys.exit(1)
    else:
        print("\n[+] 所有外键依赖顺序正确！")
        sys.exit(0)

if __name__ == '__main__':
    main()
