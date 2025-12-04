/**
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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolCounts
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolEventOutbox
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolRequests
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayPool
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The NostrClient manages Nostr relay operations, subscriptions, and event delivery. It maintains:
 * - A RelayPool for managing connections to a collection of Nostr relays
 * - Active subscriptions tracking through PoolSubscriptionRepository
 * - An event outbox for managing unsent events and retry logic
 * - Automatic relay pool reconciliation based on subscription and event needs
 *
 * Core responsibilities include:
 * - Initializing and managing relay connections using WebSocket builders
 * - Coordinating subscription state across multiple relays
 * - Handling event and filter resending when relays disconnect and reconnect
 * - Aggregating relay status from the pool's state flow
 * - Maintaining listeners for propagating relay events and state changes
 *
 * Features:
 * - Reactive updating of relay sets based on subscription and outbox activity
 * - Relayer reconnection strategy that processes pending requests
 * - Filter comparison logic to detect significant subscription changes and avoid redundant requests
 * - Integration with RelayStats for tracking relay performance
 * - Thread-safe reconnection methods using coroutine flows
 *
 * The class combines flows from active subscriptions and the event outbox to ensure relays
 * are connected to all necessary endpoints. It also listens to relay state changes to update
 * subscriptions and message retries when connections are re-established.
 */
