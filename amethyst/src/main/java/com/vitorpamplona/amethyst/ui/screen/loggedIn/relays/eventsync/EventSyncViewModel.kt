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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Syncs the user's events across all known relays:
 *  1. Downloads all events authored by the user and sends them to their outbox relays.
 *  2. Downloads all events that p-tag the user (non-DM) and sends them to their inbox relays.
 *  3. Downloads kind-4 and kind-1059 events that p-tag the user and sends them to DM relays.
 *
 * Up to [MAX_CONCURRENT_RELAYS] relays are queried in parallel. As soon as one relay is fully
 * exhausted (all pages retrieved) the next relay from the list starts immediately, keeping the
 * concurrency window full at all times.
 *
 * Each relay is paginated individually: after EOSE the oldest [Event.createdAt] seen on that
 * relay becomes the next `until` cursor, repeating until the relay returns no new events.
 *
 * The sync is pausable: calling [cancel] transitions to [SyncState.Paused] so the user can
 * [resume] from the last completed relay index rather than starting over.
 *
 * Scoped to the AccountViewModel so the sync survives navigation within the same session.
 */
class EventSyncViewModel(
    val account: Account,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Maximum number of relays queried at the same time. */
        const val MAX_CONCURRENT_RELAYS = 50

        /** How long (ms) to wait for a single relay to reply per page before giving up. */
        const val RELAY_TIMEOUT_MS = 30_000L
    }

    sealed class SyncState {
        object Idle : SyncState()

        data class Running(
            val relaysCompleted: Int,
            val totalRelays: Int,
            val eventsSent: Int,
        ) : SyncState()

        /** Cancelled mid-run; can be resumed from [nextRelayIndex]. */
        data class Paused(
            val nextRelayIndex: Int,
            val totalRelays: Int,
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
                runSync(startRelayIndex = 0, initialEventsSent = 0)
            }
    }

    fun resume() {
        val paused = _syncState.value as? SyncState.Paused ?: return
        if (_syncState.value is SyncState.Running) return
        syncJob =
            scope.launch(Dispatchers.IO) {
                runSync(
                    startRelayIndex = paused.nextRelayIndex,
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
                    nextRelayIndex = maxOf(0, current.relaysCompleted - MAX_CONCURRENT_RELAYS),
                    totalRelays = current.totalRelays,
                    eventsSent = current.eventsSent,
                )
            } else {
                SyncState.Idle
            }
    }

    private suspend fun runSync(
        startRelayIndex: Int,
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

        val relaysToProcess = if (startRelayIndex > 0) allRelays.drop(startRelayIndex) else allRelays
        val totalRelays = allRelays.size

        val outboxTargets = account.outboxRelays.flow.value
        val inboxTargets = account.nip65RelayList.inboxFlow.value
        val dmTargets = account.dmRelays.flow.value

        val baseFilters =
            buildList {
                if (outboxTargets.isNotEmpty()) add(Filter(authors = listOf(myPubKey)))
                if (inboxTargets.isNotEmpty() || dmTargets.isNotEmpty()) {
                    add(Filter(tags = mapOf("p" to listOf(myPubKey))))
                }
            }

        if (baseFilters.isEmpty()) {
            _syncState.value =
                SyncState.Error("No outbox, inbox, or DM relays configured.")
            return
        }

        // Thread-safe dedup sets and counter — relay workers run concurrently.
        val outboxSent = ConcurrentHashMap.newKeySet<String>()
        val inboxSent = ConcurrentHashMap.newKeySet<String>()
        val dmSent = ConcurrentHashMap.newKeySet<String>()
        val totalSent = AtomicLong(initialEventsSent.toLong())

        val relaysCompleted = AtomicInteger(startRelayIndex)

        _syncState.value =
            SyncState.Running(
                relaysCompleted = relaysCompleted.get(),
                totalRelays = totalRelays,
                eventsSent = totalSent.get().toInt(),
            )

        try {
            downloadFromPool(
                relays = relaysToProcess,
                baseFilters = baseFilters,
                onEvent = { event ->
                    if (event.pubKey == myPubKey && outboxTargets.isNotEmpty()) {
                        if (outboxSent.add(event.id)) {
                            account.client.send(event, outboxTargets)
                            totalSent.incrementAndGet()
                        }
                    }
                    val pTagsMe =
                        event.tags.any { tag ->
                            tag.size >= 2 && tag[0] == "p" && tag[1] == myPubKey
                        }
                    if (pTagsMe) {
                        if (event.kind == 4 || event.kind == 1059) {
                            if (dmTargets.isNotEmpty() && dmSent.add(event.id)) {
                                account.client.send(event, dmTargets)
                                totalSent.incrementAndGet()
                            }
                        } else {
                            if (inboxTargets.isNotEmpty() && inboxSent.add(event.id)) {
                                account.client.send(event, inboxTargets)
                                totalSent.incrementAndGet()
                            }
                        }
                    }
                },
                onRelayComplete = {
                    val completed = relaysCompleted.incrementAndGet()
                    _syncState.value =
                        SyncState.Running(
                            relaysCompleted = completed,
                            totalRelays = totalRelays,
                            eventsSent = totalSent.get().toInt(),
                        )
                },
            )

            _syncState.value =
                SyncState.Done(
                    totalEventsSent = totalSent.get().toInt(),
                    durationMs = System.currentTimeMillis() - startTime,
                )
        } catch (e: Exception) {
            if (isActive) {
                _syncState.value = SyncState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Maintains a sliding window of up to [MAX_CONCURRENT_RELAYS] active relay workers.
     * As soon as one relay finishes (all pages exhausted), the next relay from [relays]
     * starts immediately — no waiting for an entire batch to drain.
     */
    private suspend fun downloadFromPool(
        relays: List<NormalizedRelayUrl>,
        baseFilters: List<Filter>,
        onEvent: (Event) -> Unit,
        onRelayComplete: () -> Unit,
    ) {
        val semaphore = Semaphore(MAX_CONCURRENT_RELAYS)
        supervisorScope {
            for (relay in relays) {
                if (!isActive) break
                semaphore.acquire()
                launch {
                    try {
                        downloadFromRelay(relay, baseFilters, onEvent)
                        onRelayComplete()
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
    }

    /**
     * Fetches all pages from a single [relay] using paginated `until` cursors.
     *
     * Sends [baseFilters] and waits for EOSE. If events were returned, the oldest
     * [Event.createdAt] minus one becomes the next `until` and the query repeats.
     * Stops when EOSE arrives with no new events (relay exhausted) or the relay
     * cannot be reached.
     */
    private suspend fun downloadFromRelay(
        relay: NormalizedRelayUrl,
        baseFilters: List<Filter>,
        onEvent: (Event) -> Unit,
    ) {
        var until: Long? = null

        while (isActive) {
            val pageCount = AtomicInteger(0)
            val pageMinTs = AtomicLong(Long.MAX_VALUE)
            val done = Channel<Unit>(Channel.CONFLATED)
            val subId = newSubId()

            val filters =
                if (until == null) {
                    baseFilters
                } else {
                    baseFilters.map { it.copy(until = until) }
                }

            val listener =
                object : IRequestListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        onEvent(event)
                        pageCount.incrementAndGet()
                        pageMinTs.updateAndGet { minOf(it, event.createdAt) }
                    }

                    override fun onEose(
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        done.trySend(Unit)
                    }

                    override fun onClosed(
                        message: String,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        done.trySend(Unit)
                    }

                    override fun onCannotConnect(
                        relay: NormalizedRelayUrl,
                        message: String,
                        forFilters: List<Filter>?,
                    ) {
                        done.trySend(Unit)
                    }
                }

            account.client.openReqSubscription(subId, mapOf(relay to filters), listener)
            withTimeoutOrNull(RELAY_TIMEOUT_MS) { done.receive() }
            account.client.close(subId)
            done.close()

            if (pageCount.get() == 0) break  // relay exhausted or unreachable

            // Advance cursor: next page starts just before the oldest event seen.
            until = pageMinTs.get() - 1
        }
    }
}
