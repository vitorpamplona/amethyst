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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [NostrJsonPretty], the layout Jackson's `InliningTagArrayPrettyPrinter` wrote. The expected
 * text and the pin were recorded while every output was also compared with Jackson.
 */
class NostrJsonPrettyPinnedTest {
    @Test
    fun eventsAndTemplates() {
        val e =
            Event(
                "a".repeat(64),
                "b".repeat(64),
                1_700_000_000,
                1,
                arrayOf(arrayOf("e", "x", "", "root"), arrayOf("t"), emptyArray(), arrayOf("p", "q\"\n")),
                "hi\n\"there\"",
                "c".repeat(128),
            )
        assertEquals(
            """
            {
              "id": "${"a".repeat(64)}",
              "pubkey": "${"b".repeat(64)}",
              "created_at": 1700000000,
              "kind": 1,
              "tags": [
                ["e", "x", "", "root"],
                ["t"],
                [],
                ["p", "q\"\n"]
              ],
              "content": "hi\n\"there\"",
              "sig": "${"c".repeat(128)}"
            }
            """.trimIndent(),
            NostrJsonWriter.toPrettyJson(e),
        )
        val empty = Event("a".repeat(64), "b".repeat(64), 1, 1, emptyArray(), "", "c".repeat(128))
        assertEquals(
            """
            {
              "id": "${"a".repeat(64)}",
              "pubkey": "${"b".repeat(64)}",
              "created_at": 1,
              "kind": 1,
              "tags": [],
              "content": "",
              "sig": "${"c".repeat(128)}"
            }
            """.trimIndent(),
            NostrJsonWriter.toPrettyJson(empty),
        )

        val t = EventTemplate<Event>(5, 7, arrayOf(arrayOf("d", "1")), "c")
        assertEquals(
            """
            {
              "created_at": 5,
              "kind": 7,
              "tags": [
                ["d", "1"]
              ],
              "content": "c"
            }
            """.trimIndent(),
            NostrJsonWriter.toPrettyJson(t),
        )
    }

    // Strings as compact JSON: plain, with escapes, with structural chars, and non-ASCII.
    private val strings = listOf("\"\"", "\"x\"", "\"a\\\"b\"", "\"line\\nbreak\"", "\"{[,:]}\"", "\"\u00e9\u6f22\"")

    /** A random JSON document, as compact text. */
    private fun randomTree(
        random: Random,
        depth: Int,
    ): String {
        val pick = if (depth > 5) random.nextInt(4) else random.nextInt(7)
        return when (pick) {
            0 -> strings.random(random)
            1 -> random.nextInt(-1000, 1000).toString()
            2 -> random.nextBoolean().toString()
            3 -> "null"
            4, 5 -> (0 until random.nextInt(0, 4)).joinToString(",", "[", "]") { randomTree(random, depth + 1) }
            else -> (0 until random.nextInt(0, 4)).joinToString(",", "{", "}") { "\"k$it\":" + randomTree(random, depth + 1) }
        }
    }

    @Test
    fun randomTrees() {
        val random = Random(55)
        val pin = Pin("pretty random trees")
        repeat(5_000) {
            val compact = randomTree(random, 0)
            val formatted = NostrJsonPretty.format(compact)
            pin.add(formatted)
            // Whitespace in the input changes nothing.
            assertEquals(formatted, NostrJsonPretty.format(formatted), compact)
        }
        pin.check(PIN_RANDOM_TREES)
    }

    companion object {
        /** See [Pin]: recorded while every output was also compared with Jackson. */
        private const val PIN_RANDOM_TREES = "8b7a2a57ab3d98fbbc1dbfc420fc3b7dca680bbb3ccaad5ae52bc1e289fb7bf9"
    }
}
