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
package com.vitorpamplona.quartz.experimental.nipsOnNostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NipTextRelationshipAccessorsTest {
    private val pk1 = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val pk2 = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val eid = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eid2 = "98b574c3527f0ffb30b7271084e3f07480733c7289f8de424d29eae82e36c758"
    private val relay = "wss://relay.damus.io/"
    private val zero = "00".repeat(32)
    private val sig = "00".repeat(64)
    private val forked = "30817:$pk2:nip-01"
    private val quoted = "30023:$pk2:article"

    private fun nip(vararg extra: Array<String>) = NipTextEvent(zero, pk1, 1, arrayOf(arrayOf("d", "nip-xx")) + extra, "a nip", sig)

    @Test
    fun namesMentionsQuotesAndReferences() {
        val event =
            nip(
                arrayOf("p", pk2, relay),
                arrayOf("p", "nothex"),
                arrayOf("q", eid2, relay),
                arrayOf("q", quoted),
                arrayOf("e", eid, relay, "fork"),
                arrayOf("a", forked, relay, "fork"),
                arrayOf("a", "not-a-coordinate"),
            )
        assertEquals(listOf(pk2), event.mentions().map { it.pubKey })
        assertEquals(listOf(pk2), event.mentionKeys())
        assertEquals(listOf(eid2), event.quotedEvents().map { it.eventId })
        assertEquals(listOf(quoted), event.quotedAddresses().map { it.address.toValue() })
        assertEquals(listOf(eid), event.referencedEvents())
        assertEquals(listOf(forked), event.referencedAddresses())

        assertEquals(listOf(pk2), event.linkedPubKeys())
        assertEquals(listOf(eid, eid2), event.linkedEventIds())
        assertEquals(listOf(forked, quoted), event.linkedAddressIds())
    }

    @Test
    fun isAForkReadsTheForkAccessors() {
        assertTrue(nip(arrayOf("e", eid, relay, "fork")).isAFork())
        assertTrue(nip(arrayOf("a", forked, relay, "fork")).isAFork())
        assertFalse(nip(arrayOf("e", eid, relay, "mention")).isAFork())
    }
}
