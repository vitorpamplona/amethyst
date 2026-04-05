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
package com.vitorpamplona.amethyst.cli.engine

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CliRelayPool(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    private val connections = ConcurrentHashMap<String, RelayConnection>()
    val incomingEvents = Channel<Pair<String, String>>(Channel.UNLIMITED)
    private val subscriptionCounter = AtomicInteger(0)

    fun connect(relayUrls: List<String>) {
        for (url in relayUrls) {
            if (connections.containsKey(url)) continue
            val conn = RelayConnection(url)
            connections[url] = conn
            conn.connect()
        }
    }

    fun disconnect() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }

    fun send(event: Event) {
        val json = """["EVENT",${event.toJson()}]"""
        connections.values.forEach { conn ->
            conn.send(json)
        }
    }

    fun sendToRelay(
        relayUrl: String,
        event: Event,
    ) {
        val json = """["EVENT",${event.toJson()}]"""
        connections[relayUrl]?.send(json)
    }

    fun subscribe(
        filters: List<Filter>,
        onEvent: (String, String) -> Unit,
    ): String {
        val subId = "sub_${subscriptionCounter.incrementAndGet()}"
        val filtersJson = filters.joinToString(",") { it.toJson() }
        val reqJson = """["REQ","$subId",$filtersJson]"""

        scope.launch {
            for ((_, msg) in incomingEvents) {
                onEvent(subId, msg)
            }
        }

        connections.values.forEach { conn ->
            conn.send(reqJson)
        }

        return subId
    }

    fun unsubscribe(subId: String) {
        val closeJson = """["CLOSE","$subId"]"""
        connections.values.forEach { it.send(closeJson) }
    }

    fun connectedRelays(): List<String> = connections.keys.toList()

    fun isConnected(relayUrl: String): Boolean = connections[relayUrl]?.isConnected == true

    inner class RelayConnection(
        val url: String,
    ) {
        private var socket: WebSocket? = null
        var isConnected = false
            private set

        private val listener =
            object : WebSocketListener {
                override fun onOpen(
                    pingMillis: Int,
                    compression: Boolean,
                ) {
                    isConnected = true
                }

                override fun onMessage(text: String) {
                    incomingEvents.trySend(url to text)
                }

                override fun onClosed(
                    code: Int,
                    reason: String,
                ) {
                    isConnected = false
                }

                override fun onFailure(
                    t: Throwable,
                    code: Int?,
                    response: String?,
                ) {
                    isConnected = false
                    scope.launch {
                        delay(5000)
                        connect()
                    }
                }
            }

        fun connect() {
            socket =
                BasicOkHttpWebSocket(NormalizedRelayUrl(url), { httpClient }, listener).also {
                    it.connect()
                }
        }

        fun disconnect() {
            socket?.disconnect()
            socket = null
            isConnected = false
        }

        fun send(msg: String): Boolean = socket?.send(msg) ?: false
    }
}
