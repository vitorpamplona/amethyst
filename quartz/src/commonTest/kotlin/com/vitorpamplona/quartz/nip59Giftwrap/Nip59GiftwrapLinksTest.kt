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
package com.vitorpamplona.quartz.nip59Giftwrap

import com.vitorpamplona.quartz.graph.Link
import com.vitorpamplona.quartz.graph.LinkTarget
import com.vitorpamplona.quartz.graph.Relation
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.EphemeralGiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class Nip59GiftwrapLinksTest {
    private val id = "0".repeat(64)
    private val sig = "0".repeat(128)
    private val throwaway = "1".repeat(64)
    private val recipient = "2".repeat(64)

    @Test
    fun wrapsLinkOnlyTheirRecipient() {
        val tags = arrayOf(arrayOf("p", recipient, "wss://inbox.example/"))
        val expected = listOf(Link(Relation.RECIPIENT, LinkTarget.User(recipient), "p"))
        assertEquals(expected, GiftWrapEvent(id, throwaway, 1, tags, "ciphertext", sig).links())
        assertEquals(expected, EphemeralGiftWrapEvent(id, throwaway, 1, tags, "ciphertext", sig).links())
    }
}
