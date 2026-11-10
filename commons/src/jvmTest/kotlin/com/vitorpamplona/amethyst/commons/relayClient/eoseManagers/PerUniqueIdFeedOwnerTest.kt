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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A hashtag or geohash screen closed and opened again: the cursors earned for that id were kept
 * app-wide while the screen's feed (and the notes it held) went away. Reproduced on desktop:
 * `#fotografie` reopened after a GC showed 191 notes instead of 534.
 */
class PerUniqueIdFeedOwnerTest {
    private val relay = NormalizedRelayUrl("wss://relay.example/")

    private class Key(
        val tag: String,
        val owner: Any,
    )

    private class Manager(
        keys: () -> Set<Key>,
    ) : PerUniqueIdEoseManager<Key, String>(EmptyNostrClient(), keys) {
        val sinceAsked = mutableMapOf<String, SincePerRelayMap?>()

        override fun updateFilter(
            key: Key,
            since: SincePerRelayMap?,
        ): List<RelayBasedFilter>? {
            sinceAsked[key.tag] = since
            return null
        }

        override fun id(key: Key) = key.tag

        override fun feedOwner(key: Key) = key.owner
    }

    @Test
    fun reopeningTheScreenDropsTheCursorsOfTheClosedOne() {
        var keys = emptySet<Key>()
        val manager = Manager { keys }

        val firstScreen = Any()
        keys = setOf(Key("fotografie", firstScreen))
        manager.updateSubscriptions(keys)
        manager.newEose(Key("fotografie", firstScreen), relay, 1000)

        manager.updateSubscriptions(keys)
        assertEquals(1000L, manager.sinceAsked["fotografie"]?.get(relay)?.time)

        // Closed, then opened again: a new screen feed for the same tag.
        keys = emptySet()
        manager.updateSubscriptions(keys)
        keys = setOf(Key("fotografie", Any()))
        manager.updateSubscriptions(keys)
        assertNull(manager.sinceAsked["fotografie"])
    }
}
