from __future__ import annotations

from i18n.validation import (
    escape_android_quotes,
    extract_placeholders,
    normalize_translation,
    text_to_xml,
    validate_translation,
    validate_xml,
)


def test_extracts_placeholders() -> None:
    assert extract_placeholders("%1$s has %2$d items, %.1f%%") == [
        "%%",
        "%.1f",
        "%1$s",
        "%2$d",
    ]
    assert extract_placeholders("no placeholders") == []


def test_allows_reordered_placeholders() -> None:
    assert validate_translation("%1$s sent %2$d", "%2$d أرسل %1$s") is None


def test_rejects_placeholder_mismatch() -> None:
    assert validate_translation("Welcome, %1$s!", "مرحبًا!") is not None
    assert validate_translation("%1$d%%", "%1$d") is not None


def test_rejects_line_break_mismatch() -> None:
    assert validate_translation("a\\nb", "ab") is not None
    assert validate_translation("a\\nb", "أ\\nب") is None
    # 真实换行和字面量 \\n 等价
    assert validate_translation("a\\nb", "a\nb") is None
    assert validate_translation("a\nb", "a\\nb") is None


def test_rejects_empty_translation() -> None:
    assert validate_translation("Hello", "") is not None
    assert validate_translation("Hello", "   ") is not None


def test_rejects_markup_mismatch() -> None:
    assert validate_translation("<b>Bold</b> text", "粗体文本") is not None
    assert validate_translation("<b>Bold</b> text", "<b>粗体</b>文本") is None


def test_escapes_bare_quotes() -> None:
    assert escape_android_quotes("don't") == "don\\'t"
    assert escape_android_quotes("don\\'t") == "don\\'t"
    assert escape_android_quotes('say "hi"') == 'say \\"hi\\"'


def test_leaves_quotes_inside_tags_alone() -> None:
    assert escape_android_quotes('<xliff:g id="n">%s</xliff:g>\'s') == (
        '<xliff:g id="n">%s</xliff:g>\\\'s'
    )


def test_normalizes_bare_ampersands_but_keeps_entities() -> None:
    assert normalize_translation("A & B &amp; C &#169;") == "A &amp; B &amp; C &#169;"


def test_converts_plain_text_input_to_xml() -> None:
    assert text_to_xml("Tom & Jerry's <tag>") == "Tom &amp; Jerry\\'s &lt;tag&gt;"


def test_accepts_well_formed_fragments() -> None:
    for value in [
        "A &amp; B",
        "a &lt; b",
        "&#169; &#xA9; &gt;",
        "<b>x</b>",
        '<xliff:g id="n">%s</xliff:g>',
        "x > y",
        "",
    ]:
        assert validate_xml(value) is None, value


def test_rejects_malformed_attributes_and_text() -> None:
    for value in [
        "<b a=1>x</b>",
        '<b a="1" a="2">x</b>',
        '<b 1a="x">x</b>',
        "a ]]> b",
        "a\x01b",
    ]:
        assert validate_xml(value) is not None, value


def test_protected_terms_must_survive_translation() -> None:
    assert validate_translation("Token usage", "令牌用量", ["Token"]) is not None
    assert validate_translation("Token usage", "Token 用量", ["Token"]) is None
    # 中文里 Token 常直接贴 CJK，没有空格
    assert validate_translation("Token usage", "Token用量", ["Token"]) is None
    assert validate_translation("Max Tokens", "最大Token数", ["Token"]) is None
    assert validate_translation("No token limit", "無Token限制", ["Token"]) is None
    assert validate_translation("tokens", "權杖", ["Token"]) is not None
    # 词边界：tokenizer 不算出现 Token
    assert validate_translation("tokenizer", "分词器", ["Token"]) is None


def test_accepts_cdata_and_rejects_bare_cdata_end() -> None:
    assert validate_xml("a <![CDATA[<b>]]> b") is None
    assert validate_xml("<![CDATA[A & B]]>") is None
    assert validate_xml("<![CDATA[&nbsp;]]>") is None
    assert validate_xml('<b title="a]]>b">x</b>') is None
    assert validate_xml("a ]]> b") is not None


def test_rejects_malformed_fragments() -> None:
    for value in ["A & B", "a < b", "<b>x", "x</b>", "&nbsp;"]:
        assert validate_xml(value) is not None, value


def test_translations_must_be_valid_xml() -> None:
    assert "invalid XML" in (validate_translation("a &lt; b", "a < b") or "")
    assert "invalid XML" in (validate_translation("<b>Bold</b>", "<b>粗体") or "")
