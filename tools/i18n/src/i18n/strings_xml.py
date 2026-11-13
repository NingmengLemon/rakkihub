"""Android strings.xml 的读写。

文件被切分为文本片段和 <string> 元素，只改动被修改的元素，其余内容（注释、
<plurals>、<string-array>、空白和顺序）原样保留。值是元素内部的原始 XML
（不解码实体），因此 &amp;、\\' 和内联标签都能往返不变。
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

EMPTY_FILE = "<?xml version='1.0' encoding='UTF-8'?>\n<resources>\n</resources>\n"

# `<string` 后必须是空白、> 或 /，避免匹配到 <string-array>
STRING_ELEMENT = re.compile(r"<string(?=[\s>/])([^>]*?)(?:/>|>([\s\S]*?)</string\s*>)")
COMMENT = re.compile(r"<!--[\s\S]*?-->")
NAME_ATTR = re.compile(r'\bname\s*=\s*"([^"]*)"')
TRANSLATABLE_FALSE = re.compile(r'\btranslatable\s*=\s*"false"')
INDENT = re.compile(r"\n([ \t]+)<string[\s>/]")


@dataclass
class StringElement:
    name: str
    # 开始标签中 name 之外的原始属性串，例如 ` name="a" translatable="false"`
    attrs: str
    value: str
    translatable: bool
    # 未修改时原样输出
    raw: str | None = None


Segment = str | StringElement


def _parse_segments(content: str) -> list[Segment]:
    # 注释替换为等长空白，使下标与原文对齐且不会匹配到注释里的 <string>
    masked = COMMENT.sub(lambda match: " " * len(match.group(0)), content)
    segments: list[Segment] = []
    cursor = 0
    for match in STRING_ELEMENT.finditer(masked):
        attrs = match.group(1)
        name_match = NAME_ATTR.search(attrs)
        if not name_match:
            continue
        start, end = match.start(), match.end()
        value_start = start + len("<string") + len(attrs) + 1
        inner = match.group(2)
        value = "" if inner is None else content[value_start : value_start + len(inner)]
        segments.append(content[cursor:start])
        segments.append(
            StringElement(
                name=name_match.group(1),
                attrs=re.sub(r"\s*/\s*$", "", attrs),
                value=value,
                translatable=not TRANSLATABLE_FALSE.search(attrs),
                raw=content[start:end],
            )
        )
        cursor = end
    segments.append(content[cursor:])
    return segments


class StringsFile:
    def __init__(self, path: Path, content: str):
        self.path = path
        self._segments = _parse_segments(content)
        self._elements: dict[str, StringElement] = {
            segment.name: segment
            for segment in self._segments
            if isinstance(segment, StringElement)
        }
        indent = INDENT.search(content)
        self._indent = indent.group(1) if indent else "  "
        self._dirty = False

    @classmethod
    def load(cls, path: Path) -> StringsFile:
        content = (
            path.open(encoding="utf-8", newline="").read()
            if path.exists()
            else EMPTY_FILE
        )
        return cls(path, content)

    @property
    def exists(self) -> bool:
        return self.path.exists()

    def entries(self) -> dict[str, str]:
        """所有条目 {name: value}，保持文件中的顺序。"""
        return {name: element.value for name, element in self._elements.items()}

    def translatable_entries(self) -> dict[str, str]:
        """需要翻译的条目（排除 translatable="false"）。"""
        return {
            name: element.value
            for name, element in self._elements.items()
            if element.translatable
        }

    def has(self, name: str) -> bool:
        return name in self._elements

    def get(self, name: str) -> str | None:
        element = self._elements.get(name)
        return element.value if element else None

    def set(self, name: str, value: str) -> None:
        """更新已有条目，不存在则追加到末尾。"""
        existing = self._elements.get(name)
        if existing is not None:
            if existing.value == value:
                return
            existing.value = value
            existing.raw = None
        else:
            self._append(
                StringElement(
                    name=name, attrs=f' name="{name}"', value=value, translatable=True
                )
            )
        self._dirty = True

    def delete(self, name: str) -> bool:
        element = self._elements.get(name)
        if element is None:
            return False
        index = next(
            i for i, segment in enumerate(self._segments) if segment is element
        )
        before = self._segments[index - 1] if index > 0 else ""
        after = self._segments[index + 1] if index + 1 < len(self._segments) else ""
        # 元素独占一行时连同缩进和换行一起删除
        if isinstance(before, str) and isinstance(after, str):
            line_only = (
                re.search(r"\n[ \t]*$", before) is not None
                and re.match(r"^\r?\n", after) is not None
            )
            merged = (
                re.sub(r"[ \t]*$", "", before) + re.sub(r"^\r?\n", "", after)
                if line_only
                else before + after
            )
            # 合并相邻文本，后续删除相邻元素时才能判断它是否独占一行
            self._segments[index - 1 : index + 2] = [merged]
        else:
            del self._segments[index]
        del self._elements[name]
        self._dirty = True
        return True

    def serialize(self) -> str:
        return "".join(
            segment
            if isinstance(segment, str)
            else (segment.raw or f"<string{segment.attrs}>{segment.value}</string>")
            for segment in self._segments
        )

    def save(self) -> bool:
        """有改动时写盘，返回是否写入。"""
        if not self._dirty:
            return False
        self.path.parent.mkdir(parents=True, exist_ok=True)
        # newline="" 避免 Windows 上把 \n 换成 \r\n
        with self.path.open("w", encoding="utf-8", newline="") as handle:
            handle.write(self.serialize())
        self._dirty = False
        return True

    def _append(self, element: StringElement) -> None:
        last = len(self._segments) - 1
        tail = self._segments[last]
        close = tail.rfind("</resources>") if isinstance(tail, str) else -1
        if not isinstance(tail, str) or close < 0:
            raise ValueError(f"{self.path}: 找不到 </resources>")
        head = re.sub(r"[ \t]*$", "", tail[:close])
        if not head.endswith("\n"):
            head += "\n"
        self._segments[last : last + 1] = [
            head + self._indent,
            element,
            "\n" + tail[close:],
        ]
        self._elements[element.name] = element
