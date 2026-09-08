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
package com.vitorpamplona.quartz.experimental.publications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AsciiDocToMarkdownTest {
    private fun convert(
        s: String,
        resolve: (String) -> String? = { null },
    ) = AsciiDocToMarkdown.convert(s, resolve)

    @Test
    fun convertsHeadings() {
        assertEquals("# The Book", convert("= The Book"))
        assertEquals("## Chapter 1", convert("== Chapter 1"))
        assertEquals("###### Deep", convert("====== Deep"))
    }

    @Test
    fun leavesADelimiterLineAlone() {
        // `====` with no text is a block delimiter, not a heading.
        assertEquals("====", convert("===="))
    }

    @Test
    fun convertsListsIncludingNesting() {
        assertEquals("- one\n- two", convert("* one\n* two"))
        assertEquals("- top\n  - nested", convert("* top\n** nested"))
        assertEquals("1. first\n1. second", convert(". first\n. second"))
    }

    @Test
    fun convertsEmphasis() {
        assertEquals("**bold**", convert("*bold*"))
        assertEquals("*italic*", convert("_italic_"))
        assertEquals("a **bold** and an *italic* word", convert("a *bold* and an _italic_ word"))
    }

    @Test
    fun doesNotTreatIntraWordUnderscoresAsEmphasis() {
        // snake_case identifiers and file names must survive.
        assertEquals("some_variable_name", convert("some_variable_name"))
        assertEquals("a file_name.txt here", convert("a file_name.txt here"))
    }

    @Test
    fun convertsLinksAndImages() {
        assertEquals("[Nostr](https://nostr.com)", convert("link:https://nostr.com[Nostr]"))
        assertEquals("[https://nostr.com](https://nostr.com)", convert("link:https://nostr.com[]"))
        assertEquals("[docs](https://example.com/a)", convert("https://example.com/a[docs]"))
        assertEquals("![A cover](https://img.example/c.jpg)", convert("image::https://img.example/c.jpg[A cover]"))
        assertEquals("see ![icon](https://i.example/i.png) here", convert("see image:https://i.example/i.png[icon] here"))
    }

    @Test
    fun convertsSourceBlocksAndKeepsTheLanguage() {
        val out = convert("[source,kotlin]\n----\nval x = 1\n----")

        assertEquals("```kotlin\nval x = 1\n```", out)
    }

    @Test
    fun convertsAnUnlabelledListingBlock() {
        assertEquals("```\nplain text\n```", convert("----\nplain text\n----"))
        assertEquals("```\nliteral\n```", convert("....\nliteral\n...."))
    }

    @Test
    fun neverRewritesInsideAVerbatimBlock() {
        // The whole point: a code sample keeps its stars, underscores and brackets.
        val src = "----\nval s = \"*not bold* _not italic_ [[not a wikilink]]\"\n= not a heading\n----"

        assertEquals("```\nval s = \"*not bold* _not italic_ [[not a wikilink]]\"\n= not a heading\n```", convert(src))
    }

    @Test
    fun closesAnUnterminatedVerbatimBlock() {
        // A truncated section must not leave the renderer with an open fence.
        assertTrue(convert("----\nleft open").endsWith("```"))
    }

    @Test
    fun convertsQuoteBlocks() {
        assertEquals("> To be or not to be.", convert("____\nTo be or not to be.\n____").trim())
    }

    @Test
    fun convertsAdmonitions() {
        assertEquals("> **NOTE:** Mind the gap.", convert("NOTE: Mind the gap."))
        assertEquals("> **WARNING:** Hot.", convert("WARNING: Hot."))
    }

    @Test
    fun dropsDocumentAttributesAndComments() {
        assertEquals("Body", convert(":toc:\n:author: Aesop\n// a comment\nBody"))
    }

    @Test
    fun dropsBlockAttributeLinesRatherThanPrintingBrackets() {
        assertEquals("Quoted", convert("[quote]\nQuoted"))
    }

    @Test
    fun resolvesWikilinksThroughTheCallback() {
        val out = convert("See [[fable]] for more.") { if (it == "fable") "nostr:naddr1xyz" else null }

        assertEquals("See [fable](nostr:naddr1xyz) for more.", out)
    }

    @Test
    fun supportsALabelledWikilink() {
        val out = convert("See [[fable|the fable]].") { "nostr:naddr1xyz" }

        assertEquals("See [the fable](nostr:naddr1xyz).", out)
    }

    @Test
    fun anUnresolvableWikilinkFallsBackToItsLabelNotToBrackets() {
        assertEquals("See fable for more.", convert("See [[fable]] for more."))
    }

    @Test
    fun passesUnknownConstructsThroughUnchanged() {
        // Tables are not supported; the text must survive rather than be mangled.
        val table = "|===\n| a | b\n|==="
        assertEquals(table, convert(table))
    }

    @Test
    fun aBlockImageSurvivesATrailingSpace() {
        // Otherwise matchEntire fails, the inline rule catches `image:` and captures `:url`,
        // and the "never mangled output" promise is broken by a dead link.
        assertEquals("![A cover](https://img.example/c.jpg)", convert("image::https://img.example/c.jpg[A cover] "))
    }

    @Test
    fun anOrphanSourceAttributeDoesNotLabelALaterBlock() {
        // The language must not survive a block that never opened.
        val out = convert("[source,kotlin]\n\nprose\n\n....\nplain\n....")

        assertTrue("```\nplain\n```" in out, out)
        assertTrue("```kotlin" !in out, out)
    }

    @Test
    fun leavesPlainProseAlone() {
        val prose = "ONE WINTER a Farmer found a Snake stiff and frozen with cold."
        assertEquals(prose, convert(prose))
    }

    @Test
    fun blankContentIsReturnedAsIs() {
        assertEquals("", convert(""))
        assertEquals("   ", convert("   "))
    }

    @Test
    fun convertsARealisticSection() {
        val src =
            """
            = The Farmer and The Snake

            A [[fable]], by _Aesop_.

            NOTE: This is a *classic*.

            ONE WINTER a Farmer found a Snake stiff and frozen with cold.

            * compassion
            * ingratitude

            See link:https://example.com/aesop[the collection].
            """.trimIndent()

        val out = convert(src) { "nostr:naddr1fable" }

        assertTrue(out.startsWith("# The Farmer and The Snake"), out)
        assertTrue("A [fable](nostr:naddr1fable), by *Aesop*." in out, out)
        assertTrue("> **NOTE:** This is a **classic**." in out, out)
        assertTrue("- compassion\n- ingratitude" in out, out)
        assertTrue("[the collection](https://example.com/aesop)" in out, out)
    }
}
