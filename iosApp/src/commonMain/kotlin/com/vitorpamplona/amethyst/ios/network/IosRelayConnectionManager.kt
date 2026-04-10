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
package com.vitorpamplona.amethyst.ios.network

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connection state of a relay (more granular than just connected/disconnected).
 */
enum class RelayConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/**
 * Represents the connection status and permissions of a Nostr relay.
 */
data class RelayStatus(
    val url: NormalizedRelayUrl,
    val connected: Boolean,
    val connectionState: RelayConnectionState = if (connected) RelayConnectionState.CONNECTED else RelayConnectionState.DISCONNECTED,
    val pingMs: Int? = null,
    val compressed: Boolean = false,
    val error: String? = null,
    val read: Boolean = true,
    val write: Boolean = true,
    val nip11: Nip11RelayInformation? = null,
)

/**
 * Default relay URLs for Nostr connectivity.
 */
object DefaultRelays {
    val RELAYS =
        listOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol",
            "wss://relay.snort.social",
            "wss://nostr.wine",
            "wss://relay.primal.net",
        )

    /** Extended list for relay discovery / recommendations. */
    val RECOMMENDED =
        listOf(
            "wss://relay.damus.io",
            "wss://relay.nostr.band",
            "wss://nos.lol",
            "wss://relay.snort.social",
            "wss://nostr.wine",
            "wss://relay.primal.net",
            "wss://purplepag.es",
            "wss://relay.nostr.bg",
            "wss://nostr.mom",
            "wss://relay.mostr.pub",
        )
}

/**
 * iOS relay connection manager using NSURLSession websockets.
 * Mirrors the desktop RelayConnectionManager pattern with added
 * per-relay permissions, NIP-11 info caching and individual reconnect.
 */
class IosRelayConnectionManager : RelayConnectionListener {
    private val _client = NostrClient(IosWebSocket.Builder())

    val client: INostrClient get() = _client

    private val _relayStatuses = MutableStateFlow<Map<NormalizedRelayUrl, RelayStatus>>(emptyMap())
    val relayStatuses: StateFlow<Map<NormalizedRelayUrl, RelayStatus>> = _relayStatuses.asStateFlow()

    val connectedRelays: StateFlow<Set<NormalizedRelayUrl>> = _client.connectedRelaysFlow()
    val availableRelays: StateFlow<Set<NormalizedRelayUrl>> = _client.availableRelaysFlow()

    /** Cached NIP-11 information per relay URL. */
    private val _nip11Cache = MutableStateFlow<Map<NormalizedRelayUrl, Nip11RelayInformation>>(emptyMap())
    val nip11Cache: StateFlow<Map<NormalizedRelayUrl, Nip11RelayInformation>> = _nip11Cache.asStateFlow()

    init {
        _client.addConnectionListener(this)
    }

    fun connect() {
        _client.connect()
    }

    fun disconnect() {
        _client.disconnect()
    }

    /**
     * Reconnect all relays.
     */
    fun reconnectAll() {
        _relayStatuses.value.keys.forEach { url ->
            updateRelayStatus(url) { it.copy(connectionState = RelayConnectionState.CONNECTING, error = null) }
        }
        _client.reconnect()
    }

    fun addRelay(url: String): NormalizedRelayUrl? {
        val normalized = RelayUrlNormalizer.Companion.normalizeOrNull(url) ?: return null
        updateRelayStatus(normalized) { it.copy(connected = false, connectionState = RelayConnectionState.DISCONNECTED, error = null) }
        return normalized
    }

    /**
     * Add relay with explicit read/write permissions.
     */
    fun addRelay(
        url: String,
        read: Boolean,
        write: Boolean,
    ): NormalizedRelayUrl? {
        val normalized = RelayUrlNormalizer.Companion.normalizeOrNull(url) ?: return null
        updateRelayStatus(normalized) {
            it.copy(connected = false, connectionState = RelayConnectionState.DISCONNECTED, error = null, read = read, write = write)
        }
        return normalized
    }

    fun removeRelay(url: NormalizedRelayUrl) {
        _relayStatuses.value = _relayStatuses.value - url
    }

    fun addDefaultRelays() {
        DefaultRelays.RELAYS.forEach { addRelay(it) }
    }

    /**
     * Toggle read permission for a relay.
     */
    fun setRelayRead(
        url: NormalizedRelayUrl,
        read: Boolean,
    ) {
        updateRelayStatus(url) { it.copy(read = read) }
    }

    /**
     * Toggle write permission for a relay.
     */
    fun setRelayWrite(
        url: NormalizedRelayUrl,
        write: Boolean,
    ) {
        updateRelayStatus(url) { it.copy(write = write) }
    }

    /**
     * Store NIP-11 information for a relay.
     */
    fun cacheNip11(
        url: NormalizedRelayUrl,
        info: Nip11RelayInformation,
    ) {
        _nip11Cache.value = _nip11Cache.value + (url to info)
        updateRelayStatus(url) { it.copy(nip11 = info) }
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

    fun broadcastToAll(event: Event) {
        val connected = connectedRelays.value
        publish(event, connected)
    }

    /** Returns only the relays that have write enabled. */
    fun writeRelays(): Set<NormalizedRelayUrl> =
        _relayStatuses.value.entries
            .filter { it.value.write && it.value.connected }
            .map { it.key }
            .toSet()

    /** Returns only the relays that have read enabled. */
    fun readRelays(): Set<NormalizedRelayUrl> =
        _relayStatuses.value.entries
            .filter { it.value.read && it.value.connected }
            .map { it.key }
            .toSet()

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
        updateRelayStatus(relay.url) { it.copy(connected = false, connectionState = RelayConnectionState.CONNECTING, error = null) }
    }

    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        updateRelayStatus(relay.url) {
            it.copy(
                connected = true,
                connectionState = RelayConnectionState.CONNECTED,
                pingMs = pingMillis,
                compressed = compressed,
                error = null,
            )
        }
    }

    override fun onDisconnected(relay: IRelayClient) {
        updateRelayStatus(relay.url) { it.copy(connected = false, connectionState = RelayConnectionState.DISCONNECTED) }
    }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        updateRelayStatus(relay.url) { it.copy(connected = false, connectionState = RelayConnectionState.DISCONNECTED, error = errorMessage) }
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        // Events handled by subscription listeners
    }

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        // No-op
    }
}
