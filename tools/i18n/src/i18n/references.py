"""扫描源码中引用的字符串 key。"""

from __future__ import annotations

import re
from pathlib import Path

REFERENCE_PATTERNS = (re.compile(r"\bR\.string\.(\w+)"), re.compile(r"@string/(\w+)"))

# 系统保留的条目，即使代码中没有引用也不视为未使用
RESERVED_KEYS = frozenset({"app_name"})


def find_referenced_keys(project_root: Path, patterns: list[str]) -> set[str]:
    referenced: set[str] = set()
    for pattern in patterns:
        for path in project_root.glob(pattern):
            if not path.is_file():
                continue
            relative = path.relative_to(project_root).as_posix()
            if "/build/" in relative:
                continue
            content = path.read_text(encoding="utf-8", errors="ignore")
            for regex in REFERENCE_PATTERNS:
                referenced.update(match.group(1) for match in regex.finditer(content))
    return referenced
