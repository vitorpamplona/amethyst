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

import com.vitorpamplona.amethyst.commons.model.observables.IFilter
import com.vitorpamplona.amethyst.commons.model.observables.NoteSetMatchingFilter
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A relay's feed takes in every note seen on that relay, and a note already cached can be seen on
 * it later: the same event, from another relay, is no new event. A candidate set that reads
 * `Note.relays` hears about it all the same.
 */
class EventCacheRelayWatchTest {
    private val signer = NostrSignerSync(KeyPair())
    private val first = NormalizedRelayUrl("wss://first.example.com/")
    private val watched = NormalizedRelayUrl("wss://watched.example.com/")

    private fun post(): TextNoteEvent = signer.sign(1_000, TextNoteEvent.KIND, emptyArray(), "hi")

    private val onWatched = IFilter { it.hasRelay(watched) }

    @Test
    fun aCachedNoteSeenOnTheRelayLaterJoinsTheSet() {
        val cache = EventCache()
        val set = NoteSetMatchingFilter(onWatched, watchesRelays = true)
        cache.registerNoteSet(set)

        val event = post()
        cache.justConsume(event, first, true)
        assertEquals(0, set.snapshot()?.size)

        cache.justConsume(event, watched, true)
        assertEquals(setOf(event.id), set.snapshot()?.map { it.idHex }?.toSet())
    }

    @Test
    fun aSetThatDoesNotAskIsNotTold() {
        val cache = EventCache()
        val set = NoteSetMatchingFilter(onWatched)
        cache.registerNoteSet(set)

        val event = post()
        cache.justConsume(event, first, true)
        cache.justConsume(event, watched, true)
        assertEquals(0, set.snapshot()?.size, "only sets that read relays pay for the hook")
    }

    @Test
    fun anUnregisteredSetIsNotTold() {
        val cache = EventCache()
        val set = NoteSetMatchingFilter(onWatched, watchesRelays = true)
        cache.registerNoteSet(set)
        cache.unregisterNoteSet(set)

        val event = post()
        cache.justConsume(event, first, true)
        cache.justConsume(event, watched, true)
        assertEquals(0, set.snapshot()?.size)
    }
}
