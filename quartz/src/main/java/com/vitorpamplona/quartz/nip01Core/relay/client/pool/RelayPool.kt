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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.EmptyClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayState
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val UnsupportedRelayCreation: (url: NormalizedRelayUrl) -> IRelayClient = {
    throw UnsupportedOperationException("Cannot create new relays")
}

/**
 * RelayPool manages a collection of Nostr relays, abstracting individual connections and providing
 * unified methods for sending events, managing subscriptions, and tracking relay states.
 *
 * Key features:
 * - Maintains a cache of relays using LargeCache for efficient relay management
 * - Propagates event notifications to a shared listener across all relays
 * - Provides state tracking through RelayPoolStatus (connected/available relays)
 * - Supports relay lifecycle operations like connect/disconnect/reconnect
 * - Maintains an immutable createNewRelay function for custom relay creation
 *
 * Listens to relay events and:
 * 1. Forwards callbacks to parent listener
 * 2. Updates statusFlow when relay connectivity or events change
 * 3. Automatically reconnects relays that need reconnection
 *
 * Common use cases:
 * - Sending events to multiple relays simultaneously (send method)
 * - Managing subscriptions across the relay pool (sendRequest/closed methods)
 * - Maintaining optimal relay connections (updatePool/addRelay/removeRelay methods)
 */
