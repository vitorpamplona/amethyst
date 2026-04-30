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
package com.vitorpamplona.amethyst.demo.data

import com.vitorpamplona.quartz.nip01Core.cache.projection.ProjectionState
import com.vitorpamplona.quartz.nip01Core.cache.projection.filterItems
import com.vitorpamplona.quartz.nip01Core.cache.projection.project
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.ObservableEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/**
 * The "kind:1 new threads" feed as a cold [Flow], independent of any
 * UI / ViewModel scaffolding.
 *
 * While [notes] is being collected this feed holds open one relay
 * subscription for [filter]; cancellation tears it down. Reusable
 * outside ViewModels (tests, CLI commands, debug tools).
 *
 * [relays] is exposed because publishing is the consumer's
 * responsibility — typically the ViewModel — and the publish target
 * should match the relays this feed pulls from.
 */
class NotesFeed(
    private val db: ObservableEventStore,
    private val client: NostrClient,
) {
    private val subId = newSubId()
    private val filter = Filter(kinds = listOf(TextNoteEvent.KIND), limit = 100)

    val relays =
        setOf(
            "wss://relay.damus.io".normalizeRelayUrl(),
            "wss://nos.lol".normalizeRelayUrl(),
            "wss://relay.nostr.band".normalizeRelayUrl(),
        )

    val notes: Flow<ProjectionState<TextNoteEvent>> =
        db
            .project<TextNoteEvent>(filter)
            .filterItems { it.value.isNewThread() }
            .onStart { client.subscribe(subId, relays.associateWith { listOf(filter) }) }
            .onCompletion { client.unsubscribe(subId) }
}

/** A kind:1 note is a "new thread" iff it isn't a reply to anything. */
private fun TextNoteEvent.isNewThread(): Boolean = replyingTo() == null
