#!/usr/bin/env python3
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
MERGED_SQL = ROOT / "docker" / "mysql" / "init" / "04-apply-all-migrations.sql"
FORBIDDEN_PATTERNS = [
    r"\bUSE\b",
    r"\bCALL\s+add_index_if_not_exists\b",
]
FORBIDDEN_FILENAME_PATTERNS = [
    r"__fix_",
]
KNOWN_UNSAFE_FRAGMENTS = []


def version_number(path: Path) -> int:
    match = re.match(r"V(\d+)__", path.name)
    if not match:
        raise ValueError(f"Invalid migration filename: {path.name}")
    return int(match.group(1))


def strip_sql_strings(sql: str) -> str:
    return re.sub(r"'(?:''|[^'])*'", "''", sql, flags=re.S)


def collect_files() -> list[Path]:
    return sorted(MIGRATION_DIR.glob("V*.sql"), key=version_number)


def check_version_chain(files: list[Path], issues: list[str]) -> None:
    versions = [version_number(file) for file in files]
    if not versions:
        issues.append("No migration files found")
        return
    if versions[0] != 0:
        issues.append(f"Migration chain must start at V0, got V{versions[0]}")
    expected = list(range(versions[0], versions[-1] + 1))
    if versions != expected:
        issues.append(f"Migration versions must be contiguous: expected {expected}, got {versions}")


def check_filenames(files: list[Path], issues: list[str]) -> None:
    for file in files:
        for pattern in FORBIDDEN_FILENAME_PATTERNS:
            if re.search(pattern, file.name, re.I):
                issues.append(f"{file.name}: forbidden migration filename pattern found: {pattern}")


def check_forbidden_sql(files: list[Path], issues: list[str]) -> None:
    for file in files:
        text = file.read_text(encoding="utf-8", errors="replace")
        for pattern in FORBIDDEN_PATTERNS:
            if re.search(pattern, text, re.I):
                issues.append(f"{file.name}: forbidden SQL pattern found: {pattern}")


def check_create_alter_reference_order(files: list[Path], issues: list[str]) -> None:
    created_tables: set[str] = set()
    for file in files:
        text = strip_sql_strings(file.read_text(encoding="utf-8", errors="replace"))
        for match in re.finditer(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?([A-Za-z0-9_]+)`?", text, re.I):
            created_tables.add(match.group(1).lower())
        for match in re.finditer(r"ALTER\s+TABLE\s+`?([A-Za-z0-9_]+)`?", text, re.I):
            table = match.group(1).lower()
            if table not in created_tables:
                issues.append(f"{file.name}: ALTER TABLE references table before creation: {table}")
        for match in re.finditer(r"REFERENCES\s+`?([A-Za-z0-9_]+)`?\s*\(", text, re.I):
            table = match.group(1).lower()
            if table not in created_tables:
                issues.append(f"{file.name}: FOREIGN KEY references table before creation: {table}")


def check_school_course_baseline(files: list[Path], issues: list[str]) -> None:
    all_text = "\n".join(file.read_text(encoding="utf-8", errors="replace") for file in files)
    code_text = strip_sql_strings(all_text)
    if re.search(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?course`?\b", code_text, re.I):
        issues.append("Consolidated baseline must not create legacy table `course`; use `school_course` directly")
    if re.search(r"REFERENCES\s+`?course`?\s*\(", code_text, re.I):
        issues.append("Consolidated baseline must not reference legacy table `course`; use `school_course` directly")


def check_known_regressions(files: list[Path], issues: list[str]) -> None:
    source_text = "\n".join(file.read_text(encoding="utf-8", errors="replace") for file in files)
    merged_text = MERGED_SQL.read_text(encoding="utf-8", errors="replace") if MERGED_SQL.exists() else ""
    for fragment in KNOWN_UNSAFE_FRAGMENTS:
        if fragment in source_text:
            issues.append(f"Migration source contains known unsafe fragment: {fragment[:80]}")
        if fragment in merged_text:
            issues.append(f"Merged SQL contains known unsafe fragment: {fragment[:80]}")


def check_merged_sql(files: list[Path], issues: list[str]) -> None:
    if not MERGED_SQL.exists():
        issues.append(f"Merged SQL file not found: {MERGED_SQL}")
        return
    merged = MERGED_SQL.read_text(encoding="utf-8", errors="replace")
    listed = re.findall(r"^-- (V\d+__[^\r\n]+\.sql)$", merged, re.M)
    expected = [file.name for file in files]
    if listed != expected:
        issues.append(f"Merged SQL header is not synchronized. expected={expected}, got={listed}")
    for file in files:
        marker = f"-- {file.name}"
        if marker not in merged:
            issues.append(f"Merged SQL missing section marker: {marker}")
    merged_code = strip_sql_strings(merged)
    if re.search(r"CREATE\s+TABLE\s+IF\s+NOT\s+EXISTS\s+`?course`?\b", merged_code, re.I):
        issues.append("Merged SQL must not create legacy table `course`; use `school_course` directly")
    if re.search(r"REFERENCES\s+`?course`?\s*\(", merged_code, re.I):
        issues.append("Merged SQL must not reference legacy table `course`; use `school_course` directly")


def main() -> int:
    issues: list[str] = []
    if not MIGRATION_DIR.exists():
        print(f"Migration directory not found: {MIGRATION_DIR}")
        return 1
    files = collect_files()
    check_version_chain(files, issues)
    check_filenames(files, issues)
    check_forbidden_sql(files, issues)
    check_create_alter_reference_order(files, issues)
    check_school_course_baseline(files, issues)
    check_merged_sql(files, issues)
    check_known_regressions(files, issues)
    if issues:
        print(f"Migration check failed: {len(issues)} issue(s)")
        for issue in issues:
            print(f"- {issue}")
        return 1
    print("Migration check passed")
    print(f"Checked {len(files)} migration files and merged SQL")
    return 0


if __name__ == "__main__":
    sys.exit(main())
