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
package com.vitorpamplona.amethyst.commons.relayClient.event

import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.ICacheEventStream
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingEvents
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.HintIndexer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the extracted (Phase 2b) per-note event-finder filter assembly
 * and the [ICacheProvider] seam it relies on.
 */
class EventFinderFilterAssemblyTest {
    private val relay1 = NormalizedRelayUrl("wss://relay1.test/")
    private val relay2 = NormalizedRelayUrl("wss://relay2.test/")

    /**
     * One batched `ids` filter per relay — NOT one filter per event id — and the
     * ids are sorted deterministically so REQs dedup across rebuilds.
     */
    @Test
    fun `filterMissingEvents batches one filter per relay with sorted ids`() {
        val filters =
            filterMissingEvents(
                mapOf(
                    relay1 to setOf("bbbb", "aaaa", "cccc"),
                    relay2 to setOf("dddd"),
                ),
            )

        assertEquals("one batched filter per relay", 2, filters.size)

        val r1 = filters.first { it.relay == relay1 }
        assertEquals(listOf("aaaa", "bbbb", "cccc"), r1.filter.ids)

        val r2 = filters.first { it.relay == relay2 }
        assertEquals(listOf("dddd"), r2.filter.ids)
    }

    @Test
    fun `filterMissingEvents skips relays with no ids and empty input`() {
        assertTrue(filterMissingEvents(emptyMap()).isEmpty())
        assertTrue(filterMissingEvents(mapOf(relay1 to emptySet())).isEmpty())
    }

    /**
     * The new default [ICacheProvider.checkGetOrCreateUser] must swallow a
     * malformed-key throw and return null (the event-finder follows pubkey hints
     * parsed out of arbitrary events, some of which are junk).
     */
    @Test
    fun `checkGetOrCreateUser tolerates a throwing getOrCreateUser`() {
        val throwing =
            object : StubCache() {
                override fun getOrCreateUser(pubkey: HexKey): User? = throw IllegalArgumentException("bad key")
            }
        assertNull(throwing.checkGetOrCreateUser("not-a-key"))
    }

    @Test
    fun `checkGetOrCreateUser passes through a null result`() {
        assertNull(StubCache().checkGetOrCreateUser("00"))
    }

    /** Minimal [ICacheProvider] that returns nothing; override per test. */
    private open class StubCache : ICacheProvider {
        override val relayHints = HintIndexer()

        override fun getAnyChannel(note: Note): Channel? = null

        override fun getUserIfExists(pubkey: HexKey): User? = null

        override fun countUsers(predicate: (String, User) -> Boolean): Int = 0

        override fun getNoteIfExists(hexKey: HexKey): Note? = null

        override fun checkGetOrCreateNote(hexKey: HexKey): Note? = null

        override fun getOrCreateAddressableNote(key: Address): com.vitorpamplona.amethyst.commons.model.AddressableNote = error("unused")

        override fun getEventStream(): ICacheEventStream = error("unused")

        override fun hasBeenDeleted(event: Any): Boolean = false

        override fun getOrCreateUser(pubkey: HexKey): User? = null

        override fun justConsumeMyOwnEvent(event: Event): Boolean = false
    }
}
