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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A channel keeps its newest messages when memory is short. Its history pager already paged past
 * the dropped ones: without a rewind it would never ask for them again, and scrolling back would
 * end at a hole. DMs rewound already; relay groups and Concord channels now do too.
 */
class CachePrunerChannelHistoryTest {
    private val relay = NormalizedRelayUrl("wss://groups.example.com/")
    private val author = "a".repeat(64)

    private fun message(i: Int) =
        Note(i.toString(16).padStart(64, '0')).also {
            it.event = Event(it.idHex, author, i.toLong(), 9, emptyArray(), "msg $i", "")
            it.addRelaySync(relay)
        }

    @Test
    fun aRelayGroupThatPagedPastTheDroppedMessagesAsksForThemAgain() {
        val cache = EventCache()
        val group = cache.getOrCreateRelayGroupChannel(GroupId("g", relay))
        val messages = (1..600).map { message(it) }
        messages.forEach { group.addNote(it, relay) }

        // History paged down to the oldest message and found nothing below it.
        val history = group.history
        history.floor = 1_000
        assertTrue(history.advance(relay, 1_000))
        history.onEvent(relay, 1)
        history.onEose(relay)
        assertTrue(history.advance(relay, 1_000))
        history.onEose(relay)
        assertTrue(history.isDone(relay))

        cache.pruner.pruneOldMessages()

        assertEquals(500, group.notes.size())
        assertFalse(history.isDone(relay), "the dropped band is below the floor: page it again")
        assertEquals(101L, history.reachedUntilFor(relay, 0), "from just above the newest dropped message")
        // Kept alive until here: the cache holds notes weakly.
        assertEquals(600, messages.size)
    }
}
