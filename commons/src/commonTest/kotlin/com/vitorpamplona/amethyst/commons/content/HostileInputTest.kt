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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Text comes from strangers: whatever a note holds must parse without overflowing the stack and in
 * about linear time, and the document must stay shallow enough for the renderer to walk.
 *
 * "About linear" is checked against the same parser on ordinary text, on the same binary: a hostile
 * input may cost at most [SLOWER_THAN_ORDINARY] times what ordinary text of its length costs. A fixed
 * wall-clock budget measured the platform instead of the shape: a Kotlin/Native debug test binary
 * parses Markdown 10 to 25 times slower than the JVM, ordinary text included, so a 4 MB input that
 * takes 0.3 s on the JVM takes 5 s there, while a blow-up (quadratic in the input) still costs
 * thousands of times more than ordinary text on both.
 */
class HostileInputTest {
    private class Format(
        val name: String,
        val parse: (String) -> Document,
    ) {
        /** What one character of ordinary text costs this parser, measured once per format. */
        val perChar: Duration by lazy {
            parse(ORDINARY)
            (0 until 3).minOf { timed { parse(ORDINARY) } } / ORDINARY.length
        }
    }

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
        FORMATS.forEach { format ->
            val budget = maxOf(FLOOR, format.perChar * input.length * SLOWER_THAN_ORDINARY)
            lateinit var document: Document
            val took = timed { document = format.parse(input) }
            assertTrue(took < budget, "${format.name}, $name: took $took, budget $budget (${format.perChar} a character on ordinary text)")
            assertTrue(depth(document) <= Document.MAX_DEPTH * 3, "${format.name}, $name: nests ${depth(document)} deep")
            assertTrue(PlainText.of(document).contains(mustKeep), "${format.name}, $name: lost '$mustKeep'")
        }
    }

    @Test
    fun deepQuotes() = check("quotes", "> ".repeat(20000) + "deep", "deep")

    @Test
    fun deepGluedQuotes() = check("glued quotes", ">".repeat(3000) + " deep", "deep")

    /**
     * Each level indents two more spaces, so the input grows with the square of the depth: 2,000
     * levels was 4 MB of spaces, which the Markdown parser alone took over 3 s to read in the
     * iOS simulator's debug build. 500 levels (250 KB) is still 15 times [Document.MAX_DEPTH].
     */
    @Test
    fun deepLists() = check("lists", (0 until 500).joinToString("\n") { "  ".repeat(it) + "- item$it" }, "item0")

    @Test
    fun deepEmphasis() = check("emphasis", "*_".repeat(5000) + "deep" + "_*".repeat(5000), "deep")

    @Test
    fun manyUnclosedMarks() = check("unclosed marks", "a *b _c `d <<e [[f footnote:[g ".repeat(3000) + "end", "end")

    @Test
    fun longBlankRunsInLists() = check("blank runs", "* a\n" + "\n".repeat(40000) + "* b", "b")

    private companion object {
        /** How many times ordinary text's cost a hostile input may take; the worst measured, on the JVM and on iOS, is about 4. */
        const val SLOWER_THAN_ORDINARY = 10

        /** Below this, the timer measures noise (a cold JIT, a GC pause), not the input's shape. */
        val FLOOR = 500.milliseconds

        val FORMATS =
            listOf(
                Format("markdown") { text -> MarkdownToDocument().parse(text) },
                Format("djot") { text -> DjotToDocument().parse(text) },
                Format("asciidoc") { text -> AsciiDocToDocument().parse(text) },
            )

        /** About 20 KB of what notes usually hold: headings, prose with marks and links, lists, quotes. */
        val ORDINARY =
            buildString {
                repeat(64) { i ->
                    append("# Section $i\n\n")
                    append("Some ordinary *words* in a [link](https://example.com/$i), with `code` and _more_ words after them.\n")
                    append("A second line of the same paragraph, a little longer, so that the text dominates the marks.\n\n")
                    append("- first item\n- second item with **strong** text\n- third item\n\n")
                    append("> a quoted line\n> and another one\n\n")
                }
            }

        fun timed(block: () -> Unit): Duration {
            val start = TimeSource.Monotonic.markNow()
            block()
            return start.elapsedNow()
        }
    }
}
