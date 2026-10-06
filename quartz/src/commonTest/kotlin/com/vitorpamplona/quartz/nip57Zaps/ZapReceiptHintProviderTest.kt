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
package com.vitorpamplona.quartz.nip57Zaps

import kotlin.test.Test
import kotlin.test.assertEquals

class ZapReceiptHintProviderTest {
    private val a = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val b = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val c = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val relay = "wss://relay.damus.io/"

    private val zapper = "22".repeat(32)

    private fun receipt(
        requestAuthor: String,
        vararg tags: Array<String>,
    ): ZapReceiptEvent {
        val request = ZapRequestEvent("33".repeat(32), requestAuthor, 1700000000, arrayOf(arrayOf("p", b)), "great post", "00".repeat(64))
        return ZapReceiptEvent("44".repeat(32), c, 1700000001, arrayOf(*tags, arrayOf("description", request.toJson())), "", "00".repeat(64))
    }

    @Test
    fun receiptLinksRecipientSenderAndRequestAuthor() {
        val event = receipt(zapper, arrayOf("p", b, relay), arrayOf("P", zapper), arrayOf("e", eventId))
        // the sender appears as `P` and as the request author; consumers dedupe
        assertEquals(listOf(b, zapper, zapper), event.linkedPubKeys())
        // only lowercase `p` has a relay slot
        assertEquals(listOf(b), event.pubKeyHints().map { it.pubkey })
        assertEquals(listOf(eventId), event.linkedEventIds())
        assertEquals("great post", event.indexableContent())
    }

    @Test
    fun anonymousRequestKeyIsLinkedToo() {
        val anon = "55".repeat(32)
        val event = receipt(anon, arrayOf("p", b))
        assertEquals(listOf(b, anon), event.linkedPubKeys())
    }

    @Test
    fun nonHexSenderAndRequestAuthorAreNotLinked() {
        val bogus = "zz".repeat(32)
        val event = receipt(bogus, arrayOf("p", b), arrayOf("P", bogus))
        assertEquals(listOf(b), event.linkedPubKeys())
    }
}
