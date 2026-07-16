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
package com.vitorpamplona.quartz.nip01Core.relay.client.limits

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.LimitsMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Caches the latest `LIMITS` (relay rights + limits) advertised by each relay.
 *
 * A relay sends `LIMITS` on connect and again whenever the connection's rights
 * change (e.g. after a successful NIP-42 AUTH flips `can_write` on). Like
 * [com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator],
 * this is an accessory that registers a [RelayConnectionListener], keeps
 * per-relay state, and publishes it as a Compose-stable [StateFlow] — but it is
 * purely passive: it never replies to the relay.
 *
 * The cache is connection-scoped: a relay's entry is dropped on disconnect, so a
 * stale limit from a previous session never leaks into a new one. A fresh
 * connection re-advertises `LIMITS` on connect.
 */
class RelayLimits(
    val client: INostrClient,
) {
    // onIncomingMessage / onDisconnected fire on the per-relay socket dispatcher
    // thread, so this is mutated concurrently. MutableStateFlow.update is an
    // atomic compare-and-set loop over an immutable PersistentMap, so concurrent
    // writers from different relays never corrupt the map or lose an update.
    private val _limitsFlow = MutableStateFlow<PersistentMap<NormalizedRelayUrl, LimitsMessage>>(persistentMapOf())

    /**
     * Per-relay `LIMITS` as an immutable, Compose-stable snapshot map. The map
     * identity changes on every mutation, so downstream
     * [kotlinx.coroutines.flow.distinctUntilChanged] and Compose `@Immutable`
     * skipping both work correctly.
     */
    val limitsFlow: StateFlow<PersistentMap<NormalizedRelayUrl, LimitsMessage>> = _limitsFlow.asStateFlow()

    /** The most recent `LIMITS` the relay advertised, or null if none seen on the current connection. */
    fun get(url: NormalizedRelayUrl): LimitsMessage? = _limitsFlow.value[url]

    fun snapshot(): Map<NormalizedRelayUrl, LimitsMessage> = _limitsFlow.value

    private val clientListener =
        object : RelayConnectionListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is LimitsMessage) {
                    _limitsFlow.update { it.putting(relay.url, msg) }
                }
            }

            override fun onDisconnected(relay: IRelayClient) {
                _limitsFlow.update { it.removing(relay.url) }
            }
        }

    init {
        Log.d("RelayLimits", "Init, Subscribe")
        client.addConnectionListener(clientListener)
    }

    fun destroy() {
        // makes sure to run
        Log.d("RelayLimits", "Destroy, Unsubscribe")
        client.removeConnectionListener(clientListener)
    }
}
