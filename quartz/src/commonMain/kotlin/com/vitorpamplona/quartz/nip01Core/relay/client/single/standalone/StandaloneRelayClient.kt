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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.standalone

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.EmptyClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.IRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RedirectRelayClientListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * This relay client saves any event that will be sent in an outbox
 * waits for auth and sends it again to make sure it is delivered.
 */
class StandaloneRelayClient(
    url: NormalizedRelayUrl,
    socketBuilder: WebsocketBuilder,
    listener: IRelayClientListener = EmptyClientListener,
) {
    private val outbox = LargeCache<HexKey, Event>()
    private val reqs = LargeCache<String, List<Filter>>()
    private val counts = LargeCache<String, List<Filter>>()

    val client =
        BasicRelayClient(
            url,
            socketBuilder,
            object : RedirectRelayClientListener(listener) {
                override fun onConnected(
                    relay: IRelayClient,
                    pingMillis: Int,
                    compressed: Boolean,
                ) {
                    super.onConnected(relay, pingMillis, compressed)
                    renewFilters()
                }

                override fun onIncomingMessage(
                    relay: IRelayClient,
                    msgStr: String,
                    msg: Message,
                ) {
                    if (msg is OkMessage) {
                        // remove from cache for any error that is not an auth required error.
                        // for auth required, we will do the auth and try to send again.
                        if (outbox.containsKey(msg.eventId) && !msg.message.startsWith("auth-required")) {
                            outbox.remove(msg.eventId)
                        }
                    }
                    super.onIncomingMessage(relay, msgStr, msg)
                }
            },
        )

    fun renewFilters() {
        outbox.forEach { id, event ->
            client.sendOrConnectAndSync(EventCmd(event))
        }
        reqs.forEach { subId, filters ->
            client.sendOrConnectAndSync(ReqCmd(subId, filters))
        }
        counts.forEach { subId, filters ->
            client.sendOrConnectAndSync(CountCmd(subId, filters))
        }
    }

    fun connect() = client.connect()

    fun needsToReconnect() = client.needsToReconnect()

    fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = client.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays)

    fun isConnected() = client.isConnected()

    fun sendRequest(
        subId: String,
        filters: List<Filter>,
    ) {
        reqs.put(subId, filters)
        client.sendOrConnectAndSync(ReqCmd(subId, filters))
    }

    fun sendCount(
        queryId: String,
        filters: List<Filter>,
    ) {
        counts.put(queryId, filters)
        client.sendOrConnectAndSync(CountCmd(queryId, filters))
    }

    fun send(event: Event) {
        if (event !is RelayAuthEvent) {
            outbox.put(event.id, event)
        }
        client.sendOrConnectAndSync(EventCmd(event))
    }

    fun close(subscriptionId: String) {
        reqs.remove(subscriptionId)
        counts.remove(subscriptionId)
        client.sendIfConnected(CloseCmd(subscriptionId))
    }

    fun disconnect() = client.disconnect()
}
