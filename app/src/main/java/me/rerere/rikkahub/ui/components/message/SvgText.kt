package me.rerere.rikkahub.ui.components.message

internal sealed interface SvgTextSegment {
    data class Text(val text: String) : SvgTextSegment
    data class Svg(val svg: String) : SvgTextSegment
}

private val SVG_BLOCK = Regex(
    pattern = """<svg\b[^>]*>.*?</svg\s*>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val FENCE_LINE = Regex("""^\s*(`{3,}|~{3,})""")

private val UNSAFE_SVG = Regex(
    pattern = """(?is)<script\b|<foreignObject\b|<image\b|<use\b|<style\b|\son[a-z]+\s*=|(?:xlink:)?href\s*=""",
)

private val EXTERNAL_CSS_URL = Regex("""(?is)url\s*\(\s*['"]?\s*(?!#)""")

private const val MAX_INLINE_SVG_CHARS = 64_000

/**
 * 把正文拆成 Markdown 文本段和块级 SVG 段。fenced code 里的 `<svg` 不拆，
 * 未闭合的 SVG 留在文本里交给 Markdown 渲染。
 */
internal fun splitSvgText(text: String): List<SvgTextSegment> {
    val fencedRanges = fencedCodeRanges(text)
    val matches = SVG_BLOCK.findAll(text)
        .filter { match -> fencedRanges.none { match.range.first in it } }
        .toList()
    if (matches.isEmpty()) return listOf(SvgTextSegment.Text(text))

    val segments = mutableListOf<SvgTextSegment>()
    var cursor = 0
    for (match in matches) {
        if (match.range.first > cursor) {
            segments += SvgTextSegment.Text(text.substring(cursor, match.range.first))
        }
        segments += SvgTextSegment.Svg(match.value)
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        segments += SvgTextSegment.Text(text.substring(cursor))
    }
    return segments
}

internal fun isSafeInlineSvg(svg: String): Boolean {
    if (svg.length > MAX_INLINE_SVG_CHARS) return false
    if (!svg.trimStart().startsWith("<svg", ignoreCase = true)) return false
    if (UNSAFE_SVG.containsMatchIn(svg)) return false
    if (EXTERNAL_CSS_URL.containsMatchIn(svg)) return false
    return true
}

private fun fencedCodeRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var offset = 0
    var openStart: Int? = null
    var openChar: Char? = null
    for (line in text.lineSequence()) {
        val marker = FENCE_LINE.find(line)?.groupValues?.get(1)
        if (marker != null) {
            if (openStart == null) {
                openStart = offset
                openChar = marker.first()
            } else if (openChar == marker.first()) {
                ranges += openStart..(offset + line.length)
                openStart = null
                openChar = null
            }
        }
        offset += line.length + 1
    }
    if (openStart != null) ranges += openStart..text.length
    return ranges
}
