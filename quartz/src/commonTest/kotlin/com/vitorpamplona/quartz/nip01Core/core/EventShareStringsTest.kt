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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class EventShareStringsTest {
    // A fresh instance with the same characters (string literals are shared constants).
    private fun copyOf(s: String) = StringBuilder(s).toString()

    private val id = "a".repeat(64)
    private val pubKey = "b".repeat(64)
    private val tagged = "c".repeat(64)

    private fun event() =
        Event(
            copyOf(id),
            copyOf(pubKey),
            1700000000,
            1,
            arrayOf(arrayOf(copyOf("p"), copyOf(tagged)), arrayOf(copyOf("e"), copyOf(tagged), copyOf("reply"))),
            "hello",
            "d".repeat(128),
        )

    @Test
    fun sharesEqualIdPubKeyAndTagValues() {
        val event = event()
        val pool = HashMap<String, String>()
        event.shareStrings(id, pubKey) { pool.getOrPut(it) { it } }

        assertSame(id, event.id)
        assertSame(pubKey, event.pubKey)
        // Both tags' copies of the tagged key are now one object.
        assertSame(event.tags[0][1], event.tags[1][1])
        assertEquals(listOf("p", tagged), event.tags[0].toList())
        assertEquals(listOf("e", tagged, "reply"), event.tags[1].toList())
    }

    @Test
    fun ignoresSharedStringsThatAreNotEqual() {
        val event = event()
        val before = event.id
        event.shareStrings(tagged, tagged) { it }

        assertSame(before, event.id)
        assertEquals(id, event.id)
        assertEquals(pubKey, event.pubKey)
        assertNotSame(tagged, event.pubKey)
    }
}
