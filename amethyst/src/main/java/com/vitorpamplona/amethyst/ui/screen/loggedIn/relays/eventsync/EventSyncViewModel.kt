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
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Syncs the user's events across all known relays:
 *  1. Downloads all events authored by the user and sends them to their outbox relays.
 *  2. Downloads all events that p-tag the user (non-DM) and sends them to their inbox relays.
 *  3. Downloads kind-4 and kind-1059 events that p-tag the user and sends them to DM relays.
 *
 * Relays are processed in batches of [CHUNK_SIZE].  All three filters are sent to every relay
 * in a chunk simultaneously, and events are routed to the appropriate destination by their
 * properties.  This removes the need for three sequential passes over the full relay list.
 *
 * The sync is pausable: calling [cancel] transitions to [SyncState.Paused] so the user can
 * [resume] from the last completed chunk rather than starting over.
 *
 * Scoped to the AccountViewModel so the sync survives navigation within the same session.
 */
class EventSyncViewModel(
    val account: Account,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Number of relays queried in each batch. */
        const val CHUNK_SIZE = 50

        /** How long (ms) to wait for all relays in a chunk to reply before moving on. */
        const val CHUNK_TIMEOUT_MS = 120_000L
    }

    sealed class SyncState {
        object Idle : SyncState()

        data class Running(
            val chunkIndex: Int,
            val totalChunks: Int,
            val eventsSent: Int,
        ) : SyncState()

        /** Cancelled mid-run; can be resumed from [nextChunkIndex]. */
        data class Paused(
            val nextChunkIndex: Int,
            val totalChunks: Int,
            val eventsSent: Int,
        ) : SyncState()

        data class Done(
            val totalEventsSent: Int,
            val durationMs: Long,
        ) : SyncState()

        data class Error(
            val message: String,
        ) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private var syncJob: Job? = null

    fun start() {
        if (_syncState.value is SyncState.Running) return
        syncJob =
            scope.launch(Dispatchers.IO) {
                runSync(startChunkIndex = 0, initialEventsSent = 0)
            }
    }

    fun resume() {
        val paused = _syncState.value as? SyncState.Paused ?: return
        if (_syncState.value is SyncState.Running) return
        syncJob =
            scope.launch(Dispatchers.IO) {
                runSync(
                    startChunkIndex = paused.nextChunkIndex,
                    initialEventsSent = paused.eventsSent,
                )
            }
    }

    fun cancel() {
        syncJob?.cancel()
        val current = _syncState.value
        _syncState.value =
            if (current is SyncState.Running) {
                SyncState.Paused(
                    nextChunkIndex = current.chunkIndex - 1, // retry the in-progress chunk
                    totalChunks = current.totalChunks,
                    eventsSent = current.eventsSent,
                )
            } else {
                SyncState.Idle
            }
    }

    private suspend fun runSync(
        startChunkIndex: Int,
        initialEventsSent: Int,
    ) {
        val startTime = System.currentTimeMillis()
        val myPubKey = account.signer.pubKey

        val allRelays =
            account.cache.relayHints.relayDB
                .keys()
                .toList()

        if (allRelays.isEmpty()) {
            _syncState.value =
                SyncState.Error("No known relays found. Browse some content first to discover relays.")
            return
        }

        val chunks = allRelays.chunked(CHUNK_SIZE)
        val totalChunks = chunks.size

        val outboxTargets = account.outboxRelays.flow.value
        val inboxTargets = account.nip65RelayList.inboxFlow.value
        val dmTargets = account.dmRelays.flow.value

        // Build the minimal set of filters needed across all three categories.
        // Events are routed to their correct destination(s) by property after retrieval.
        val filters =
            buildList {
                if (outboxTargets.isNotEmpty()) add(Filter(authors = listOf(myPubKey)))
                if (inboxTargets.isNotEmpty() || dmTargets.isNotEmpty()) {
                    add(Filter(tags = mapOf("p" to listOf(myPubKey))))
                }
            }

        if (filters.isEmpty()) {
            _syncState.value =
                SyncState.Error("No outbox, inbox, or DM relays configured.")
            return
        }

        // Deduplication sets — one per destination category.
        val outboxSent = HashSet<String>(1024)
        val inboxSent = HashSet<String>(1024)
        val dmSent = HashSet<String>(1024)

        var totalSent = initialEventsSent

        try {
            for (i in startChunkIndex until totalChunks) {
                if (!isActive) return

                _syncState.value =
                    SyncState.Running(
                        chunkIndex = i + 1,
                        totalChunks = totalChunks,
                        eventsSent = totalSent,
                    )

                downloadFromChunk(
                    relays = chunks[i],
                    filters = filters,
                ) { event ->
                    // Route to outbox if I authored it.
                    if (event.pubKey == myPubKey && outboxTargets.isNotEmpty()) {
                        if (outboxSent.add(event.id)) {
                            account.client.send(event, outboxTargets)
                            totalSent++
                        }
                    }
                    // Route p-tagged events to inbox or DM relays.
                    val pTagsMe =
                        event.tags.any { tag ->
                            tag.size >= 2 && tag[0] == "p" && tag[1] == myPubKey
                        }
                    if (pTagsMe) {
                        if (event.kind == 4 || event.kind == 1059) {
                            if (dmTargets.isNotEmpty() && dmSent.add(event.id)) {
                                account.client.send(event, dmTargets)
                                totalSent++
                            }
                        } else {
                            if (inboxTargets.isNotEmpty() && inboxSent.add(event.id)) {
                                account.client.send(event, inboxTargets)
                                totalSent++
                            }
                        }
                    }
                }

                // Update the running count after the chunk completes.
                if (isActive) {
                    _syncState.value =
                        SyncState.Running(
                            chunkIndex = i + 1,
                            totalChunks = totalChunks,
                            eventsSent = totalSent,
                        )
                }
            }

            _syncState.value =
                SyncState.Done(
                    totalEventsSent = totalSent,
                    durationMs = System.currentTimeMillis() - startTime,
                )
        } catch (e: Exception) {
            if (isActive) {
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sends [baseFilters] to every relay in [relays] simultaneously, then paginates per relay
     * until each one is exhausted.
     *
     * Many relays impose a hard event-count limit per filter (commonly 500).  After EOSE the
     * oldest [Event.createdAt] seen on that relay becomes the next `until` cursor, so the
     * following request fetches the next page.  This repeats until a relay returns no new
     * events, at which point it is removed from the active set.
     *
     * All relays in [relays] are queried in parallel within each pagination round.  Only the
     * relays that still have more pages participate in subsequent rounds.
     */
    private suspend fun downloadFromChunk(
        relays: List<NormalizedRelayUrl>,
        baseFilters: List<Filter>,
        onEvent: (Event) -> Unit,
    ) {
        // `until` cursor per relay; absent = first page (no cursor).
        val relayUntil = ConcurrentHashMap<NormalizedRelayUrl, Long>()
        // Relays that can no longer be contacted are skipped immediately.
        val deadRelays = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

        val activeRelays = relays.toMutableList()

        while (activeRelays.isNotEmpty() && isActive) {
            // Per-relay event count and oldest timestamp for this round.
            val roundCount = ConcurrentHashMap<NormalizedRelayUrl, Int>()
            val roundMinTs = ConcurrentHashMap<NormalizedRelayUrl, Long>()

            val remaining = AtomicInteger(activeRelays.size)
            val done = Channel<Unit>(Channel.CONFLATED)
            val subId = newSubId()

            val listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        onEvent(event)
                        roundCount.merge(relay, 1, Int::plus)
                        roundMinTs.merge(relay, event.createdAt, ::minOf)
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (remaining.decrementAndGet() <= 0) done.trySend(Unit)
                    }

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (remaining.decrementAndGet() <= 0) done.trySend(Unit)
                    }

                    override fun onCannotConnect(
                        relay: NormalizedRelayUrl,
                        message: String,
                        forFilters: List<Filter>?,
                    ) {
                        deadRelays.add(relay)
                        if (remaining.decrementAndGet() <= 0) done.trySend(Unit)
                    }
                }

            // Build per-relay filter list, injecting each relay's current `until` cursor.
            val filtersMap =
                activeRelays.associateWith { relay ->
                    val until = relayUntil[relay]
                    if (until == null) {
                        baseFilters
                    } else {
                        baseFilters.map { it.copy(until = until) }
                    }
                }

            account.client.openReqSubscription(subId, filtersMap, listener)
            withTimeoutOrNull(CHUNK_TIMEOUT_MS) { done.receive() }
            account.client.close(subId)
            done.close()

            // Advance cursors for relays that returned events; drop exhausted or dead ones.
            activeRelays.removeAll { relay ->
                when {
                    relay in deadRelays -> true
                    (roundCount[relay] ?: 0) == 0 -> true  // EOSE with no events = exhausted
                    else -> {
                        // Next page starts just before the oldest event seen this round.
                        relayUntil[relay] = roundMinTs[relay]!! - 1
                        false
                    }
                }
            }
        }
    }
}
