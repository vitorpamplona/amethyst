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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.nip90Dvms

import com.vitorpamplona.amethyst.model.topNavFeeds.favoriteDvm.FavoriteDvmTopNavPerRelayFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.favoriteDvm.FavoriteDvmTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterHomePostsByDvmIdsTest {
    private val userRelay = RelayUrlNormalizer.normalizeOrNull("wss://user.example/")!!
    private val dvmRelay = RelayUrlNormalizer.normalizeOrNull("wss://dvm.example/")!!

    @Test
    fun emptyFilterSetProducesNoRequests() {
        val set =
            FavoriteDvmTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = emptySet(),
                requestIds = emptySet(),
            )

        assertTrue(filterHomePostsByDvmIds(set, since = null, defaultSince = null).isEmpty())
    }

    @Test
    fun contentFetchIssuedOnUserRelayWithIdsFilter() {
        val ids = setOf("a".repeat(64), "b".repeat(64))
        val set =
            FavoriteDvmTopNavPerRelayFilterSet(
                contentFetches =
                    mapOf(userRelay to FavoriteDvmTopNavPerRelayFilter(ids = ids, addresses = emptySet())),
                listenRelays = emptySet(),
                requestIds = emptySet(),
            )

        val filters = filterHomePostsByDvmIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val single = filters.single()
        assertEquals(userRelay, single.relay)
        assertEquals(ids.sorted(), single.filter.ids?.sorted())
        // Content fetch should not be restricted to a kind — the DVM curates freely.
        assertEquals(null, single.filter.kinds)
    }

    @Test
    fun listenFilterIssuedOnDvmRelayWithKinds6300And7000() {
        val requestId = "9".repeat(64)
        val set =
            FavoriteDvmTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(requestId),
            )

        val filters = filterHomePostsByDvmIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val listen = filters.single()
        assertEquals(dvmRelay, listen.relay)
        assertEquals(
            listOf(NIP90ContentDiscoveryResponseEvent.KIND, NIP90StatusEvent.KIND),
            listen.filter.kinds,
        )
        val eTag = listen.filter.tags?.get("e")
        assertNotNull(eTag)
        assertEquals(listOf(requestId), eTag)
    }

    @Test
    fun mergedRequestIdsAllRideOnOneListenFilterPerRelay() {
        val req1 = "1".repeat(64)
        val req2 = "2".repeat(64)
        val set =
            FavoriteDvmTopNavPerRelayFilterSet(
                contentFetches = emptyMap(),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(req1, req2),
            )

        val filters = filterHomePostsByDvmIds(set, since = null, defaultSince = null)

        assertEquals(1, filters.size)
        val eTag =
            filters
                .single()
                .filter.tags
                ?.get("e")
                .orEmpty()
        assertTrue(eTag.containsAll(listOf(req1, req2)))
        assertEquals(2, eTag.size)
    }

    @Test
    fun contentAndListenSubscriptionsSplitAcrossTheirRespectiveRelays() {
        val ids = setOf("a".repeat(64))
        val requestId = "9".repeat(64)
        val set =
            FavoriteDvmTopNavPerRelayFilterSet(
                contentFetches =
                    mapOf(userRelay to FavoriteDvmTopNavPerRelayFilter(ids = ids, addresses = emptySet())),
                listenRelays = setOf(dvmRelay),
                requestIds = setOf(requestId),
            )

        val filters = filterHomePostsByDvmIds(set, since = null, defaultSince = null)

        assertEquals(2, filters.size)
        assertTrue(filters.any { it.relay == userRelay && it.filter.ids != null })
        assertTrue(filters.any { it.relay == dvmRelay && it.filter.kinds != null })
    }
}
