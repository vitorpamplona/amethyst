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

/**
 * Syncs the user's events across all known relays:
 *  1. Downloads all events authored by the user and sends them to their outbox relays.
 *  2. Downloads all events that p-tag the user and sends them to their inbox relays.
 *  3. Downloads kind-4 and kind-1059 events that p-tag the user and sends them to DM relays.
 *
 * The procedure iterates over every relay URL in LocalCache.relayHints.relayDB.
 * Events are deduplicated per destination to avoid redundant sends.
 *
 * Scoped to the AccountViewModel so the sync continues in the background while
 * the user navigates to other screens or away from the app.
 */
class EventSyncViewModel(
    val account: Account,
    private val scope: CoroutineScope,
) {
    sealed class SyncState {
        object Idle : SyncState()

        data class Running(
            val phase: Int,
            val phaseTotal: Int,
            val currentRelay: String,
            val relayIndex: Int,
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
                runSync()
            }
    }

    fun cancel() {
        syncJob?.cancel()
        _syncState.value = SyncState.Idle
    }

    private suspend fun runSync() {
        val startTime = System.currentTimeMillis()

        val myPubKey = account.signer.pubKey
        val allRelays =
            account.cache.relayHints.relayDB
                .keys()
                .toList()

        if (allRelays.isEmpty()) {
            _syncState.value = SyncState.Error("No known relays found. Browse some content first to discover relays.")
            return
        }

        val outboxTargets = account.outboxRelays.flow.value
        val inboxTargets = account.nip65RelayList.inboxFlow.value
        val dmTargets = account.dmRelays.flow.value

        // Deduplication sets — one per destination category
        val outboxSent = HashSet<String>(1024)
        val inboxSent = HashSet<String>(1024)
        val dmSent = HashSet<String>(1024)

        var totalSent = 0
        val totalPhases = 3

        try {
            // -------------------------
            // Phase 1: Author's events → Outbox relays
            // -------------------------
            if (outboxTargets.isNotEmpty()) {
                allRelays.forEachIndexed { index, relay ->
                    if (!isActive) return

                    _syncState.value =
                        SyncState.Running(
                            phase = 1,
                            phaseTotal = totalPhases,
                            currentRelay = relay.url,
                            relayIndex = index + 1,
                            totalRelays = allRelays.size,
                            eventsSent = totalSent,
                        )

                    downloadFromRelay(
                        relay = relay,
                        filter = Filter(authors = listOf(myPubKey)),
                    ) { event ->
                        if (outboxSent.add(event.id)) {
                            account.client.send(event, outboxTargets)
                            totalSent++
                        }
                    }
                }
            }

            // -------------------------
            // Phase 2: P-tagged (non-DM) events → Inbox relays
            // -------------------------
            if (inboxTargets.isNotEmpty()) {
                allRelays.forEachIndexed { index, relay ->
                    if (!isActive) return

                    _syncState.value =
                        SyncState.Running(
                            phase = 2,
                            phaseTotal = totalPhases,
                            currentRelay = relay.url,
                            relayIndex = index + 1,
                            totalRelays = allRelays.size,
                            eventsSent = totalSent,
                        )

                    downloadFromRelay(
                        relay = relay,
                        filter = Filter(tags = mapOf("p" to listOf(myPubKey))),
                    ) { event ->
                        // Skip DM kinds — handled in phase 3
                        if (event.kind != 4 && event.kind != 1059) {
                            if (inboxSent.add(event.id)) {
                                account.client.send(event, inboxTargets)
                                totalSent++
                            }
                        }
                    }
                }
            }

            // -------------------------
            // Phase 3: DM events (kind 4 & 1059) → DM relays
            // -------------------------
            if (dmTargets.isNotEmpty()) {
                allRelays.forEachIndexed { index, relay ->
                    if (!isActive) return

                    _syncState.value =
                        SyncState.Running(
                            phase = 3,
                            phaseTotal = totalPhases,
                            currentRelay = relay.url,
                            relayIndex = index + 1,
                            totalRelays = allRelays.size,
                            eventsSent = totalSent,
                        )

                    downloadFromRelay(
                        relay = relay,
                        filter = Filter(kinds = listOf(4, 1059), tags = mapOf("p" to listOf(myPubKey))),
                    ) { event ->
                        if (dmSent.add(event.id)) {
                            account.client.send(event, dmTargets)
                            totalSent++
                        }
                    }
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
     * Opens a REQ subscription on [relay] with [filter], delivers every event to [onEvent],
     * and suspends until the relay sends EOSE (or 90 seconds pass, whichever comes first).
     */
    private suspend fun downloadFromRelay(
        relay: NormalizedRelayUrl,
        filter: Filter,
        onEvent: (Event) -> Unit,
    ) {
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

        account.client.openReqSubscription(subId, mapOf(relay to listOf(filter)), listener)

        withTimeoutOrNull(90_000L) {
            done.receive()
        }

        account.client.close(subId)
        done.close()
    }
}
