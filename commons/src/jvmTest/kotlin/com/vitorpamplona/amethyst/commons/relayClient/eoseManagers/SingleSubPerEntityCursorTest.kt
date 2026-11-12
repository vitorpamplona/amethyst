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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Communities and follow packs share one subscription. Each is windowed by its own screen's feeds,
 * so one opened later is asked from scratch without making the others refetch.
 */
class SingleSubPerEntityCursorTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    private class Feed : RelayCursorHolder {
        override val relayCursors = RelayCursors()
    }

    private class Community(
        val id: String,
        val feeds: List<RelayCursorHolder>,
    )

    private inner class Manager(
        keys: () -> Set<Community>,
    ) : SingleSubEoseManager<Community>(EmptyNostrClient(), keys) {
        override fun updateFilter(
            keys: List<Community>,
            since: SincePerRelayMap?,
        ) = keys.map { RelayBasedFilter(relay, Filter(kinds = listOf(1111), tags = mapOf("A" to listOf(it.id)), since = since?.get(relay)?.time)) }

        override fun distinct(key: Community) = key.id

        override fun cursorHoldersOf(key: Community) = key.feeds
    }

    /** Builds one filter for every community, as the channel and profile watchers batch theirs. */
    private inner class BatchingManager(
        keys: () -> Set<Community>,
    ) : SingleSubEoseManager<Community>(EmptyNostrClient(), keys) {
        override fun updateFilter(
            keys: List<Community>,
            since: SincePerRelayMap?,
        ) = listOf(RelayBasedFilter(relay, Filter(kinds = listOf(1111), tags = mapOf("A" to keys.map { it.id }), since = since?.get(relay)?.time)))

        override fun distinct(key: Community) = key.id

        override fun cursorHoldersOf(key: Community) = key.feeds
    }

    private fun Manager.sinceFor(id: String) =
        sub
            .filters()
            ?.get(relay)
            ?.single { it.tags?.get("A") == listOf(id) }
            ?.since

    @Test
    fun aCommunityOpenedLaterIsAskedFromScratchAlone() {
        val first = Community("a", listOf(Feed()))
        var keys = setOf(first)
        val manager = Manager { keys }

        manager.updateSubscriptions(keys)
        manager.sub.listener.onEose(relay, manager.sub.filters()?.get(relay))
        manager.updateSubscriptions(keys)
        val caughtUp = manager.sinceFor("a")
        assertEquals(
            first.feeds
                .single()
                .relayCursors
                .since(relay),
            caughtUp,
        )

        val second = Community("b", listOf(Feed()))
        keys = setOf(first, second)
        manager.updateSubscriptions(keys)

        assertEquals(caughtUp, manager.sinceFor("a"), "the first one is not refetched")
        assertNull(manager.sinceFor("b"), "the new one is asked from scratch")
    }

    @Test
    fun theSameCommunityOnTwoScreensIsAskedForTheOneThatHasLess() {
        val screen1 = Feed()
        val manager = Manager { emptySet() }
        manager.updateSubscriptions(setOf(Community("a", listOf(screen1))))
        manager.sub.listener.onEose(relay, manager.sub.filters()?.get(relay))

        manager.updateSubscriptions(setOf(Community("a", listOf(screen1)), Community("a", listOf(Feed()))))

        assertNull(manager.sinceFor("a"), "the second screen holds nothing yet")
    }

    @Test
    fun entitiesWithTheSameWindowShareOneFilter() {
        val a = Community("a", listOf(Feed()))
        val b = Community("b", listOf(Feed()))
        val manager = BatchingManager { emptySet() }

        manager.updateSubscriptions(setOf(a, b))
        val first = manager.sub.filters()?.get(relay)
        assertEquals(listOf(listOf("a", "b")), first?.map { it.tags?.get("A") }, "two new ones are asked together")
        manager.sub.listener.onEose(relay, first)

        manager.updateSubscriptions(setOf(a, b))
        val caughtUp = manager.sub.filters()?.get(relay)
        assertEquals(1, caughtUp?.size, "the same EOSE gave both the same window")
        assertEquals(
            a.feeds
                .single()
                .relayCursors
                .since(relay),
            caughtUp?.single()?.since,
        )

        val c = Community("c", listOf(Feed()))
        manager.updateSubscriptions(setOf(a, b, c))
        val windows =
            manager.sub
                .filters()
                ?.get(relay)
                ?.associate { it.tags?.get("A") to it.since }
        assertEquals<Map<List<String>?, Long?>?>(mapOf(listOf("a", "b") to caughtUp?.single()?.since, listOf("c") to null), windows)
    }
}
