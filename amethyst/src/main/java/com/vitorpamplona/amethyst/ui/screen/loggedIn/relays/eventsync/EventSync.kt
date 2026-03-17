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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.normalizeRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.isTaggedUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
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
import kotlin.coroutines.coroutineContext

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
 * OK (true) responses from destination relays are tracked via [IRelayClientListener] and
 * attributed back to the source relay that contributed each event.
 *
 * Live activity is emitted via [liveActivity] so the UI can show a per-relay log of events
 * received and events accepted by destination relays.
 *
 * The sync is pausable: calling [cancel] transitions to [SyncState.Paused] so the user can
 * [resume] from the last completed relay index rather than starting over.
 *
 * Scoped to the AccountViewModel so the sync survives navigation within the same session.
 */
class EventSync(
    val account: Account,
    private val scope: CoroutineScope,
) {
    companion object {
        /** Maximum number of relays queried at the same time. */
        const val MAX_CONCURRENT_RELAYS = 50

        /** How long (ms) to wait for a single relay to reply per page before giving up. */
        const val RELAY_TIMEOUT_MS = 30_000L

        /** Maximum number of completed-relay entries kept in the activity log. */
        const val MAX_ACTIVITY_LOG = 5000
    }

    // -------------------------------------------------------------------------
    // Public state
    // -------------------------------------------------------------------------

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
            val totalEventsAccepted: Int,
            val durationMs: Long,
        ) : SyncState()

        data class Error(
            val message: String,
        ) : SyncState()
    }

    /**
     * Per-relay activity snapshot emitted continuously while the sync runs.
     *
     * @param recentCompletions  Last [MAX_ACTIVITY_LOG] relays that finished, newest first.
     * @param outboxTargets  Relays receiving events authored by the user.
     * @param inboxTargets  Relays receiving events that mention the user.
     * @param dmTargets  Relays receiving DMs addressed to the user.
     */
    data class LiveSyncActivity(
        val recentCompletions: List<CompletedRelayInfo> = emptyList(),
        val outboxTargets: Set<NormalizedRelayUrl> = emptySet(),
        val inboxTargets: Set<NormalizedRelayUrl> = emptySet(),
        val dmTargets: Set<NormalizedRelayUrl> = emptySet(),
    ) {
        /**
         * @param eventsFound  Total events received from this relay across all pages.
         * @param eventsAccepted  Events from this relay that destination relays accepted as new
         *                        (OK true). Reflects the count at relay-completion time; late
         *                        OK responses may not be included.
         */
        data class CompletedRelayInfo(
            val relay: NormalizedRelayUrl,
            val eventsFound: Int,
            val eventsAccepted: Int,
        )
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    private val _liveActivity = MutableStateFlow(LiveSyncActivity())
    val liveActivity: StateFlow<LiveSyncActivity> = _liveActivity

    // -------------------------------------------------------------------------
    // Live activity tracking (written from worker threads)
    // -------------------------------------------------------------------------

    private val trackingCompletions = ArrayDeque<LiveSyncActivity.CompletedRelayInfo>()
    private val trackingLock = Any()

    /** Destination relay sets, set at the start of each sync run. */
    @Volatile private var liveOutboxTargets: Set<NormalizedRelayUrl> = emptySet()

    @Volatile private var liveInboxTargets: Set<NormalizedRelayUrl> = emptySet()

    @Volatile private var liveDmTargets: Set<NormalizedRelayUrl> = emptySet()

    private fun emitLiveSnapshot() {
        val completions = synchronized(trackingLock) { trackingCompletions.toList() }
        _liveActivity.value =
            LiveSyncActivity(
                recentCompletions = completions,
                outboxTargets = liveOutboxTargets,
                inboxTargets = liveInboxTargets,
                dmTargets = liveDmTargets,
            )
    }

    // -------------------------------------------------------------------------
    // Control functions
    // -------------------------------------------------------------------------

    private var syncJob: Job? = null

    fun start() {
        if (_syncState.value is SyncState.Running) return
        synchronized(trackingLock) { trackingCompletions.clear() }
        _liveActivity.value = LiveSyncActivity()
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

    // -------------------------------------------------------------------------
    // Sync logic
    // -------------------------------------------------------------------------

    private suspend fun runSync(
        startRelayIndex: Int,
        initialEventsSent: Int,
    ) {
        val startTime = System.currentTimeMillis()
        val myPubKey = account.signer.pubKey

        val allRelays = listOf("wss://relay.damus.io".normalizeRelayUrl())

        /*
                    account.cache.relayHints.relayDB
                .keys()
                .toList()
         */

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

        // Publish destination relays so the UI can show them before events arrive.
        liveOutboxTargets = outboxTargets
        liveInboxTargets = inboxTargets
        liveDmTargets = dmTargets
        emitLiveSnapshot()

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

        // Thread-safe dedup sets and counters — relay workers run concurrently.
        val outboxSent = ConcurrentHashMap.newKeySet<String>()
        val inboxSent = ConcurrentHashMap.newKeySet<String>()
        val dmSent = ConcurrentHashMap.newKeySet<String>()
        val totalSent = AtomicLong(initialEventsSent.toLong())

        // OK (true) tracking: maps each sent event ID to its source relay.
        // The first OK true for an event atomically removes it from this map,
        // crediting the acceptance to the source relay and preventing double-counting.
        val sourceRelayOfEvent = ConcurrentHashMap<HexKey, NormalizedRelayUrl>()
        val acceptedCountPerRelay = ConcurrentHashMap<NormalizedRelayUrl, AtomicInteger>()
        val totalAccepted = AtomicLong(0)

        val okListener =
            object : IRelayClientListener {
                override fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    if (msg is OkMessage && msg.success) {
                        // remove() is atomic: returns non-null only for the first OK per event.
                        val sourceRelay = sourceRelayOfEvent.remove(msg.eventId) ?: return
                        acceptedCountPerRelay.getOrPut(sourceRelay) { AtomicInteger(0) }.incrementAndGet()
                        totalAccepted.incrementAndGet()
                    }
                }
            }

        val relaysCompleted = AtomicInteger(startRelayIndex)

        _syncState.value =
            SyncState.Running(
                relaysCompleted = relaysCompleted.get(),
                totalRelays = totalRelays,
                eventsSent = totalSent.get().toInt(),
            )

        account.client.subscribe(okListener)
        try {
            downloadFromPool(
                relays = relaysToProcess,
                baseFilters = baseFilters,
                onEvent = { event, sourceRelay ->
                    if (event.pubKey == myPubKey && outboxTargets.isNotEmpty()) {
                        if (outboxSent.add(event.id)) {
                            sourceRelayOfEvent[event.id] = sourceRelay
                            account.client.send(event, outboxTargets)
                            totalSent.incrementAndGet()
                        }
                    }
                    val pTagsMe = event.tags.isTaggedUser(myPubKey)
                    if (pTagsMe) {
                        if (event.kind == 4 || event.kind == 1059) {
                            if (dmTargets.isNotEmpty() && dmSent.add(event.id)) {
                                sourceRelayOfEvent[event.id] = sourceRelay
                                account.client.send(event, dmTargets)
                                totalSent.incrementAndGet()
                            }
                        } else {
                            if (inboxTargets.isNotEmpty() && inboxSent.add(event.id)) {
                                sourceRelayOfEvent[event.id] = sourceRelay
                                account.client.send(event, inboxTargets)
                                totalSent.incrementAndGet()
                            }
                        }
                    }
                },
                onRelayComplete = { relay, eventsFound ->
                    val eventsAccepted = acceptedCountPerRelay[relay]?.get() ?: 0
                    val info = LiveSyncActivity.CompletedRelayInfo(relay, eventsFound, eventsAccepted)
                    synchronized(trackingLock) {
                        trackingCompletions.addFirst(info)
                        while (trackingCompletions.size > MAX_ACTIVITY_LOG) trackingCompletions.removeLast()
                    }
                    val completed = relaysCompleted.incrementAndGet()
                    emitLiveSnapshot()
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
                    totalEventsAccepted = totalAccepted.get().toInt(),
                    durationMs = System.currentTimeMillis() - startTime,
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.message ?: "Unknown error")
        } finally {
            account.client.unsubscribe(okListener)
        }
    }

    /**
     * Maintains a sliding window of up to [MAX_CONCURRENT_RELAYS] active relay workers.
     * As soon as one relay finishes (all pages exhausted), the next relay from [relays]
     * starts immediately — no waiting for an entire batch to drain.
     *
     * [onEvent] receives the event and the URL of the relay it came from.
     */
    private suspend fun downloadFromPool(
        relays: List<NormalizedRelayUrl>,
        baseFilters: List<Filter>,
        onEvent: (Event, NormalizedRelayUrl) -> Unit,
        onRelayComplete: (NormalizedRelayUrl, Int) -> Unit,
    ) {
        val semaphore = Semaphore(MAX_CONCURRENT_RELAYS)
        supervisorScope {
            for (relay in relays) {
                if (!isActive) break
                semaphore.acquire()
                launch {
                    try {
                        val eventsFound = downloadFromRelay(relay, baseFilters) { event -> onEvent(event, relay) }
                        onRelayComplete(relay, eventsFound)
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
     *
     * @return total number of events received across all pages.
     */
    private suspend fun downloadFromRelay(
        relay: NormalizedRelayUrl,
        baseFilters: List<Filter>,
        onEvent: (Event) -> Unit,
    ): Int {
        var until: Long? = null
        var totalEvents = 0

        while (true) {
            coroutineContext.ensureActive()
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

            val count = pageCount.get()
            if (count == 0) break // relay exhausted or unreachable

            totalEvents += count

            // Advance cursor: next page starts just before the oldest event seen.
            until = pageMinTs.get() - 1
        }

        return totalEvents
    }
}
