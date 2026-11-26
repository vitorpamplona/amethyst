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
package com.vitorpamplona.amethyst.commons.content.djot

import com.vitorpamplona.amethyst.commons.content.DocumentDump
import com.vitorpamplona.amethyst.commons.content.Heading
import com.vitorpamplona.amethyst.commons.content.PlainText
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class DjotToDocumentTest {
    // What Wiki.kt resolves a wikilink to, without the author: enough to see which slug it picks.
    private val wiki: (String) -> String? = { "wiki:" + WikiRedirectEvent.normalizeSlug(it) }

    private fun dump(djot: String) = DocumentDump.dump(DjotToDocument(resolveWikilink = wiki).parse(djot)).trimEnd()

    @Test
    fun nip54Wikilinks() {
        // NIP-54's example: an undefined reference is the article with that normalized d tag; a
        // nostr: target is a link as written.
        assertEquals(
            """
            P["The " link(wiki:hello-world)["Hello World"] " article and " link(nostr:npub1abc)["Bob"] "."]
            """.trimIndent(),
            dump("The [Hello World][] article and [Bob](nostr:npub1abc).\n"),
        )
    }

    @Test
    fun wikilinkWithoutTargetKeepsItsText() {
        val doc = DjotToDocument(resolveWikilink = { null }).parse("a [b][]\n")
        assertEquals("P[\"a b\"]", DocumentDump.dump(doc).trimEnd())
    }

    @Test
    fun inlineMarkupKeepsWhatMarkdownCouldNot() {
        assertEquals(
            """
            P[em["em"] " " strong["strong"] " " del["del"] " " mark["mark"] " " ins["ins"] " " sup["sup"] " " sub["sub"] " " span[highlight]["span"] " " code"code"]
            """.trimIndent(),
            dump("_em_ *strong* {-del-} {=mark=} {+ins+} ^sup^ ~sub~ [span]{.highlight} `code`\n"),
        )
    }

    @Test
    fun lists() {
        assertEquals(
            """
            UL
              - 
                P["a"]
              - 
                P["b"]
            OL(3)
              - 
                P["x"]
              - 
                P["y"]
            UL
              - [x] 
                P["done"]
              - [ ] 
                P["todo"]
            """.trimIndent(),
            dump("- a\n- b\n\n3. x\n4. y\n\n- [x] done\n- [ ] todo\n"),
        )
    }

    @Test
    fun codeBlocks() {
        assertEquals(
            """
            CODE(kotlin)"val x = 1"
            CODE(json)"{}"
            """.trimIndent(),
            dump("``` kotlin\nval x = 1\n```\n\n{.json}\n```\n{}\n```\n"),
        )
    }

    @Test
    fun headingsGetAnchors() {
        val heading = DjotToDocument().parse("## Getting Started!\n").blocks.first() as Heading
        assertEquals(2, heading.level)
        assertEquals("getting-started", heading.anchor)
    }

    @Test
    fun footnotesAreGatheredAndNumbered() {
        assertEquals(
            """
            P["Text" fn(1) " and more" fn(2) "."]
            FOOTNOTES
              1. [a]
                P["First."]
              2. [b]
                P["Second."]
            """.trimIndent(),
            dump("Text[^a] and more[^b].\n\n[^b]: Second.\n\n[^a]: First.\n"),
        )
    }

    @Test
    fun tables() {
        assertEquals(
            """
            TABLE[L, R]
              | ["a"] | ["b"]
              | ["1"] | ["2"]
              caption["Numbers"]
            """.trimIndent(),
            dump("| a | b |\n|:--|--:|\n| 1 | 2 |\n\n^ Numbers\n"),
        )
    }

    @Test
    fun definitionsAndDivs() {
        assertEquals(
            """
            DL
              term["Term"]
                P["Its definition."]
            DIV[warning]
              P["Careful."]
            """.trimIndent(),
            dump(": Term\n\n  Its definition.\n\n::: warning\nCareful.\n:::\n"),
        )
    }

    @Test
    fun smartPunctuationAndMath() {
        assertEquals(
            "P[\"\u201Chi\u201D \u2013 \u2026 \" math\"x^2\" \" \" math\"y\"]",
            dump("\"hi\" -- ... \$`x^2` \$\$`y`\n"),
        )
    }

    @Test
    fun nostrTextIsFoundAsInNotes() {
        assertEquals(
            """
            P["See " nostr:HashTagSegment("#tag") " at " nostr:LinkSegment("https://example.com/a")]
            """.trimIndent(),
            dump("See #tag at https://example.com/a\n"),
        )
    }

    @Test
    fun nestedStrongIsDrawnOnce() {
        // A markdown habit: Djot reads **bold** as strong inside strong.
        assertEquals("P[strong[\"bold\"]]", dump("**bold**\n"))
    }

    @Test
    fun plainTextJoinsBlocks() {
        assertEquals("Title Some text.", PlainText.of(DjotToDocument().parse("# Title\n\nSome _text_.\n")))
    }
}
