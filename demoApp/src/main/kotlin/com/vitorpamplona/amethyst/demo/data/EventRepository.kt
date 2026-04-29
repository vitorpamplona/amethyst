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
import com.vitorpamplona.quartz.nip01Core.store.sqlite.SQLiteEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generic single-source-of-truth repository over the local [SQLiteEventStore].
 *
 * Generic on the event type [T]. For every [Filter] the UI cares about,
 * [observe] does two things in parallel:
 *
 *  1. Reads existing matches from SQLite and pushes them to [events] so the
 *     UI renders instantly from disk.
 *  2. Sends the same filter to the relay pool. New events arrive through
 *     [SubscriptionListener.onEvent], get verified, narrowed via [accept],
 *     persisted, and re-emitted through the same flow.
 *
 * Signing is out of scope: pass an already-signed [Event] to [publish] and
 * the repository will ingest it locally and broadcast it to [relays].
 *
 * @param accept narrows a generic [Event] from disk or relay into the
 *               concrete subtype [T] this repository tracks. Return `null`
 *               to drop the event (wrong kind, fails app-level rules, etc).
 *               Quartz's [com.vitorpamplona.quartz.utils.EventFactory] already
 *               parses each event into its NIP-specific subclass, so a simple
 *               `it as? TextNoteEvent` is usually enough.
 */
class EventRepository<T : Event>(
    private val store: SQLiteEventStore,
    private val client: NostrClient,
    private val relays: Set<NormalizedRelayUrl>,
    private val scope: CoroutineScope,
    private val accept: (Event) -> T?,
) {
    private val state = MutableStateFlow<List<T>>(emptyList())
    val events: StateFlow<List<T>> = state.asStateFlow()

    private val ingestLock = Mutex()
    private var subscriptionId: String? = null

    /**
     * Start observing events matching [filter].
     *
     * Hits the local DB immediately and opens a relay subscription that
     * keeps the in-memory list in sync as new events arrive. Calling
     * [observe] again replaces the previous subscription.
     */
    suspend fun observe(filter: Filter) {
        // 1. Local DB query — populate the UI from disk first.
        val cached: List<Event> = store.query(filter)
        state.value = cached.mapNotNull(accept).sortedByDescending { it.createdAt }

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

    /** Stop the active relay subscription, if any. The local cache is kept. */
    fun stop() {
        subscriptionId?.let { client.unsubscribe(it) }
        subscriptionId = null
    }

    /**
     * Broadcast an already-signed [event] to [relays] and surface it to the
     * UI immediately, without waiting for the relay round-trip.
     */
    suspend fun publish(event: Event) {
        ingest(event)
        client.publish(event, relays)
    }

    private suspend fun ingest(rawEvent: Event) {
        if (!rawEvent.verify()) return
        val event = accept(rawEvent) ?: return

        ingestLock.withLock {
            val current = state.value
            if (current.any { it.id == event.id }) return
            state.value = (listOf(event) + current).sortedByDescending { it.createdAt }
        }

        runCatching { store.insertEvent(event) }
    }
}
