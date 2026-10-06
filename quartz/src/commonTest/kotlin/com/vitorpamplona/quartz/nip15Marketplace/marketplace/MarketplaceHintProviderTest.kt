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
package com.vitorpamplona.quartz.nip15Marketplace.marketplace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceHintProviderTest {
    private val pk = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val other = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val third = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val eventId = "43575072239da152afe3d7b5c70ed2beb48db2b10e60c60da45229c09c877d2a"
    private val eventId2 = "b1a2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val addressId = "30023:$other:my-article"
    private val relay = "wss://relay.damus.io/"
    private val id = "00".repeat(32)
    private val sig = "00".repeat(64)

    @Test
    fun merchantsAreLinkedWithoutHints() {
        val market =
            MarketplaceEvent(
                id,
                pk,
                1L,
                arrayOf(arrayOf("d", "m")),
                """{"name":"Market","about":"Things","merchants":["$other","not-a-key","$third"]}""",
                sig,
            )

        assertEquals(listOf(other, third), market.linkedPubKeys())
        assertTrue(market.pubKeyHints().isEmpty())
    }

    @Test
    fun unparseableContentLinksNothing() {
        val market = MarketplaceEvent(id, pk, 1L, arrayOf(arrayOf("d", "m")), "not json", sig)
        assertTrue(market.linkedPubKeys().isEmpty())
        // The cached failure (a marker, not the exception) keeps answering null.
        assertNull(market.marketplaceData())
        assertNull(market.marketplaceData())
        assertEquals("", market.indexableContent())
    }

    @Test
    fun aParsedBodyIsCachedAndReused() {
        val market = MarketplaceEvent(id, pk, 1L, arrayOf(arrayOf("d", "m")), """{"name":"Market","merchants":["$other"]}""", sig)
        val first = market.marketplaceData()
        assertEquals("Market", first?.name)
        assertSame(first, market.marketplaceData())
    }
}
