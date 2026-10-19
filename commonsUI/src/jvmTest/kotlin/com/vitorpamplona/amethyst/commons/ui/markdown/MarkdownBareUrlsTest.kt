/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.markdown

import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A bare URL in a markdown note renders as one whole link. The parser read the `_`, `*` and `~`
 * in a URL's path as emphasis before it linked the URL, so Amazon's
 * `…/71zvSJXs2KL._SL1499_.jpg` became a link to `…/71zvSJXs2KL` and an italic `SL1499`.
 */
class MarkdownBareUrlsTest {
    private val amazon = "https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg"

    private fun parse(content: String) = CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse(MarkdownBareUrls.protect(content))

    private fun AstNode.all(): List<AstNode> {
        val nodes = mutableListOf(this)
        var child = links.firstChild
        while (child != null) {
            nodes += child.all()
            child = child.links.next
        }
        return nodes
    }

    private fun linksIn(content: String) = parse(content).all().mapNotNull { (it.type as? AstLink)?.destination }

    private fun hasNode(
        content: String,
        type: Class<*>,
    ) = parse(content).all().any { type.isInstance(it.type) }

    @Test
    fun anUnderscoredImageUrlStaysOneLink() {
        val content = "**Look** at this\n\n$amazon"
        assertEquals(listOf(amazon), linksIn(content))
        assertTrue(!hasNode(content, AstEmphasis::class.java))
    }

    @Test
    fun starsAndTildesInAUrlStayInTheLink() {
        assertEquals(listOf("https://example.com/a*b*c"), linksIn("**bold** https://example.com/a*b*c end"))
        assertEquals(listOf("https://x.com/~~y~~z"), linksIn("**bold** https://x.com/~~y~~z end"))
        assertEquals(listOf("https://x.com/__init__.py"), linksIn("# Title\nhttps://x.com/__init__.py"))
    }

    @Test
    fun trailingPunctuationIsLeftOutLikeBefore() {
        assertEquals(listOf("https://example.com/a_b_.jpg"), linksIn("**bold** see (https://example.com/a_b_.jpg)."))
    }

    @Test
    fun emphasisAroundAUrlStillWorks() {
        val italic = "_see https://x.com/a_"
        assertEquals(listOf("https://x.com/a"), linksIn(italic))
        assertTrue(hasNode(italic, AstEmphasis::class.java))

        assertTrue(hasNode("**https://x.com/a_b**", AstStrongEmphasis::class.java))
        assertEquals(listOf("https://x.com/a_b"), linksIn("**https://x.com/a_b**"))

        val struck = "~~https://x.com/a~~"
        assertTrue(hasNode(struck, AstStrikethrough::class.java))
        assertEquals(listOf("https://x.com/a"), linksIn(struck))
    }

    @Test
    fun linksAndAutolinksAreLeftAlone() {
        assertEquals(listOf("https://x.com/a_b_"), linksIn("**b** [https://x.com/a_b_](https://x.com/a_b_)"))
        assertEquals(listOf("https://x.com/a_b_"), linksIn("**b** <https://x.com/a_b_>"))
        val reference = "**b** [docs][1]\n\n[1]: https://x.com/a_b_"
        assertSame(reference, MarkdownBareUrls.protect(reference))
    }

    @Test
    fun codeIsLeftAlone() {
        for (content in listOf("**b** `https://x.com/a_b_`", "**b**\n```\nhttps://x.com/a_b_\n```", "**b**\n\n    https://x.com/a_b_", "<img src=\"https://x.com/a_b_.png\">")) {
            assertSame(content, MarkdownBareUrls.protect(content), content)
        }
    }

    @Test
    fun aUrlAfterAClosedFenceIsProtected() {
        assertEquals("```\ncode\n```\n<$amazon>", MarkdownBareUrls.protect("```\ncode\n```\n$amazon"))
    }

    @Test
    fun contentWithNothingToProtectIsReturnedAsIs() {
        for (content in listOf("**b** https://example.com/plain", "snake_case and **b**", "")) {
            assertSame(content, MarkdownBareUrls.protect(content))
        }
    }
}
