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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class RelayMetrics(
    val eventCount: Long = 0,
    val lastEventAt: Long? = null,
)

/**
 * Manages Nostr relay connections, subscriptions, and status tracking.
 * Can be used by both Android and Desktop apps.
 *
 * @param websocketBuilder Platform-specific websocket builder (e.g., OkHttp-based)
 */
open class RelayConnectionManager(
    websocketBuilder: WebsocketBuilder,
) : RelayConnectionListener {
    private val _client = NostrClient(websocketBuilder)

    /** Exposes the underlying INostrClient for subscription coordinators */
    val client: com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient get() = _client

    private val _relayStatuses = MutableStateFlow<Map<NormalizedRelayUrl, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<NormalizedRelayUrl, RelayStatus>> = _relayStatuses.asStateFlow()

    val connectedRelays: StateFlow<Set<NormalizedRelayUrl>> = _client.connectedRelaysFlow()
    val availableRelays: StateFlow<Set<NormalizedRelayUrl>> = _client.availableRelaysFlow()

    // Relays explicitly removed by user — suppress status updates from pool callbacks
    private val removedRelays = ConcurrentHashMap.newKeySet<NormalizedRelayUrl>()

    // Hot metrics — written on every event, no StateFlow emission
    private val rawMetricsMap = ConcurrentHashMap<NormalizedRelayUrl, RelayMetrics>()

    // Throttled snapshot — 1Hz emission for UI
    private val _relayMetrics = MutableStateFlow<Map<NormalizedRelayUrl, RelayMetrics>>(emptyMap())
    val relayMetrics: StateFlow<Map<NormalizedRelayUrl, RelayMetrics>> = _relayMetrics.asStateFlow()

    init {
        _client.addConnectionListener(this)
    }

    /** Start 1Hz metrics snapshot. Call once after connect(). */
    fun startMetricsSnapshot(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(1_000)
                _relayMetrics.value = HashMap(rawMetricsMap)
            }
        }
    }

    fun connect() {
        _client.connect()
    }

    fun disconnect() {
        _client.disconnect()
    }

    fun addRelay(url: String): NormalizedRelayUrl? {
        val normalized = RelayUrlNormalizer.Companion.normalizeOrNull(url) ?: return null
        removedRelays.remove(normalized)
        updateRelayStatus(normalized) { it.copy(connected = false, error = null) }
        return normalized
    }

    fun removeRelay(url: NormalizedRelayUrl) {
        removedRelays.add(url)
        _relayStatuses.update { it - url }
    }

    fun addDefaultRelays() {
        DefaultRelays.RELAYS.forEach { addRelay(it) }
    }

    fun subscribe(
        subId: String,
        filters: List<Filter>,
        relays: Set<NormalizedRelayUrl> = availableRelays.value,
        listener: SubscriptionListener? = null,
    ) {
        val filterMap = relays.associateWith { filters }
        _client.subscribe(subId, filterMap, listener)
    }

    fun unsubscribe(subId: String) {
        _client.unsubscribe(subId)
    }

    fun publish(
        event: Event,
        relays: Set<NormalizedRelayUrl> = connectedRelays.value,
    ) {
        _client.publish(event, relays)
    }

    /**
     * Broadcasts an event to all connected relays.
     */
    fun broadcastToAll(event: Event) {
        val connected = connectedRelays.value
        publish(event, connected)
    }

    /**
     * Sends an event to a specific relay (for NWC).
     * Adds the relay if not already in the list.
     */
    fun publishToRelay(
        relay: NormalizedRelayUrl,
        event: Event,
    ) {
        if (relay !in availableRelays.value) {
            updateRelayStatus(relay) { it.copy(connected = false, error = null) }
        }
        _client.publish(event, setOf(relay))
    }

    /**
     * Subscribes on a specific relay (for NWC).
     * Adds the relay if not already in the list.
     */
    fun subscribeOnRelay(
        relay: NormalizedRelayUrl,
        subId: String,
        filters: List<Filter>,
        onEvent: (Event, NormalizedRelayUrl) -> Unit,
    ) {
        if (relay !in availableRelays.value) {
            updateRelayStatus(relay) { it.copy(connected = false, error = null) }
        }
        val filterMap = mapOf(relay to filters)
        _client.subscribe(
            subId = subId,
            filters = filterMap,
            listener =
                object : SubscriptionListener {
                    override fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        onEvent(event, relay)
                    }
                },
        )
    }

    /**
     * Closes a subscription on a specific relay.
     */
    fun closeSubscription(
        relay: NormalizedRelayUrl,
        subId: String,
    ) {
        _client.unsubscribe(subId)
    }

    private fun updateRelayStatus(
        url: NormalizedRelayUrl,
        transform: (RelayStatus) -> RelayStatus,
    ) {
        if (url in removedRelays) return
        _relayStatuses.update { current ->
            val existing = current[url] ?: RelayStatus(url, connected = false)
            current + (url to transform(existing))
        }
    }

    override fun onConnecting(relay: IRelayClient) {
        updateRelayStatus(relay.url) {
            it.copy(connected = false, error = null)
        }
    }

    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        updateRelayStatus(relay.url) {
            it.copy(connected = true, pingMs = pingMillis, compressed = compressed, error = null)
        }
    }

    override fun onDisconnected(relay: IRelayClient) {
        updateRelayStatus(relay.url) {
            it.copy(connected = false)
        }
    }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        updateRelayStatus(relay.url) {
            it.copy(connected = false, error = errorMessage)
        }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        // Only count EVENT messages, not EOSE/OK/NOTICE/AUTH
        if (msg !is EventMessage) return
        rawMetricsMap.compute(relay.url) { _, m ->
            RelayMetrics(
                eventCount = (m?.eventCount ?: 0) + 1,
                lastEventAt = System.currentTimeMillis(),
            )
        }
    }

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        // Command send tracking — no-op for now
    }
}
