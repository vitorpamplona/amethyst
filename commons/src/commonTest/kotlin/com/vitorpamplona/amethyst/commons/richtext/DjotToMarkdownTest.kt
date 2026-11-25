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
package com.vitorpamplona.amethyst.commons.richtext

import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class DjotToMarkdownTest {
    private val wiki: (String) -> String? = { "wiki:" + WikiRedirectEvent.normalizeSlug(it) }

    private fun md(djot: String) = DjotToMarkdown.convert(djot, wiki)

    // NIP-54's own example: undefined references become wikilinks, defined ones keep their target.
    @Test
    fun nip54Wikilinks() =
        assertEquals(
            "Bitcoin is a [cryptocurrency](wiki:cryptocurrency) invented by [Satoshi Nakamoto](nostr:npub1satoshi).\n\n" +
                "See also: [proof of work](wiki:proof-of-work) and [lightning network](wiki:lightning-network).\n",
            md(
                "Bitcoin is a [cryptocurrency][] invented by [Satoshi Nakamoto][].\n\n" +
                    "See also: [proof of work][] and [lightning network][Lightning Network].\n\n" +
                    "[Satoshi Nakamoto]: nostr:npub1satoshi\n",
            ),
        )

    @Test
    fun wikilinkWithoutTargetKeepsItsText() = assertEquals("a b\n", DjotToMarkdown.convert("a [b][]\n") { null })

    @Test
    fun inlineMarkupAndEscapes() =
        assertEquals(
            "*emph* **strong** mark sub sup ~~del~~ 5 \\* 3 a\\_b\n",
            md("_emph_ *strong* {=mark=} ~sub~ ^sup^ {-del-} 5 * 3 a_b\n"),
        )

    @Test
    fun lists() = assertEquals("- a\n- b\n\n1. x\n2. y\n", md("- a\n- b\n\n1. x\n2. y\n"))

    @Test
    fun codeBlock() = assertEquals("```kotlin\nval x = 1\n```\n", md("``` kotlin\nval x = 1\n```\n"))

    @Test
    fun headingAndEscapedLineStart() = assertEquals("# Title\n\n\\# not heading\n", md("# Title\n\n\\# not heading\n"))

    @Test
    fun footnotesBecomeNumberedNotes() = assertEquals("Text\\[1\\].\n\n***\n\n1. Note.\n", md("Text[^1].\n\n[^1]: Note.\n"))

    @Test
    fun bareUrlsStayWhole() = assertEquals("see https://example.com/a_b*c here\n", md("see https://example.com/a_b*c here\n"))

    @Test
    fun table() = assertEquals("| a | b |\n| :-- | --: |\n| 1 | 2 |\n", md("| a | b |\n|:--|--:|\n| 1 | 2 |\n"))

    @Test
    fun smartPunctuation() = assertEquals("\u201Chi\u201D \u2013 \u2026\n", md("\"hi\" -- ...\n"))

    @Test
    fun plainTextJoinsBlocks() = assertEquals("Title Some text.", DjotToMarkdown.plainText("# Title\n\nSome _text_.\n"))

    // Markdown habits in Djot: `**bold**` is strong inside strong, written once.
    @Test
    fun nestedStrongIsWrittenOnce() = assertEquals("**bold** and **x**\n", md("**bold** and *x*\n"))

    @Test
    fun legacyAsciiDocIsRecognized() {
        assertEquals(true, DjotToMarkdown.isLegacyAsciiDoc("\n= Hummingbird\n\n[sidebar]\n"))
        assertEquals(false, DjotToMarkdown.isLegacyAsciiDoc("# Title\n\n= not a title\n"))
        assertEquals(false, DjotToMarkdown.isLegacyAsciiDoc("=  \n"))
    }

    @Test
    fun classNamesTheCodeLanguage() = assertEquals("```json\n{}\n```\n", md("{.json}\n```\n{}\n```\n"))
}
