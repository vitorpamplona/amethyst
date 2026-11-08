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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The account switch that lost notifications and half the home feed on desktop: the assemblers are
 * app-wide, so the cursors an account earned stayed after its view models (and the feeds that held
 * its notes) were cleared. Coming back asked the relays only for what was newer.
 */
class PerUserAndFollowListFeedOwnerTest {
    private val relay = NormalizedRelayUrl("wss://relay.example/")
    private val context = UserContext { Note("addr") }
    private val jack = User("aa".repeat(32), context)
    private val fiatjaf = User("bb".repeat(32), context)

    private class Key(
        val user: User,
        val feed: Any,
    )

    private class Manager(
        keys: () -> Set<Key>,
    ) : PerUserAndFollowListEoseManager<Key, String>(EmptyNostrClient(), keys) {
        val sinceAsked = mutableMapOf<User, SincePerRelayMap?>()

        override fun updateFilter(
            key: Key,
            since: SincePerRelayMap?,
        ): List<RelayBasedFilter>? {
            sinceAsked[key.user] = since
            return null
        }

        override fun user(key: Key) = key.user

        override fun list(key: Key) = "AllFollows"

        override fun feedOwner(key: Key) = key.feed
    }

    @Test
    fun `switching away and back drops the cursors of the account that left`() {
        var keys = emptySet<Key>()
        val manager = Manager { keys }

        val jackFirstFeed = Any()
        keys = setOf(Key(jack, jackFirstFeed))
        manager.updateSubscriptions(keys)
        manager.newEose(Key(jack, jackFirstFeed), relay, 1000)

        // Still the same feed: the cursor holds.
        manager.updateSubscriptions(keys)
        assertEquals(1000L, manager.sinceAsked[jack]?.get(relay)?.time)

        // Switch to another account: jack's view models, and the feed holding his notes, are cleared.
        keys = setOf(Key(fiatjaf, Any()))
        manager.updateSubscriptions(keys)

        // Back to jack with a new, empty feed: ask from scratch.
        keys = setOf(Key(jack, Any()))
        manager.updateSubscriptions(keys)
        assertNull(manager.sinceAsked[jack])
    }
}
