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
package com.vitorpamplona.amethyst.commons.content.asciidoc

import com.vitorpamplona.amethyst.commons.content.DocumentDump
import kotlin.test.Test
import kotlin.test.assertEquals

class AsciiDocToDocumentTest {
    private val wiki: (String) -> String? = { "wiki:" + it.lowercase().replace(' ', '-') }

    private fun dump(asciidoc: String) = DocumentDump.dump(AsciiDocToDocument(resolveWikilink = wiki).parse(asciidoc)).trimEnd()

    @Test
    fun titlesAndParagraphs() {
        assertEquals(
            """
            H1["Hummingbird"]
            H2["Range"]
            P["One line" SB "and the next."]
            P["A second paragraph."]
            """.trimIndent(),
            dump("= Hummingbird\n:toc:\n// a comment\n\n== Range\n\nOne line\nand the next.\n\nA second paragraph.\n"),
        )
    }

    @Test
    fun inlineMarkup() {
        assertEquals(
            """
            P[strong["bold"] " " em["italic"] " " code"mono" " " mark["mark"] " x" sup["2"] " H" sub["2"] "O " strong["un" em["con"] "strained"] " snake_case_word"]
            """.trimIndent(),
            dump("*bold* _italic_ `mono` #mark# x^2^ H~2~O **un__con__strained** snake_case_word\n"),
        )
    }

    @Test
    fun linksDropTheirAttributes() {
        // The old converter showed "Rupelian,role=orange": the role is an attribute, not text.
        assertEquals(
            """
            P["Range: " link(https://en.wikipedia.org/wiki/Rupelian)["Rupelian"] ", " link(https://example.com)["site"] ", " link(mailto:me@example.com)["mail me"] ", " link(#ref)["the reference"]]
            """.trimIndent(),
            dump("Range: link:https://en.wikipedia.org/wiki/Rupelian[Rupelian,role=orange], https://example.com[site^], mailto:me@example.com[mail me], <<ref,the reference>>\n"),
        )
    }

    @Test
    fun wikilinksAndImages() {
        assertEquals(
            """
            P["See " link(wiki:hello-world)["Hello World"] " and " link(wiki:a)["label"] "."]
            P[img(https://example.com/bird.jpg)"A bird"]
            """.trimIndent(),
            dump("See [[Hello World]] and [[a|label]].\n\nimage::https://example.com/bird.jpg[A bird,300,200]\n"),
        )
    }

    @Test
    fun listsNestAndCheck() {
        assertEquals(
            """
            UL
              - 
                P["one"]
                UL
                  - 
                    P["nested"]
              - 
                P["two" SB "continued"]
            OL(1)
              - 
                P["first"]
              - 
                P["second"]
            UL
              - [x] 
                P["done"]
              - [ ] 
                P["todo"]
            """.trimIndent(),
            dump("* one\n** nested\n* two\ncontinued\n\n. first\n. second\n\n* [x] done\n* [ ] todo\n"),
        )
    }

    @Test
    fun admonitionsAndQuotes() {
        assertEquals(
            """
            CALLOUT(note)
              P["Read this."]
            CALLOUT(warning)
              P["Careful."]
            QUOTE
              P["Quoted."]
            """.trimIndent(),
            dump("NOTE: Read this.\n\n[WARNING]\n====\nCareful.\n====\n\n____\nQuoted.\n____\n"),
        )
    }

    @Test
    fun codeIsKeptAsWritten() {
        assertEquals(
            """
            CODE(kotlin)"val x = *not bold*"
            CODE()"literal _text_"
            CODE()"indented line"
            """.trimIndent(),
            dump("[source,kotlin]\n----\nval x = *not bold*\n----\n\n....\nliteral _text_\n....\n\n  indented line\n"),
        )
    }

    @Test
    fun tables() {
        assertEquals(
            """
            TABLE[N, N]
              | ["Name"] | ["Size"]
              | ["Ruby"] | [strong["small"]]
              caption["Birds"]
            """.trimIndent(),
            dump(".Birds\n|===\n|Name |Size\n\n|Ruby |*small*\n|===\n"),
        )
    }

    @Test
    fun descriptionsAndFootnotes() {
        assertEquals(
            """
            DL
              term["Wing"]
                P["What it flies with."]
            P["A claim" fn(1) "."]
            FOOTNOTES
              1. [1]
                P["The source."]
            """.trimIndent(),
            dump("Wing:: What it flies with.\n\nA claim footnote:[The source.].\n"),
        )
    }

    @Test
    fun hardBreaksAndTypography() {
        assertEquals(
            """
            P["line one" BR "line two — (©) and…"]
            """.trimIndent(),
            dump("line one +\nline two -- ((C)) and...\n"),
        )
    }
}
