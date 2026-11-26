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
package com.vitorpamplona.amethyst.commons.content.markdown

import com.vitorpamplona.amethyst.commons.content.DocumentDump
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownFootnotesTest {
    private fun dump(markdown: String) = DocumentDump.dump(MarkdownToDocument().parse(markdown)).trimEnd()

    @Test
    fun callsAreNumberedInOrderOfFirstUse() {
        assertEquals(
            """
            P["First" fn(1) " then second" fn(2) " and first again" fn(1) "."]
            P["After."]
            FOOTNOTES
              1. [b]
                P["Note " strong["B"] "."]
              2. [a]
                P["Note A."]
            """.trimIndent(),
            dump("First[^b] then second[^a] and first again[^B].\n\n[^a]: Note A.\n[^b]: Note **B**.\n\nAfter."),
        )
    }

    @Test
    fun aDefinitionDoesNotJoinTheParagraphsAroundIt() {
        assertEquals(
            """
            P["One" fn(1)]
            P["Two"]
            FOOTNOTES
              1. [1]
                P["Note."]
            """.trimIndent(),
            dump("One[^1]\n\n[^1]: Note.\n\nTwo"),
        )
    }

    @Test
    fun indentedLinesContinueANote() {
        assertEquals(
            """
            P["Text" fn(1)]
            FOOTNOTES
              1. [long]
                P["First paragraph" SB "lazy line."]
                P["Second paragraph."]
                CODE()"code"
            """.trimIndent(),
            dump("Text[^long]\n\n[^long]: First paragraph\nlazy line.\n\n    Second paragraph.\n\n        code"),
        )
    }

    @Test
    fun adjacentCallsAndNotesCallingNotes() {
        assertEquals(
            """
            P["Both" fn(1) fn(2)]
            FOOTNOTES
              1. [x]
                P["X, see" fn(3) "."]
              2. [y]
                P["Y."]
              3. [z]
                P["Z."]
            """.trimIndent(),
            dump("Both[^x][^y]\n\n[^x]: X, see[^z].\n[^y]: Y.\n[^z]: Z."),
        )
    }

    @Test
    fun undefinedCallsAndUnusedNotes() {
        // A call with no note is its text; a note nobody calls is not drawn (GitHub's rule).
        assertEquals(
            """
            P["Missing [^nope] here."]
            """.trimIndent(),
            dump("Missing [^nope] here.\n\n[^unused]: Never called."),
        )
    }

    @Test
    fun codeIsLeftAlone() {
        assertEquals(
            """
            P["Call" fn(1)]
            CODE()"[^1]: not a definition\nx[^1]"
            P[code"[^1]"]
            FOOTNOTES
              1. [1]
                P["Real."]
            """.trimIndent(),
            dump("Call[^1]\n\n```\n[^1]: not a definition\nx[^1]\n```\n\n`[^1]`\n\n[^1]: Real."),
        )
    }

    @Test
    fun linksStillWork() {
        assertEquals(
            """
            P[link(https://x.com)["site"] " and a note" fn(1)]
            FOOTNOTES
              1. [n]
                P["See " link(https://x.com)["site"] "."]
            """.trimIndent(),
            dump("[site] and a note[^n]\n\n[site]: https://x.com\n[^n]: See [site]."),
        )
    }

    @Test
    fun manyNotesStayLinear() {
        val n = 5_000
        val text = StringBuilder()
        for (i in 0 until n) text.append("w[^n").append(i).append("] ")
        text.append("\n\n")
        for (i in 0 until n) {
            text
                .append("[^n")
                .append(i)
                .append("]: note [^n")
                .append((i + 1) % n)
                .append("]\n")
        }
        val doc = MarkdownToDocument().parse(text.toString())
        val dumped = DocumentDump.dump(doc)
        assertTrue(dumped.contains("$n. [n${n - 1}]"), "every note is numbered")
    }

    @Test
    fun noBracketCaretMeansNoWork() {
        val src = "Plain [link](https://x.com) text"
        assertTrue(MarkdownFootnotes.extract(src).source === src)
    }
}