class NostrClient(
    private val websocketBuilder: WebsocketBuilder,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : INostrClient,
    IRelayClientListener {
    private val relayPool: RelayPool = RelayPool(websocketBuilder, this)

    private val activeRequests: PoolRequests = PoolRequests()
    private val activeCounts: PoolCounts = PoolCounts()
    private val eventOutbox: PoolEventOutbox = PoolEventOutbox()

    private var listeners = setOf<IRelayClientListener>()

    // controls the state of the client in such a way that if it is active
    // new filters will be sent to the relays and a potential reconnect can
    // be triggered.
    // Default: STARTS active
    private var isActive = true

    /**
     * Whatches for any changes in the relay list from subscriptions or outbox
     * and updates the relayPool as needed.
     */
    @OptIn(FlowPreview::class)
    private val allRelays =
        combine(
            activeRequests.desiredRelays,
            activeCounts.relays,
            eventOutbox.relays,
        ) { reqs, counts, outbox ->
            reqs + counts + outbox
        }.sample(300)
            .onEach {
                relayPool.updatePool(it)
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                activeRequests.desiredRelays.value + activeCounts.relays.value + eventOutbox.relays.value,
            )

    // Reconnects all relays that may have disconnected
    override fun connect() {
        isActive = true
        relayPool.connect()
    }

    override fun disconnect() {
        isActive = false
        relayPool.disconnect()
    }

    override fun isActive() = isActive

    class Reconnect(
        val onlyIfChanged: Boolean,
        val ignoreRetryDelays: Boolean,
    )

    val refreshConnection = MutableStateFlow(Reconnect(false, false))

    @OptIn(FlowPreview::class)
    val debouncingConnection =
        refreshConnection
            .debounce(200)
            .onEach {
                if (it.onlyIfChanged) {
                    relayPool.reconnectIfNeedsTo(it.ignoreRetryDelays)
                } else {
                    relayPool.disconnect()
                    relayPool.connect()
                }
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                false,
            )

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) {
        refreshConnection.tryEmit(Reconnect(onlyIfChanged, ignoreRetryDelays))
    }

    override fun openReqSubscription(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: IRequestListener?,
    ) {
        val relaysToUpdate = activeRequests.addOrUpdate(subId, filters, listener)

        if (isActive) {
            activeRequests.sendToRelayIfChanged(subId, relaysToUpdate) { relay, cmd ->
                if (cmd is CloseCmd || cmd is AuthCmd) {
                    relayPool.sendIfConnected(relay, cmd)
                } else {
                    relayPool.sendOrConnectAndSync(relay, cmd)
                }
            }

            // wakes up all the other relays
            // makes sure the relay wakes up if it was disconnected by the server
            // upon connection, the relay will run the default Sync and update all
            // filters, including this one.
            reconnect(true)
        }
    }

    override fun queryCount(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        val relaysToUpdate = activeCounts.addOrUpdate(subId, filters)

        if (isActive) {
            activeCounts.sendToRelayIfChanged(subId, relaysToUpdate) { relay, cmd ->
                if (cmd is CloseCmd || cmd is AuthCmd) {
                    relayPool.sendIfConnected(relay, cmd)
                } else {
                    relayPool.sendOrConnectAndSync(relay, cmd)
                }
            }

            // wakes up all the other relays
            reconnect(true)
        }
    }

    override fun send(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val relaysToUpdate = eventOutbox.markAsSending(event, relayList)

        if (isActive) {
            eventOutbox.sendToRelayIfChanged(event, relaysToUpdate, relayPool::sendOrConnectAndSync)

            // wakes up all the other relays
            reconnect(true)
        }
    }

    override fun close(subId: String) {
        val relaysToUpdateReqs = activeRequests.remove(subId)
        val relaysToUpdateCounts = activeCounts.remove(subId)

        if (isActive) {
            activeRequests.sendToRelayIfChanged(subId, relaysToUpdateReqs, relayPool::sendIfConnected)
            activeCounts.sendToRelayIfChanged(subId, relaysToUpdateCounts, relayPool::sendIfConnected)
        }
    }

    override fun renewFilters(relay: IRelayClient) {
        if (isActive) {
            scope.launch {
                activeRequests.syncState(relay.url, relay::sendOrConnectAndSync)
                activeCounts.syncState(relay.url, relay::sendOrConnectAndSync)
                eventOutbox.syncState(relay.url, relay::sendOrConnectAndSync)
            }
        }
    }

    /**
     * when a new connection starts, resets the state
     */
    override fun onConnecting(relay: IRelayClient) {
        activeRequests.onConnecting(relay.url)
        listeners.forEach { it.onConnecting(relay) }
    }

    /**
     * Relay just connected. Use this to send all
     * filters and events you need.
     */
    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        renewFilters(relay)
        listeners.forEach { it.onConnected(relay, pingMillis, compressed) }
    }

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        if (success) {
            activeRequests.onSent(relay.url, cmd)
            activeCounts.onSent(relay.url, cmd)
            eventOutbox.onSent(relay.url, cmd)
        }
        listeners.forEach { it.onSent(relay, cmdStr, cmd, success) }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        activeRequests.onIncomingMessage(relay, msg)
        activeCounts.onIncomingMessage(relay, msg)
        eventOutbox.onIncomingMessage(relay.url, msg)

        listeners.forEach { it.onIncomingMessage(relay, msgStr, msg) }
    }

    /**
     * Relay just diconnected.
     */
    override fun onDisconnected(relay: IRelayClient) {
        activeRequests.onDisconnected(relay.url)
        listeners.forEach { it.onDisconnected(relay) }
    }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        activeRequests.onCannotConnect(relay.url, errorMessage)
        activeCounts.onCannotConnect(relay.url, errorMessage)
        eventOutbox.onCannotConnect(relay.url, errorMessage)

        listeners.forEach { it.onCannotConnect(relay, errorMessage) }
    }

    override fun subscribe(listener: IRelayClientListener) {
        listeners = listeners.plus(listener)
    }

    fun isSubscribed(listener: IRelayClientListener): Boolean = listeners.contains(listener)

    override fun unsubscribe(listener: IRelayClientListener) {
        listeners = listeners.minus(listener)
    }

    fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = activeRequests.activeFiltersFor(url)

    fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = activeCounts.activeFiltersFor(url)

    fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = eventOutbox.activeOutboxCacheFor(url)

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = activeRequests.getSubscriptionFiltersOrNull(subId)

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = activeCounts.getSubscriptionFiltersOrNull(subId)

    override fun connectedRelaysFlow() = relayPool.connectedRelays

    override fun availableRelaysFlow() = relayPool.availableRelays
}
