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
package com.vitorpamplona.amethyst.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** `amy cordn send --tags JSON`: a tag list on the command line, read as an event's tags. */
class CliTagArrayTest {
    @Test
    fun readsATagList() {
        val tags = CliJson.parseTagArray("""[["option","0","Pizza"],["polltype","singlechoice"],["e"]]""")
        assertEquals(listOf(listOf("option", "0", "Pizza"), listOf("polltype", "singlechoice"), listOf("e")), tags.map { it.toList() })
    }

    @Test
    fun refusesAnythingElse() {
        listOf("""{"a":1}""", """[["x",1]]""", """["x"]""", "not json", """[["x",null]]""").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { CliJson.parseTagArray(bad) }
        }
    }
}
