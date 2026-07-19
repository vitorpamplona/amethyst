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
package com.vitorpamplona.quartz.nip65RelayList

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlin.test.Test
import kotlin.test.assertEquals

class AdvertisedRelayListMutationsTest {
    fun norm(str: String) = RelayUrlNormalizer.normalizeOrNull(str)!!

    val relay1 = norm("wss://relay1.com")
    val relay2 = norm("wss://relay2.com")

    fun List<AdvertisedRelayInfo>.keys() = map { it.relayUrl.url to it.type }

    @Test
    fun addWriteToReadOnlyPromotesToBoth() {
        val before = listOf(AdvertisedRelayInfo(relay1, AdvertisedRelayType.READ))

        val after = before.addFacet(relay1, AdvertisedRelayFacet.WRITE)

        assertEquals(listOf(relay1.url to AdvertisedRelayType.BOTH), after.keys())
    }

    @Test
    fun removeWriteFromBothDemotesToRead() {
        val before = listOf(AdvertisedRelayInfo(relay1, AdvertisedRelayType.BOTH))

        val after = before.removeFacet(relay1, AdvertisedRelayFacet.WRITE)

        assertEquals(listOf(relay1.url to AdvertisedRelayType.READ), after.keys())
    }

    @Test
    fun removeLastFacetDropsTheRelay() {
        val before =
            listOf(
                AdvertisedRelayInfo(relay1, AdvertisedRelayType.WRITE),
                AdvertisedRelayInfo(relay2, AdvertisedRelayType.BOTH),
            )

        val after = before.removeFacet(relay1, AdvertisedRelayFacet.WRITE)

        assertEquals(listOf(relay2.url to AdvertisedRelayType.BOTH), after.keys())
    }

    @Test
    fun addToAbsentRelayCreatesSingleFacetEntry() {
        val before = listOf(AdvertisedRelayInfo(relay1, AdvertisedRelayType.BOTH))

        val afterRead = before.addFacet(relay2, AdvertisedRelayFacet.READ)
        val afterWrite = before.addFacet(relay2, AdvertisedRelayFacet.WRITE)

        assertEquals(
            listOf(relay1.url to AdvertisedRelayType.BOTH, relay2.url to AdvertisedRelayType.READ),
            afterRead.keys(),
        )
        assertEquals(
            listOf(relay1.url to AdvertisedRelayType.BOTH, relay2.url to AdvertisedRelayType.WRITE),
            afterWrite.keys(),
        )
    }

    @Test
    fun splitReadWriteEntriesForSameUrlMergeToBoth() {
        // Legal per NIP-65 and produced by split-marker clients: two `r` tags
        // for the same URL, one `read` and one `write`. The facets must merge
        // to BOTH instead of the last entry winning.
        val before =
            listOf(
                AdvertisedRelayInfo(relay1, AdvertisedRelayType.READ),
                AdvertisedRelayInfo(relay1, AdvertisedRelayType.WRITE),
            )

        val afterAdd = before.addFacet(relay2, AdvertisedRelayFacet.READ)
        assertEquals(
            listOf(relay1.url to AdvertisedRelayType.BOTH, relay2.url to AdvertisedRelayType.READ),
            afterAdd.keys(),
        )

        val afterRemove = before.removeFacet(relay1, AdvertisedRelayFacet.WRITE)
        assertEquals(listOf(relay1.url to AdvertisedRelayType.READ), afterRemove.keys())

        val afterSet = before.setFacet(listOf(relay1), AdvertisedRelayFacet.WRITE)
        assertEquals(listOf(relay1.url to AdvertisedRelayType.BOTH), afterSet.keys())
    }

    @Test
    fun setFacetDemotesUnlistedAndAddsTargets() {
        val before =
            listOf(
                AdvertisedRelayInfo(relay1, AdvertisedRelayType.BOTH),
                AdvertisedRelayInfo(relay2, AdvertisedRelayType.WRITE),
            )

        val after = before.setFacet(listOf(relay2), AdvertisedRelayFacet.WRITE)

        assertEquals(
            listOf(relay1.url to AdvertisedRelayType.READ, relay2.url to AdvertisedRelayType.WRITE),
            after.keys(),
        )
    }
}
