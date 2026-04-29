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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.store.sqlite.SQLiteEventStore
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for the UI is the local [SQLiteEventStore].
 *
 * For every filter the UI cares about, [observe] does two things in parallel:
 *
 *  1. Reads existing matches from the SQLite store and pushes them to a
 *     [StateFlow] so the UI renders instantly from disk.
 *  2. Sends the same filter to the relay pool. New events arrive through
 *     [SubscriptionListener.onEvent], get verified, written to the store,
 *     and re-emitted through the same flow.
 *
 * The UI never talks to relays directly — it just collects the flow.
 */
class Kind1Repository(
    private val store: SQLiteEventStore,
    private val client: NostrClient,
    private val signer: NostrSigner,
    private val relays: Set<NormalizedRelayUrl>,
    private val scope: CoroutineScope,
) {
    private val notesState = MutableStateFlow<List<TextNoteEvent>>(emptyList())
    val notes: StateFlow<List<TextNoteEvent>> = notesState.asStateFlow()

    private val ingestLock = Mutex()
    private var subscriptionId: String? = null

    /**
     * Start observing kind:1 notes matching [filter].
     *
     * Hits the local DB immediately and opens a relay subscription that keeps
     * the in-memory list in sync as new events arrive.
     */
    suspend fun observe(filter: Filter) {
        // 1. Local DB query — populate the UI from disk first.
        val cached: List<TextNoteEvent> = store.query(filter)
        notesState.value = cached.sortedByDescending { it.createdAt }

        // 2. Relay subscription — same filter, fanned out to every configured relay.
        subscriptionId?.let { client.unsubscribe(it) }
        val subId = newSubId()
        subscriptionId = subId
        client.subscribe(
            subId = subId,
            filters = relays.associateWith { listOf(filter) },
            listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        scope.launch { ingest(event) }
                    }
                },
        )
    }

    /**
     * Sign and publish a new kind:1 note. The store and the UI flow get
     * updated as soon as the event is built — no need to wait for a relay
     * round-trip.
     */
    suspend fun publish(content: String) {
        val template = TextNoteEvent.build(content)
        val signed = signer.sign<TextNoteEvent>(template)
        ingest(signed)
        client.publish(signed, relays)
    }

    private suspend fun ingest(event: Event) {
        if (event !is TextNoteEvent) return
        if (!event.verify()) return

        ingestLock.withLock {
            val current = notesState.value
            if (current.any { it.id == event.id }) return
            notesState.value = (listOf(event) + current).sortedByDescending { it.createdAt }
        }

        runCatching { store.insertEvent(event) }
    }
}
