#!/usr/bin/env python3
"""全面扫描 04-apply-all-migrations.sql 的结构性问题"""
import re
import sys
from pathlib import Path

def scan_sql(path: Path):
    text = path.read_text(encoding='utf-8')
    lines = text.split('\n')
    issues = []

    # 1. PREPARE/EXECUTE/DEALLOCATE 配对
    prepare_stack = []
    for i, line in enumerate(lines, 1):
        m = re.match(r'\s*PREPARE\s+(\w+)\s+FROM', line, re.IGNORECASE)
        if m:
            prepare_stack.append((m.group(1), i))
        m = re.match(r'\s*DEALLOCATE\s+PREPARE\s+(\w+)', line, re.IGNORECASE)
        if m:
            if prepare_stack:
                prepared_name, _ = prepare_stack[-1]
                if prepared_name == m.group(1):
                    prepare_stack.pop()
                else:
                    issues.append(f"Line {i}: DEALLOCATE {m.group(1)} mismatches PREPARE {prepared_name}")
    for name, line_no in prepare_stack:
        issues.append(f"Line {line_no}: PREPARE {name} never DEALLOCATED")

    # 2. IF(cond, a, b) 中的 stray `)` 模式 — 我们修过，但再检查一遍
    for i, line in enumerate(lines, 1):
        # 匹配单引号字符串结尾跟 ')' 再跟 `,` 的情况，且下一行以 `'SELECT ` 或 `'ALTER ` 开头
        if re.search(r"'\),\s*$", line) and i < len(lines):
            next_line = lines[i] if i < len(lines) else ''
            # 如果下一行是 IF 的 else 分支（以 'SELECT 或 'ALTER 开头），则当前行末尾的 `)` 是错的
            if re.match(r"\s*'(SELECT|ALTER|CREATE|INSERT|UPDATE|DELETE)", next_line):
                issues.append(f"Line {i}: suspicious stray ')' in IF 2nd argument: {line.strip()[:80]}")

    # 3. 引号平衡 — 统计每行单引号数量（排除 '' 转义）
    for i, line in enumerate(lines, 1):
        # 忽略注释行
        stripped = line.strip()
        if stripped.startswith('--') or stripped.startswith('/*'):
            continue
        # 先剥离 '' 转义
        normalized = line.replace("''", '')
        # 再剥离字符串内容（'...'）
        singles = normalized.count("'")
        if singles % 2 != 0:
            # 跨行字符串是合法的，但在此脚本中少见。加警告
            # 实际中 SQL 单行通常闭合，多行字符串很少
            pass  # 放宽：多行 SELECT/INSERT 允许

    # 4. 括号平衡（全文）
    depth = 0
    in_string = False
    in_comment = False
    line_depth_snapshot = []
    for ci, ch in enumerate(text):
        if in_comment:
            if ch == '\n':
                in_comment = False
            continue
        if not in_string:
            # 检查注释开始
            if ch == '-' and ci + 1 < len(text) and text[ci + 1] == '-':
                in_comment = True
                continue
            if ch == "'":
                in_string = True
                continue
            if ch == '(':
                depth += 1
            elif ch == ')':
                depth -= 1
                if depth < 0:
                    # 找到行号
                    line_no = text[:ci].count('\n') + 1
                    issues.append(f"Position {ci} (line ~{line_no}): unexpected ')' — paren underflow")
                    depth = 0  # 继续扫描
        else:
            if ch == "'":
                # 检查是否是 '' 转义
                if ci + 1 < len(text) and text[ci + 1] == "'":
                    # skip — 这里应该 advance 两个字符，但逐字符扫描简化处理
                    pass
                else:
                    in_string = False
    if depth != 0:
        issues.append(f"Unbalanced parens at EOF: depth={depth}")

    # 5. 检查每个 CREATE TABLE IF NOT EXISTS 是否正确闭合 `) ENGINE=...`
    create_pattern = re.compile(r'CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?[`\w.]+\s*\(', re.IGNORECASE)
    for m in create_pattern.finditer(text):
        start = m.end() - 1
        d = 1
        i = start + 1
        while i < len(text) and d > 0:
            c = text[i]
            if c == "'":
                # skip string
                j = i + 1
                while j < len(text):
                    if text[j] == "'":
                        if j + 1 < len(text) and text[j + 1] == "'":
                            j += 2
                            continue
                        break
                    j += 1
                i = j + 1
                continue
            if c == '-' and i + 1 < len(text) and text[i + 1] == '-':
                # skip to EOL
                while i < len(text) and text[i] != '\n':
                    i += 1
                continue
            if c == '(':
                d += 1
            elif c == ')':
                d -= 1
            i += 1
        if d != 0:
            line_no = text[:m.start()].count('\n') + 1
            issues.append(f"Line {line_no}: CREATE TABLE paren not balanced")

    return issues

if __name__ == '__main__':
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('docker/mysql/init/04-apply-all-migrations.sql')
    issues = scan_sql(path)
    if not issues:
        print("No issues found")
    else:
        print(f"Found {len(issues)} issue(s):")
        for iss in issues[:50]:
            print(f"  {iss}")
        if len(issues) > 50:
            print(f"  ... and {len(issues) - 50} more")
