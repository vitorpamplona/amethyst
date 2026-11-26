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
package com.vitorpamplona.amethyst.commons.content

import com.vitorpamplona.amethyst.commons.content.asciidoc.AsciiDocToDocument
import com.vitorpamplona.amethyst.commons.content.djot.DjotToDocument
import com.vitorpamplona.amethyst.commons.content.markdown.MarkdownToDocument
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Text comes from strangers: whatever a note holds must parse without overflowing the stack and in
 * about linear time, and the document must stay shallow enough for the renderer to walk.
 */
class HostileInputTest {
    private val formats: List<Pair<String, (String) -> Document>> =
        listOf(
            "markdown" to { text -> MarkdownToDocument().parse(text) },
            "djot" to { text -> DjotToDocument().parse(text) },
            "asciidoc" to { text -> AsciiDocToDocument().parse(text) },
        )

    /** How deep [document] nests, blocks and inlines together, walked with a stack. */
    private fun depth(document: Document): Int {
        val stack = ArrayList<Pair<Any, Int>>()
        document.blocks.forEach { stack.add(it to 1) }
        var deepest = 0
        while (stack.isNotEmpty()) {
            val (node, level) = stack.removeAt(stack.size - 1)
            if (level > deepest) deepest = level
            val children: List<Any> =
                when (node) {
                    is ListBlock -> node.items.flatMap { it.blocks }
                    is Paragraph -> node.inlines
                    is Heading -> node.inlines
                    is BlockContainer -> node.blocks
                    is InlineContainer -> node.children
                    else -> emptyList()
                }
            children.forEach { stack.add(it to level + 1) }
        }
        return deepest
    }

    private fun check(
        name: String,
        input: String,
        mustKeep: String,
    ) {
        formats.forEach { (format, parse) ->
            val start = TimeSource.Monotonic.markNow()
            val document = parse(input)
            val took = start.elapsedNow()
            assertTrue(took < 3.seconds, "$format, $name: took $took")
            assertTrue(depth(document) <= Document.MAX_DEPTH * 3, "$format, $name: nests ${depth(document)} deep")
            assertTrue(PlainText.of(document).contains(mustKeep), "$format, $name: lost '$mustKeep'")
        }
    }

    @Test
    fun deepQuotes() = check("quotes", "> ".repeat(20000) + "deep", "deep")

    @Test
    fun deepGluedQuotes() = check("glued quotes", ">".repeat(3000) + " deep", "deep")

    @Test
    fun deepLists() = check("lists", (0 until 2000).joinToString("\n") { "  ".repeat(it) + "- item$it" }, "item0")

    @Test
    fun deepEmphasis() = check("emphasis", "*_".repeat(5000) + "deep" + "_*".repeat(5000), "deep")

    @Test
    fun manyUnclosedMarks() = check("unclosed marks", "a *b _c `d <<e [[f footnote:[g ".repeat(3000) + "end", "end")

    @Test
    fun longBlankRunsInLists() = check("blank runs", "* a\n" + "\n".repeat(40000) + "* b", "b")
}
