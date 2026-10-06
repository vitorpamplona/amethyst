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
package com.vitorpamplona.quartz.nipCCGeocaching

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.GeocacheVerificationEvent
import com.vitorpamplona.quartz.nipCCGeocaching.verification.tags.FinderCacheTag
import kotlin.test.Test
import kotlin.test.assertEquals

class GeocacheHintProviderTest {
    private val owner = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"
    private val finder = "99bb5591c9116600f845107d31f9b59e2f7c7e09a1ff802e84f1d43da557ca64"
    private val cacheKey = "7d7ffd720b907fe597a7f454afe02f2dc1eca440baa029e9117b1c3209839377"
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io/")!!
    private val cache = Address(GeocacheListingEvent.KIND, owner, "gc1")

    private fun listing(vararg tags: Array<String>) = GeocacheListingEvent("00".repeat(32), owner, 1700000000, arrayOf(arrayOf("d", "gc1"), *tags), "", "00".repeat(64))

    @Test
    fun theFirstToFindWinnerIsALinkedUser() {
        val event = listing(arrayOf("n", "first-to-find"), arrayOf("F", finder), arrayOf("verification", cacheKey))
        assertEquals(listOf(finder), event.linkedPubKeys())
        assertEquals(emptyList(), event.pubKeyHints())
    }

    @Test
    fun anFWithoutTheFirstToFindModifierNamesNobody() {
        assertEquals(emptyList(), listing(arrayOf("F", finder)).linkedPubKeys())
    }

    @Test
    fun aVerificationLinksItsFinderAndItsCache() {
        val event =
            GeocacheVerificationEvent(
                "00".repeat(32),
                cacheKey,
                1700000000,
                arrayOf(FinderCacheTag.assemble(finder, cache, relay)),
                GeocacheVerificationEvent.contentFor(finder),
                "00".repeat(64),
            )

        assertEquals(listOf(finder), event.linkedPubKeys())
        assertEquals(emptyList(), event.pubKeyHints())
        assertEquals(listOf(cache.toValue()), event.linkedAddressIds())
        assertEquals(listOf(AddressHint(cache.toValue(), relay)), event.addressHints())
    }

    @Test
    fun aPlainAddressCacheHasNoRelayHint() {
        val event =
            GeocacheVerificationEvent(
                "00".repeat(32),
                cacheKey,
                1700000000,
                arrayOf(arrayOf("a", "$finder:${cache.toValue()}")),
                "",
                "00".repeat(64),
            )

        assertEquals(listOf(cache.toValue()), event.linkedAddressIds())
        assertEquals(emptyList(), event.addressHints())
    }
}
