package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgTextTest {
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"></svg>"""

    @Test
    fun `splits a standalone svg block`() {
        val segments = splitSvgText("before\n\n$svg\n\nafter")

        assertEquals(3, segments.size)
        assertEquals(SvgTextSegment.Text("before\n\n"), segments[0])
        assertEquals(SvgTextSegment.Svg(svg), segments[1])
        assertEquals(SvgTextSegment.Text("\n\nafter"), segments[2])
    }

    @Test
    fun `requires a blank line before and after the block`() {
        assertEquals(
            listOf(SvgTextSegment.Text("before\n$svg\nafter")),
            splitSvgText("before\n$svg\nafter"),
        )
    }

    @Test
    fun `does not split inline emphasis links lists or tables`() {
        val inline = listOf(
            "**bold $svg tail**",
            "see [the $svg icon](https://example.com)",
            "- one\n- $svg\n- three",
            "| a | b |\n| - | - |\n| $svg | x |",
        )
        inline.forEach { content ->
            assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
        }
    }

    @Test
    fun `does not split inline code`() {
        val content = "use `$svg` in text"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `keeps svg inside a fenced code block as text`() {
        val content = "```\n$svg\n```"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `keeps svg inside a language-tagged fenced block as text`() {
        val content = "```kotlin\n$svg\n```"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `keeps svg inside a nested fence as text`() {
        val content = "````\n```kotlin\n$svg\n```\n````"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `keeps svg inside a blockquote fence as text`() {
        val content = "> ```\n> $svg\n> ```"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `keeps svg inside an indented code block as text`() {
        val content = "    $svg"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `handles CRLF fences without offset drift`() {
        val content = "```\r\n${"line\r\n".repeat(20)}$svg\r\n```"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `does not split an unclosed svg`() {
        val content = "before <svg width=\"10\">after"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `handles multiple standalone svg blocks`() {
        val second = """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"></svg>"""
        val segments = splitSvgText("$svg\n\nmiddle\n\n$second")

        assertEquals(3, segments.size)
        assertEquals(SvgTextSegment.Svg(svg), segments[0])
        assertEquals(SvgTextSegment.Text("\n\nmiddle\n\n"), segments[1])
        assertEquals(SvgTextSegment.Svg(second), segments[2])
    }

    @Test
    fun `accepts a simple static svg`() {
        assertTrue(isSafeInlineSvg(svg))
    }

    @Test
    fun `rejects scripts and event handlers`() {
        assertFalse(isSafeInlineSvg("""<svg><script>alert(1)</script></svg>"""))
        assertFalse(isSafeInlineSvg("""<svg onload="alert(1)"></svg>"""))
    }

    @Test
    fun `rejects external references`() {
        assertFalse(isSafeInlineSvg("""<svg><image href="https://example.com/a.png"/></svg>"""))
        assertFalse(isSafeInlineSvg("""<svg><use href="#a"/></svg>"""))
        assertFalse(isSafeInlineSvg("""<svg><a href="https://example.com">x</a></svg>"""))
    }

    @Test
    fun `rejects foreignObject and style tags`() {
        assertFalse(isSafeInlineSvg("""<svg><foreignObject><div/></foreignObject></svg>"""))
        assertFalse(isSafeInlineSvg("""<svg><style>@import url(https://example.com)</style></svg>"""))
    }

    @Test
    fun `allows quoted local gradient references and data-href`() {
        assertTrue(isSafeInlineSvg("""<svg><rect fill="url(#g)"/></svg>"""))
        assertTrue(isSafeInlineSvg("""<svg><rect fill="url('#g')"/></svg>"""))
        assertTrue(isSafeInlineSvg("""<svg><rect fill="url( #g )"/></svg>"""))
        assertTrue(isSafeInlineSvg("""<svg data-href="x"><text> once = 1</text></svg>"""))
    }

    @Test
    fun `rejects external css urls`() {
        assertFalse(isSafeInlineSvg("""<svg><rect fill="url(https://example.com/a)"/></svg>"""))
        assertFalse(isSafeInlineSvg("""<svg><rect fill="url('https://example.com/a')"/></svg>"""))
    }

    @Test
    fun `enforces the character limit at the boundary`() {
        val prefix = "<svg>"
        val suffix = "</svg>"
        val accepted = prefix + "x".repeat(64_000 - prefix.length - suffix.length) + suffix
        assertEquals(64_000, accepted.length)
        assertTrue(isSafeInlineSvg(accepted))
        assertFalse(isSafeInlineSvg(accepted + "x"))
    }
}
