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

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.EmptyClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val websocketBuilder: WebsocketBuilder,
    val listener: IRelayClientListener = EmptyClientListener,
) : IRelayClientListener {
    private val relays = LargeCache<NormalizedRelayUrl, IRelayClient>()

    private val _connectedRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val connectedRelays = _connectedRelays.asStateFlow()

    private val _availableRelays = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
    val availableRelays = _availableRelays.asStateFlow()

    fun getRelay(url: NormalizedRelayUrl): IRelayClient? = relays.get(url)

    private fun createNewRelay(url: NormalizedRelayUrl) =
        BasicRelayClient(
            url = url,
            socketBuilder = websocketBuilder,
            listener = this,
        )

    fun reconnectIfNeedsTo(ignoreRetryDelays: Boolean = false) {
        relays.forEach { url, relay ->
            if (relay.isConnected()) {
                if (relay.needsToReconnect()) {
                    // network has changed, force reconnect
                    relay.disconnect()
                    relay.connect()
                }
            } else {
                // relay is not connected. Connect if it is time
                relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays)
            }
        }
    }

    fun connect() {
        relays.forEach { url, relay ->
            relay.connect()
        }
    }

    fun connectIfDisconnected() {
        relays.forEach { url, relay ->
            relay.connectAndSyncFiltersIfDisconnected()
        }
    }

    fun connectIfDisconnected(relay: NormalizedRelayUrl) = relays.get(relay)?.connectAndSyncFiltersIfDisconnected()

    fun disconnect() {
        relays.forEach { url, relay ->
            relay.disconnect()
        }
    }

    fun sendOrConnectAndSync(
        relay: NormalizedRelayUrl,
        cmd: Command,
    ) = getOrCreateRelay(relay).sendOrConnectAndSync(cmd)

    fun sendIfConnected(
        relay: NormalizedRelayUrl,
        cmd: Command,
    ) = getOrCreateRelay(relay).sendIfConnected(cmd)

    fun sendOrConnectAndSync(
        list: Set<NormalizedRelayUrl>,
        cmd: Command,
    ) {
        list.forEach {
            getOrCreateRelay(it).sendOrConnectAndSync(cmd)
        }
    }

    fun sendIfConnected(
        list: Set<NormalizedRelayUrl>,
        cmd: Command,
    ) {
        list.forEach {
            getOrCreateRelay(it).sendIfConnected(cmd)
        }
    }

    // --------------------
    // Pool Maintenance
    // --------------------
    fun getOrCreateRelay(relay: NormalizedRelayUrl): IRelayClient {
        val r = relays.getOrCreate(relay, ::createNewRelay)
        if (_availableRelays.value.size != relays.size()) {
            _availableRelays.update { relays.keys() }
        }
        return r
    }

    fun createRelayIfAbsent(relay: NormalizedRelayUrl): Boolean = relays.createIfAbsent(relay, ::createNewRelay)

    /**
     * Updates the pool of relays without disconnecting the existing ones.
     */
    fun updatePool(newRelays: Set<NormalizedRelayUrl>) {
        val toRemove = relays.keys() - newRelays
        var atLeastOne = false

        newRelays.forEach { relay ->
            if (createRelayIfAbsent(relay)) {
                atLeastOne = true
            }
        }

        toRemove.forEach { relay ->
            if (removeRelayInner(relay)) {
                atLeastOne = true
            }
        }

        if (atLeastOne) {
            _availableRelays.update { relays.keys() }
        }
    }

    fun addRelay(relay: NormalizedRelayUrl): IRelayClient {
        if (createRelayIfAbsent(relay)) {
            _availableRelays.update { relays.keys() }
        }
        return getOrCreateRelay(relay)
    }

    fun addAllRelays(relayList: List<NormalizedRelayUrl>) {
        var atLeastOne = false
        relayList.forEach { relay ->
            if (createRelayIfAbsent(relay)) {
                atLeastOne = true
            }
        }

        if (atLeastOne) {
            _availableRelays.update { relays.keys() }
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
            _availableRelays.update { relays.keys() }
        }
    }

    fun removeAllRelays() {
        if (relays.size() > 0) {
            disconnect()
            relays.clear()
            _availableRelays.update { emptySet() }
        }
    }

    // --------------------
    // Listener Interceptor
    // --------------------
    override fun onConnecting(relay: IRelayClient) = listener.onConnecting(relay)

    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        _connectedRelays.update { it + relay.url }
        listener.onConnected(relay, pingMillis, compressed)
    }

    override fun onDisconnected(relay: IRelayClient) {
        _connectedRelays.update { it - relay.url }
        listener.onDisconnected(relay)
    }

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) = listener.onIncomingMessage(relay, msgStr, msg)

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) = listener.onCannotConnect(relay, errorMessage)

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) = listener.onSent(relay, cmdStr, cmd, success)

    fun connectedRelaysCount(): Int = relays.count { url, relay -> relay.isConnected() }
}
