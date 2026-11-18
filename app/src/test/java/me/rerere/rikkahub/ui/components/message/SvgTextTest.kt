package me.rerere.rikkahub.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgTextTest {
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"></svg>"""

    @Test
    fun `splits text around svg blocks`() {
        val segments = splitSvgText("before\n$svg\nafter")

        assertEquals(3, segments.size)
        assertEquals(SvgTextSegment.Text("before\n"), segments[0])
        assertEquals(SvgTextSegment.Svg(svg), segments[1])
        assertEquals(SvgTextSegment.Text("\nafter"), segments[2])
    }

    @Test
    fun `keeps svg inside fenced code as text`() {
        val content = "```\n$svg\n```"
        val segments = splitSvgText(content)

        assertEquals(listOf(SvgTextSegment.Text(content)), segments)
    }

    @Test
    fun `does not split an unclosed svg`() {
        val content = "before <svg width=\"10\">after"
        assertEquals(listOf(SvgTextSegment.Text(content)), splitSvgText(content))
    }

    @Test
    fun `handles multiple svg blocks`() {
        val second = """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"></svg>"""
        val segments = splitSvgText("$svg\nmiddle\n$second")

        assertEquals(3, segments.size)
        assertEquals(SvgTextSegment.Svg(svg), segments[0])
        assertEquals(SvgTextSegment.Text("\nmiddle\n"), segments[1])
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
    fun `allows local gradient references but rejects external css urls`() {
        assertTrue(
            isSafeInlineSvg(
                """<svg><defs><linearGradient id="g"/></defs><rect fill="url(#g)"/></svg>"""
            )
        )
        assertFalse(isSafeInlineSvg("""<svg><rect fill="url(https://example.com/a)"/></svg>"""))
    }

    @Test
    fun `rejects oversized svg`() {
        assertFalse(isSafeInlineSvg("<svg>" + "x".repeat(64_001) + "</svg>"))
    }
}
