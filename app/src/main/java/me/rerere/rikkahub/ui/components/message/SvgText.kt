package me.rerere.rikkahub.ui.components.message

internal sealed interface SvgTextSegment {
    data class Text(val text: String) : SvgTextSegment
    data class Svg(val svg: String) : SvgTextSegment
}

private val SVG_BLOCK = Regex(
    pattern = """<svg\b[^>]*>.*?</svg\s*>""",
    options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val FENCE_OPEN = Regex("""^ {0,3}(`{3,}|~{3,})(.*)$""")
private val FENCE_CLOSE = Regex("""^ {0,3}(`{3,}|~{3,})[ \t]*$""")

// 属性名前必须有空白，避免把 text content 里的 " once = 1" 当事件属性；
// href 前不能是字母、数字、下划线、冒号或连字符，避免命中 data-href。
private val UNSAFE_SVG = Regex(
    pattern = """(?is)<script\b|<foreignObject\b|<image\b|<use\b|<style\b|\son[a-z]+\s*=\s*["']|(?<![\w:-])(?:xlink:)?href\s*=\s*["']""",
)

private const val MAX_INLINE_SVG_CHARS = 64_000

/**
 * 把正文拆成 Markdown 文本段和块级 SVG 段。
 *
 * 只接受用空行和正文隔开、独占一行的 `<svg>` 块；inline code、blockquote、
 * 缩进代码块和 fenced code 里的内容都不拆。未闭合的 SVG 留在文本里。
 */
internal fun splitSvgText(text: String): List<SvgTextSegment> {
    val fencedRanges = fencedCodeRanges(text)
    val matches = SVG_BLOCK.findAll(text)
        .filter { match ->
            isStandaloneBlock(text, match.range) && fencedRanges.none { match.range.first in it }
        }
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
    if (hasExternalCssUrl(svg)) return false
    return true
}

private fun hasExternalCssUrl(svg: String): Boolean {
    var from = 0
    while (true) {
        val start = svg.indexOf("url(", from, ignoreCase = true)
        if (start < 0) return false
        val end = svg.indexOf(')', start + 4)
        if (end < 0) return true
        val value = svg.substring(start + 4, end).trim().trim('"', '\'').trim()
        if (!value.startsWith("#")) return true
        from = end + 1
    }
}

/**
 * SVG 必须独占一行，并且前后都是空行（或文本边界）。这样不会把 emphasis、
 * 链接、列表、表格、引用和缩进代码块里的 `<svg>` 拆出去。
 */
private fun isStandaloneBlock(text: String, range: IntRange): Boolean {
    val start = range.first
    val end = range.last + 1
    val lineStart = text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
    if (lineStart != start) return false

    val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    if (text.substring(end, lineEnd).isNotBlank()) return false

    if (lineStart > 0) {
        val previousEnd = lineStart - 1
        val previousStart = text.lastIndexOf('\n', previousEnd - 1) + 1
        if (text.substring(previousStart, previousEnd).isNotBlank()) return false
    }
    if (lineEnd < text.length) {
        val nextEnd = text.indexOf('\n', lineEnd + 1).let { if (it < 0) text.length else it }
        if (text.substring(lineEnd + 1, nextEnd).isNotBlank()) return false
    }
    return true
}

private data class Line(val start: Int, val end: Int, val text: String)

private fun fencedCodeRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var openStart: Int? = null
    var openChar: Char? = null
    var openLength = 0
    for (line in lines(text)) {
        val open = FENCE_OPEN.matchEntire(line.text)
        if (openStart == null) {
            if (open != null) {
                val marker = open.groupValues[1]
                openStart = line.start
                openChar = marker.first()
                openLength = marker.length
            }
            continue
        }
        val close = FENCE_CLOSE.matchEntire(line.text)
        if (close != null) {
            val marker = close.groupValues[1]
            if (marker.first() == openChar && marker.length >= openLength) {
                ranges += openStart..line.end
                openStart = null
                openChar = null
                openLength = 0
            }
        }
    }
    if (openStart != null) ranges += openStart..text.length
    return ranges
}

private fun lines(text: String): List<Line> {
    val result = mutableListOf<Line>()
    var start = 0
    for (index in text.indices) {
        if (text[index] != '\n') continue
        var end = index
        if (end > start && text[end - 1] == '\r') end--
        result += Line(start = start, end = end, text = text.substring(start, end))
        start = index + 1
    }
    result += Line(start = start, end = text.length, text = text.substring(start))
    return result
}
