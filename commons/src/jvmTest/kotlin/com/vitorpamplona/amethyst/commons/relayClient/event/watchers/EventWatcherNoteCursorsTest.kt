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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderQueryState
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderAccount
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reply, reaction and zap counters under a note keep their relay cursors on the [Note], which
 * holds those children. A note LocalCache rebuilt for the same id (after an account switch away and
 * back) has none of them, so it must be asked from scratch: by id, it kept the old cursor and showed
 * 5 replies and 36 likes instead of 119 and 424 for good.
 */
class EventWatcherNoteCursorsTest {
    private val relay = NormalizedRelayUrl("wss://relay.test/")
    private val id = "a".repeat(64)

    private fun note() = Note(id).also { it.addRelaySync(relay) }

    private fun EventWatcherSubAssembler.windows() =
        sub
            .filters()
            ?.get(relay)
            .orEmpty()
            .map { it.since }

    private fun EventWatcherSubAssembler.answer() = sub.listener.onEose(relay, sub.filters()?.get(relay))

    @Test
    fun aNoteRebuiltForTheSameIdIsAskedFromScratch() {
        val first = note()
        var keys = setOf(EventFinderQueryState(first, StubAccount()))
        val watcher = EventWatcherSubAssembler(EmptyNostrClient()) { keys }

        watcher.updateSubscriptions(keys)
        assertTrue(watcher.windows().isNotEmpty() && watcher.windows().all { it == null }, "a new note is asked from scratch")
        watcher.answer()
        watcher.updateSubscriptions(keys)
        val caughtUp = first.childCursorsOrNull()?.since(relay)
        assertNotNull(caughtUp)
        assertTrue(watcher.windows().all { it == caughtUp }, "then only for what came after")

        // LocalCache let the note go with its children and built a new one for the same id.
        val rebuilt = note()
        keys = setOf(EventFinderQueryState(rebuilt, StubAccount()))
        val stale = watcher.sub.filters()?.get(relay)
        watcher.updateSubscriptions(keys)
        assertTrue(watcher.windows().all { it == null }, "the rebuilt note holds nothing: from scratch")

        // The REQ planned for the old object answers late: it fetched only what that one lacked.
        watcher.sub.listener.onEose(relay, stale)
        assertNull(rebuilt.childCursorsOrNull()?.since(relay))

        watcher.answer()
        assertNotNull(rebuilt.childCursorsOrNull()?.since(relay), "its own from-scratch answer counts")
    }

    @Test
    fun notesCaughtUpTogetherShareAFilterAndANewOneIsAskedApart() {
        val old = note()
        var keys = setOf(EventFinderQueryState(old, StubAccount()))
        val watcher = EventWatcherSubAssembler(EmptyNostrClient()) { keys }
        watcher.updateSubscriptions(keys)
        watcher.answer()

        val other = Note("b".repeat(64)).also { it.addRelaySync(relay) }
        keys = setOf(EventFinderQueryState(old, StubAccount()), EventFinderQueryState(other, StubAccount()))
        watcher.updateSubscriptions(keys)

        val byNote =
            watcher.sub
                .filters()
                ?.get(relay)
                .orEmpty()
                .groupBy({ it.tags?.get("e")?.single() }, { it.since })
        assertTrue(byNote.getValue(old.idHex).all { it == old.childCursorsOrNull()?.since(relay) })
        assertTrue(byNote.getValue(other.idHex).all { it == null })
        assertEquals(setOf(old.idHex, other.idHex), byNote.keys.filterNotNull().toSet())
    }

    private class StubAccount : UserFinderAccount {
        override val userFinderPubkeyHex: HexKey = "00".repeat(32)

        override fun indexRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun outboxHomeRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun searchRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun searchOnlyRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun followPlusAllMineWithSearchRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun commonRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun cardHomeRelays(): Set<NormalizedRelayUrl> = emptySet()

        override fun trustProvider(): ServiceProviderTag? = null

        override fun followerCountProvider(): ServiceProviderTag? = null

        override fun declaredFollowsByOutboxRelay(): Map<NormalizedRelayUrl, Set<HexKey>> = emptyMap()
    }
}
