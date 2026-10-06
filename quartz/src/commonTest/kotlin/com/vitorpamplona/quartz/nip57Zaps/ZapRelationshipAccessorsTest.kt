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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZapRelationshipAccessorsTest {
    private val recipient = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val sender = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val post = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val stream = "30311:$recipient:live"
    private val relay = "wss://relay.damus.io/"
    private val sig = "00".repeat(64)

    private val targets =
        arrayOf(
            arrayOf("p", recipient, relay),
            arrayOf("e", post),
            arrayOf("a", stream, relay),
            arrayOf("a", "garbage"),
        )

    @Test
    fun zapRequestZappedAddresses() {
        val request = ZapRequestEvent("33".repeat(32), sender, 1700000000, targets, "", sig)
        assertEquals(listOf(stream), request.zappedAddresses())
        assertEquals(request.zappedAddresses(), request.linkedAddressIds())
        assertEquals(request.zappedPost(), request.linkedEventIds())
        assertEquals(request.zappedAuthor(), request.linkedPubKeys())
    }

    @Test
    fun privateZapMirrorsTheRequestAccessors() {
        val event = PrivateZapEvent("44".repeat(32), sender, 1700000000, targets, "", sig)
        assertEquals(listOf(recipient), event.zappedAuthor())
        assertEquals(listOf(post), event.zappedPost())
        assertEquals(listOf(stream), event.zappedAddresses())
        assertEquals(event.zappedAuthor(), event.linkedPubKeys())
        assertEquals(event.zappedPost(), event.linkedEventIds())
        assertEquals(event.zappedAddresses(), event.linkedAddressIds())
    }

    private fun receipt(
        requestAuthor: String,
        vararg tags: Array<String>,
    ): ZapReceiptEvent {
        val request = ZapRequestEvent("33".repeat(32), requestAuthor, 1700000000, targets, "", sig)
        return ZapReceiptEvent("55".repeat(32), "66".repeat(32), 1700000001, arrayOf(*tags, arrayOf("description", request.toJson())), "", sig)
    }

    @Test
    fun receiptZappedAddressesAndSender() {
        val event = receipt(sender, *targets, arrayOf("P", sender))
        assertEquals(listOf(stream), event.zappedAddresses())
        assertEquals(listOf(stream), (event as ZapReceiptEventInterface).zappedAddresses())
        assertEquals(sender, event.zapSender())
        assertEquals(sender, event.zappedRequestAuthor())
        assertEquals(event.zappedAddresses(), event.linkedAddressIds())
        assertEquals(event.zappedPost(), event.linkedEventIds())
    }

    @Test
    fun receiptWithoutAValidSender() {
        val bogus = "zz".repeat(32)
        val event = receipt(bogus, arrayOf("p", recipient), arrayOf("P", bogus))
        assertNull(event.zapSender())
        assertNull(event.zappedRequestAuthor())
        assertEquals(listOf(recipient), event.linkedPubKeys())
        assertTrue(event.zappedAddresses().isEmpty())
    }
}
