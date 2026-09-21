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
package com.vitorpamplona.amethyst.commons.relayClient.event.watchers

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nipCCGeocaching.foundLog.GeocacheFoundLogEvent
import com.vitorpamplona.quartz.nipCCGeocaching.listing.GeocacheListingEvent
import kotlin.test.Test
import kotlin.test.assertTrue

class FilterRepliesAndReactionsToAddressesTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://one.example")!!
    private val author = "b".repeat(64)

    private fun addressable(address: Address) = AddressableNote(address).apply { addRelaySync(relay) }

    /**
     * A NIP-CC found log is the cache's history. It hangs off the listing by a lowercase `a`, the
     * same tag the engagement watcher already asks about, but it is its own kind — so without
     * 7516 in the kind list the cache screen asks for every reaction to a cache and never for its
     * finds, and a cache with logs on a relay we are connected to still reads as unfound.
     */
    @Test
    fun asksForFoundLogsOfACache() {
        val cache = addressable(Address(GeocacheListingEvent.KIND, author, "a-cache"))

        val filters = filterRepliesAndReactionsToAddresses(listOf(cache), null)!!

        val askingByAddress = filters.filter { it.filter.tags?.containsKey("a") == true }

        assertTrue(
            askingByAddress.any { GeocacheFoundLogEvent.KIND in (it.filter.kinds ?: emptyList()) },
            "no filter asks for kind ${GeocacheFoundLogEvent.KIND} on the cache's address",
        )
    }
}