class RelayPool(
    val listener: IRelayClientListener = EmptyClientListener,
    val createNewRelay: (url: NormalizedRelayUrl) -> IRelayClient = UnsupportedRelayCreation,
) : IRelayClientListener {
    private val relays = LargeCache<NormalizedRelayUrl, IRelayClient>()

    // Backing property to avoid flow emissions from other classes
    private val _statusFlow = MutableStateFlow<RelayPoolStatus>(RelayPoolStatus())
    val statusFlow: StateFlow<RelayPoolStatus> = _statusFlow.asStateFlow()

    fun getRelay(url: NormalizedRelayUrl): IRelayClient? = relays.get(url)

    fun getAll() = statusFlow.value.connected

    fun getAllNeedsToReconnect() = relays.filter { url, relay -> relay.needsToReconnect() }

    fun reconnectsRelaysThatNeedTo() {
        relays.forEach { url, relay ->
            if (relay.needsToReconnect()) {
                relay.disconnect()
                relay.connect()
            }
        }
    }

    fun connect() =
        relays.forEach { url, relay ->
            relay.connect()
        }

    fun connectIfDisconnected() =
        relays.forEach { url, relay ->
            relay.connectAndSyncFiltersIfDisconnected()
        }

    fun connectIfDisconnected(relay: NormalizedRelayUrl) = relays.get(relay)?.connectAndSyncFiltersIfDisconnected()

    fun disconnect() =
        relays.forEach { url, relay ->
            relay.disconnect()
        }

    fun sendRequest(
        relay: NormalizedRelayUrl,
        subId: String,
        filters: List<Filter>,
    ) {
        relays.get(relay)?.sendRequest(subId, filters)
    }

    fun sendRequest(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        relays.forEach { url, relay ->
            val filters = filters[relay.url]
            if (!filters.isNullOrEmpty()) {
                relay.sendRequest(subId, filters)
            }
        }
    }

    fun sendCount(
        relay: NormalizedRelayUrl,
        subId: String,
        filters: List<Filter>,
    ) {
        relays.get(relay)?.sendCount(subId, filters)
    }

    fun sendCount(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        relays.forEach { url, relay ->
            val filters = filters[relay.url]
            if (!filters.isNullOrEmpty()) {
                relay.sendCount(subId, filters)
            }
        }
    }

    fun close(subscriptionId: String) =
        relays.forEach { url, relay ->
            relay.close(subscriptionId)
        }

    fun close(
        relay: NormalizedRelayUrl,
        subscriptionId: String,
    ) = relays.get(relay)?.close(subscriptionId)

    fun send(
        signedEvent: Event,
        list: Set<NormalizedRelayUrl>,
    ) {
        list.forEach {
            getOrCreateRelay(it).send(signedEvent)
        }
    }

    // --------------------
    // Pool Maintenance
    // --------------------
    fun getOrCreateRelay(relay: NormalizedRelayUrl) = relays.getOrCreate(relay, createNewRelay)

    fun createRelayIfAbsent(relay: NormalizedRelayUrl): Boolean = relays.createIfAbsent(relay, createNewRelay)

    /**
     * Updates the pool of relays without disconnecting the existing ones.
     */
    fun updatePool(newRelays: Set<NormalizedRelayUrl>) {
        val toRemove = relays.keys() - newRelays
        var atLeastOne = false

        newRelays.forEach {
            if (createRelayIfAbsent(it)) {
                atLeastOne = true
            }
        }

        toRemove.forEach {
            if (removeRelayInner(it)) {
                atLeastOne = true
            }
        }

        if (atLeastOne) {
            updateStatus()
        }
    }

    fun addRelay(relay: NormalizedRelayUrl): IRelayClient {
        if (createRelayIfAbsent(relay)) {
            updateStatus()
        }
        return getOrCreateRelay(relay)
    }

    fun addAllRelays(relayList: List<NormalizedRelayUrl>) {
        var atLeastOne = false
        relayList.forEach {
            if (createRelayIfAbsent(it)) {
                atLeastOne = true
            }
        }
        if (atLeastOne) {
            updateStatus()
        }
    }

    private fun removeRelayInner(relay: NormalizedRelayUrl): Boolean {
        val relayInPool = relays.remove(relay)
        if (relayInPool != null) {
            relayInPool.disconnect()
            return true
        }
        return false
    }

    fun removeRelay(relay: NormalizedRelayUrl) {
        if (removeRelayInner(relay)) {
            updateStatus()
        }
    }

    fun removeAllRelays() {
        if (relays.size() > 0) {
            disconnect()
            relays.clear()
            updateStatus()
        }
    }

    // --------------------
    // Listener Interceptor
    // --------------------

    override fun onEvent(
        relay: IRelayClient,
        subId: String,
        event: Event,
        arrivalTime: Long,
        afterEOSE: Boolean,
    ) {
        listener.onEvent(relay, subId, event, arrivalTime, afterEOSE)
    }

    override fun onError(
        relay: IRelayClient,
        subId: String,
        error: Error,
    ) {
        listener.onError(relay, subId, error)
        updateStatus()
    }

    override fun onEOSE(
        relay: IRelayClient,
        subId: String,
        arrivalTime: Long,
    ) {
        listener.onEOSE(relay, subId, arrivalTime)
    }

    override fun onRelayStateChange(
        relay: IRelayClient,
        type: RelayState,
    ) {
        listener.onRelayStateChange(relay, type)
        updateStatus()
    }

    override fun onSendResponse(
        relay: IRelayClient,
        eventId: String,
        success: Boolean,
        message: String,
    ) = listener.onSendResponse(relay, eventId, success, message)

    override fun onAuth(
        relay: IRelayClient,
        challenge: String,
    ) = listener.onAuth(relay, challenge)

    override fun onNotify(
        relay: IRelayClient,
        description: String,
    ) = listener.onNotify(relay, description)

    override fun onClosed(
        relay: IRelayClient,
        subId: String,
        message: String,
    ) = listener.onClosed(relay, subId, message)

    override fun onSend(
        relay: IRelayClient,
        msg: String,
        success: Boolean,
    ) = listener.onSend(relay, msg, success)

    override fun onBeforeSend(
        relay: IRelayClient,
        event: Event,
    ) = listener.onBeforeSend(relay, event)

    // ---------------
    // STATUS Reports
    // ---------------

    fun availableRelays(): Set<NormalizedRelayUrl> = relays.keys()

    fun connectedRelays(): Set<NormalizedRelayUrl> =
        relays.mapNotNullIntoSet { url, relay ->
            if (relay.isConnected()) {
                url
            } else {
                null
            }
        }

    private fun updateStatus() {
        val connected = connectedRelays()
        val available = availableRelays()
        if (_statusFlow.value.connected != connected || _statusFlow.value.available != available) {
            _statusFlow.tryEmit(RelayPoolStatus(connected, available))
        }
    }

    @Immutable
    data class RelayPoolStatus(
        val connected: Set<NormalizedRelayUrl> = emptySet(),
        val available: Set<NormalizedRelayUrl> = emptySet(),
        val isConnected: Boolean = connected.isNotEmpty(),
    )
}
