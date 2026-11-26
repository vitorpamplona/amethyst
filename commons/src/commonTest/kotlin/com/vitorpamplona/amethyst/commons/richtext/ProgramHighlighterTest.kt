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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgramHighlighterTest {
    /** Each token as `TYPE:text`, in order. */
    private fun tokens(
        code: String,
        language: String,
    ) = CodeHighlighter.tokenize(code, language).map { "${it.type}:${code.substring(it.start, it.end)}" }

    @Test
    fun kotlin() {
        assertEquals(
            listOf(
                "KEYWORD:val",
                "TYPE:Document",
                "FUNCTION:parse",
                "STRING:\"# Title\"",
                "COMMENT:// cached",
                "KEYWORD:fun",
                "FUNCTION:size",
                "TYPE:Int",
                "NUMBER:42",
                "STRING:\"\"\"a\n\"b\"\n\"\"\"",
            ),
            tokens("val doc: Document = parse(\"# Title\") // cached\nfun size(): Int = 42\n\"\"\"a\n\"b\"\n\"\"\"", "kotlin"),
        )
    }

    @Test
    fun pythonHashCommentsAndTripleQuotes() {
        assertEquals(
            listOf("KEYWORD:def", "FUNCTION:greet", "STRING:'''doc'''", "COMMENT:# say hi", "KEYWORD:return", "STRING:\"hi\""),
            tokens("def greet(name):\n    '''doc'''\n    # say hi\n    return f\"hi\"", "py"),
        )
    }

    @Test
    fun shellVariablesAndApostrophes() {
        assertEquals(
            listOf("KEYWORD:echo", "KEY:\$HOME", "STRING:\"x\"", "COMMENT:# done", "KEY:\${PATH}"),
            tokens("echo \$HOME don't \"x\" # done\nls \${PATH}", "bash"),
        )
    }

    @Test
    fun rustLifetimesAreNotStrings() {
        val found = tokens("fn first<'a>(x: &'a str) -> &'a str { x }", "rust")
        // No string swallows the line from one lifetime's quote to the next.
        assertEquals(listOf("KEYWORD:fn", "KEYWORD:str", "KEYWORD:str"), found)
    }

    @Test
    fun sqlKeywordsInAnyCase() {
        assertEquals(
            listOf("KEYWORD:SELECT", "KEYWORD:FROM", "KEYWORD:where", "NUMBER:1", "COMMENT:-- all"),
            tokens("SELECT id FROM events where kind = 1 -- all", "sql"),
        )
    }

    @Test
    fun cPreprocessorAndBlockComments() {
        assertEquals(
            listOf("KEYWORD:include", "COMMENT:/* entry */", "KEYWORD:int", "FUNCTION:main", "KEYWORD:return", "NUMBER:0x1F"),
            tokens("#include <stdio.h>\n/* entry */\nint main() { return 0x1F; }", "c"),
        )
    }

    @Test
    fun unterminatedInputNeverThrows() {
        listOf("\"open", "/* open", "'''open", "`open", "\${", "\$").forEach { code ->
            listOf("kotlin", "python", "go", "bash", "lua", "sql").forEach { language ->
                val found = CodeHighlighter.tokenize(code, language)
                assertTrue(found.all { it.start < it.end && it.end <= code.length }, "$language: $code")
            }
        }
    }

    @Test
    fun jsonKeepsItsOwnHighlighter() {
        assertEquals(listOf("PUNCTUATION:{", "KEY:\"kind\"", "PUNCTUATION::", "NUMBER:1", "PUNCTUATION:}"), tokens("{\"kind\": 1}", "json"))
    }
}
