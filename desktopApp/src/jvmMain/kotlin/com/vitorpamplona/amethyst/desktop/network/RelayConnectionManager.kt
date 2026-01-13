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
package com.vitorpamplona.amethyst.desktop.network

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Nostr relay connections, subscriptions, and status tracking.
 * Can be used by both Android and Desktop apps.
 *
 * @param websocketBuilder Platform-specific websocket builder (e.g., OkHttp-based)
 */
open class RelayConnectionManager(
    websocketBuilder: WebsocketBuilder,
) : IRelayClientListener {
    private val client = NostrClient(websocketBuilder)

    private val _relayStatuses = MutableStateFlow<Map<NormalizedRelayUrl, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<NormalizedRelayUrl, RelayStatus>> = _relayStatuses.asStateFlow()

    val connectedRelays: StateFlow<Set<NormalizedRelayUrl>> = client.connectedRelaysFlow()
    val availableRelays: StateFlow<Set<NormalizedRelayUrl>> = client.availableRelaysFlow()

    init {
        client.subscribe(this)
    }

    fun connect() {
        client.connect()
    }

    fun disconnect() {
        client.disconnect()
    }

    fun addRelay(url: String): NormalizedRelayUrl? {
        val normalized = RelayUrlNormalizer.Companion.normalizeOrNull(url) ?: return null
        updateRelayStatus(normalized) { it.copy(connected = false, error = null) }
        return normalized
    }

    fun removeRelay(url: NormalizedRelayUrl) {
        _relayStatuses.value = _relayStatuses.value - url
    }

    fun addDefaultRelays() {
        DefaultRelays.RELAYS.forEach { addRelay(it) }
    }

    fun subscribe(
        subId: String,
        filters: List<Filter>,
        relays: Set<NormalizedRelayUrl> = availableRelays.value,
        listener: IRequestListener? = null,
    ) {
        val filterMap = relays.associateWith { filters }
        client.openReqSubscription(subId, filterMap, listener)
    }

    fun unsubscribe(subId: String) {
        client.close(subId)
    }

    fun send(
        event: Event,
        relays: Set<NormalizedRelayUrl> = connectedRelays.value,
    ) {
        client.send(event, relays)
    }

    /**
     * Broadcasts an event to all connected relays.
     */
    fun broadcastToAll(event: Event) {
        val connected = connectedRelays.value
        send(event, connected)
    }

    private fun updateRelayStatus(
        url: NormalizedRelayUrl,
        update: (RelayStatus) -> RelayStatus,
    ) {
        _relayStatuses.value =
            _relayStatuses.value.toMutableMap().apply {
                val current = this[url] ?: RelayStatus(url, connected = false)
                this[url] = update(current)
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
        // Events are handled by subscription listeners
    }

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        // Command send tracking
    }
}
