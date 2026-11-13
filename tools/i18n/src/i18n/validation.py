"""译文校验与规范化。值均为 strings.xml 中 <string> 内部的原始 XML。"""

from __future__ import annotations

import re

# Android/Java 格式符，例如 %s、%d、%1$s、%.2f、%%
PLACEHOLDER = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc%]")
TAG = re.compile(r"<[^>]+>")
UNESCAPED_QUOTE = re.compile(r"(?<!\\)(['\"])")
BARE_AMPERSAND = re.compile(r"&(?!(?:[a-zA-Z]+|#\d+|#x[0-9a-fA-F]+);)")
# aapt 只认 XML 预定义实体，其它实体即使 XML 合法也会报错
UNKNOWN_ENTITY = re.compile(
    r"&(?!(?:amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)[a-zA-Z]+;"
)
TAG_NAME = re.compile(r"<([A-Za-z_][\w:.-]*)")
CDATA = re.compile(r"<!\[CDATA\[[\s\S]*?\]\]>")
ATTRIBUTE = re.compile(r"([A-Za-z_][\w:.-]*)\s*=\s*(\"[^\"]*\"|'[^']*')")
CLOSING_TAG_NAME = re.compile(r"[A-Za-z_][\w:.-]*")


def _find_tag_end(value: str, start: int) -> int:
    quote: str | None = None
    index = start + 1
    while index < len(value):
        char = value[index]
        if quote is not None:
            if char == quote:
                quote = None
        elif char in "'\"":
            quote = char
        elif char == ">":
            return index
        index += 1
    return -1


def _check_control_chars(value: str) -> str | None:
    for char in value:
        if ord(char) < 0x20 and char not in "\t\n\r":
            return "invalid XML: control character"
    return None


def _check_tag_syntax(tag: str) -> str | None:
    if tag.startswith("</"):
        name = tag[2:-1].strip()
        if not CLOSING_TAG_NAME.fullmatch(name):
            return "invalid XML: malformed closing tag"
        return None
    match = TAG_NAME.match(tag)
    if not match:
        return "invalid XML: malformed tag"
    rest = tag[match.end() :]
    if rest.rstrip().endswith("/>"):
        rest = rest.rstrip()[:-2]
    elif rest.endswith(">"):
        rest = rest[:-1]
    else:
        return "invalid XML: malformed tag"
    seen: set[str] = set()
    position = 0
    while position < len(rest):
        whitespace = re.match(r"\s+", rest[position:])
        if not whitespace:
            return "invalid XML: malformed attributes"
        position += whitespace.end()
        if position >= len(rest):
            break
        attribute = ATTRIBUTE.match(rest[position:])
        if not attribute:
            return "invalid XML: malformed attribute"
        if attribute.group(1) in seen:
            return f"invalid XML: duplicate attribute {attribute.group(1)}"
        seen.add(attribute.group(1))
        position += attribute.end()
    return None


def _check_tags(value: str) -> str | None:
    stack: list[str] = []
    index = 0
    while index < len(value):
        if value[index] != "<":
            next_tag = value.find("<", index)
            if next_tag < 0:
                next_tag = len(value)
            if "]]>" in value[index:next_tag]:
                return "invalid XML: ]]> in text"
            index = next_tag
            continue
        if value.startswith("<!--", index):
            end = value.find("-->", index + 4)
            if end < 0:
                return "invalid XML: unterminated comment"
            index = end + 3
            continue
        if value.startswith("<![CDATA[", index):
            end = value.find("]]>", index + 9)
            if end < 0:
                return "invalid XML: unterminated CDATA"
            index = end + 3
            continue
        end = _find_tag_end(value, index)
        if end < 0:
            return "invalid XML: unterminated tag"
        tag = value[index : end + 1]
        error = _check_tag_syntax(tag)
        if error:
            return error
        if tag.startswith("</"):
            name = tag[2:-1].strip()
            if not stack or stack[-1] != name:
                return f"invalid XML: unexpected </{name}>"
            stack.pop()
        elif tag.startswith("<?"):
            pass
        else:
            name = TAG_NAME.match(tag).group(1)
            if not tag.rstrip().endswith("/>"):
                stack.append(name)
        index = end + 1
    if stack:
        return f"invalid XML: unclosed <{stack[-1]}>"
    return None


def validate_xml(value: str) -> str | None:
    """值不能作为 <string> 内容时返回错误信息。"""
    text = CDATA.sub("", value)
    entity = UNKNOWN_ENTITY.search(text)
    if entity:
        return f"invalid XML: unknown entity {entity.group(0)}"
    if BARE_AMPERSAND.search(text):
        return "invalid XML: bare ampersand"
    control = _check_control_chars(value)
    if control:
        return control
    return _check_tags(value)


def extract_placeholders(value: str) -> list[str]:
    """返回排序后的格式符，允许译文调整顺序。"""
    return sorted(match.group(0) for match in PLACEHOLDER.finditer(value))


def _newline_count(value: str) -> int:
    """字面量 \\n 和真实换行在 Android 里都表示换行，比较时合并计算。"""
    return value.count("\\n") + value.count("\n")


def _extract_tags(value: str) -> list[str]:
    return sorted(match.group(0) for match in TAG.finditer(value))


def _map_text(value: str, transform) -> str:
    """只处理标签之外的文本，避免改动标签属性里的引号。"""
    parts = re.split(r"(<[^>]+>)", value)
    return "".join(
        part if i % 2 == 1 else transform(part) for i, part in enumerate(parts)
    )


def escape_android_quotes(value: str) -> str:
    """转义裸 ' 和 \"，aapt 会报错或静默丢弃它们。"""
    return _map_text(value, lambda text: UNESCAPED_QUOTE.sub(r"\\\1", text))


def text_to_xml(value: str) -> str:
    """把用户输入的纯文本转成可放进 <string> 的内容。"""
    escaped = (
        BARE_AMPERSAND.sub("&amp;", value).replace("<", "&lt;").replace(">", "&gt;")
    )
    return escape_android_quotes(escaped)


def normalize_translation(value: str) -> str:
    """修正模型输出中常见的转义问题。"""
    return escape_android_quotes(
        _map_text(value, lambda text: BARE_AMPERSAND.sub("&amp;", text))
    )


def validate_translation(
    source: str, translated: str, protected_terms: tuple[str, ...] | list[str] = ()
) -> str | None:
    """译文不能安全替换原文时返回错误信息。"""
    if not translated.strip():
        return "empty translation"
    xml_error = validate_xml(translated)
    if xml_error:
        return xml_error
    source_placeholders = extract_placeholders(source)
    target_placeholders = extract_placeholders(translated)
    if source_placeholders != target_placeholders:
        return f"placeholder mismatch: {source_placeholders} vs {target_placeholders}"
    if _newline_count(source) != _newline_count(translated):
        return "line break count mismatch"
    if _extract_tags(source) != _extract_tags(translated):
        return "markup mismatch"
    for term in protected_terms:
        pattern = re.compile(
            rf"(?<![A-Za-z]){re.escape(term)}(?:s)?(?![A-Za-z])", re.IGNORECASE
        )
        if pattern.search(source) and not pattern.search(translated):
            return f"protected term missing: {term}"
    return None
