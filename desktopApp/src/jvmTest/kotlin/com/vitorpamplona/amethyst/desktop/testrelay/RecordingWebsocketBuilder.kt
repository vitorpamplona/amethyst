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
package com.vitorpamplona.amethyst.desktop.testrelay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * [WebsocketBuilder] that delegates to [inner] but records every frame
 * the client sends so tests can assert on subscription-id traffic
 * without parsing raw JSON or wiring a server-side observer.
 *
 * Used by the Phase 5.2 regression tests to count how many times the
 * `"bootstrap-relay-config"` REQ is emitted — the no-double-subscribe
 * invariant the bootstrap-gate removal must not violate.
 */
class RecordingWebsocketBuilder(
    private val inner: WebsocketBuilder,
) : WebsocketBuilder {
    private val perSubscriptionReqCount = ConcurrentHashMap<String, AtomicInteger>()

    fun reqCountForSubscription(subId: String): Int = perSubscriptionReqCount[subId]?.get() ?: 0

    fun totalReqCount(): Int = perSubscriptionReqCount.values.sumOf { it.get() }

    fun observedSubscriptionIds(): Set<String> = perSubscriptionReqCount.keys.toSet()

    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket {
        val delegate = inner.build(url, out)
        return object : WebSocket by delegate {
            override fun send(msg: String): Boolean {
                if (msg.startsWith("[\"REQ\"")) {
                    // ["REQ","<subId>",{filter1},...]
                    val rest = msg.removePrefix("[\"REQ\",\"")
                    val subId = rest.substringBefore('"')
                    perSubscriptionReqCount
                        .computeIfAbsent(subId) { AtomicInteger(0) }
                        .incrementAndGet()
                }
                return delegate.send(msg)
            }
        }
    }
}

/**
 * [WebsocketBuilder] that produces sockets which never connect. Used by
 * the Phase 5.2 "no relays available" regression test to verify the
 * subscription registers anyway (the pool queues it) and the UI does
 * not deadlock waiting for a connection that will never come up.
 */
class NeverConnectsWebsocketBuilder : WebsocketBuilder {
    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket =
        object : WebSocket {
            override fun needsReconnect(): Boolean = true

            override fun connect() {
                // Intentionally a no-op — never opens, never closes.
            }

            override fun disconnect() {
                // No-op.
            }

            override fun send(msg: String): Boolean = false
        }
}
