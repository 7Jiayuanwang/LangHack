#!/usr/bin/env python3
"""
Regenerate docker/mysql/init/04-apply-all-migrations.sql from
backend/src/main/resources/db/migration/V*.sql files.

Layout:
  - Header: charset + NAMES + warning comment
  - For each V{N}__<name>.sql in sorted order:
      - Section marker: -- V{N}__<name>.sql
      - File contents verbatim
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
OUT_FILE = ROOT / "docker" / "mysql" / "init" / "04-apply-all-migrations.sql"


def version_key(path: Path) -> int:
    import re
    m = re.match(r"V(\d+)__", path.name)
    if not m:
        raise ValueError(f"Bad filename: {path.name}")
    return int(m.group(1))


def main() -> int:
    files = sorted(MIGRATION_DIR.glob("V*.sql"), key=version_key)
    if not files:
        print("No V*.sql files found", file=sys.stderr)
        return 1

    parts = [
        "-- Auto-generated from backend/src/main/resources/db/migration/V*.sql",
        "-- Do not edit by hand; update Flyway migrations and regenerate.",
        "-- NOTE: 此文件使用 $$ 作为存储过程分隔符，必须通过 04-apply-all-migrations.sh 执行",
        "--       不可直接 source，否则 DELIMITER 命令报错",
        "",
        "USE `${MYSQL_DATABASE}`;",
        "SET NAMES utf8mb4;",
        "",
    ]

    for f in files:
        parts.append("")
        parts.append(f"-- {f.name}")
        parts.append("")
        parts.append(f.read_text(encoding="utf-8"))
        if not parts[-1].endswith("\n"):
            parts[-1] += "\n"

    OUT_FILE.write_text("\n".join(parts), encoding="utf-8")
    print(f"Generated {OUT_FILE} ({OUT_FILE.stat().st_size} bytes, {len(files)} files)")
    return 0


if __name__ == "__main__":
    sys.exit(main())