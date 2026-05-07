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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of [Relay] instances keyed by relay URL. Implements
 * [WebsocketBuilder] so it can be plugged into
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient] in place of
 * `BasicOkHttpWebSocket.Builder` to redirect every outbound connection to an
 * in-memory relay.
 *
 * Usage:
 * ```
 * val hub = RelayHub()
 * val relay = hub.getOrCreate("ws://test.relay/")
 * runBlocking { relay.preload(listOf(event1, event2)) }
 * val client = NostrClient(hub, scope)
 * ```
 *
 * Unknown URLs auto-create an empty relay so a single hub can transparently
 * back any number of test endpoints.
 */
class RelayHub(
    private val defaultPolicy: () -> IRelayPolicy = { EmptyPolicy },
) : WebsocketBuilder,
    AutoCloseable {
    private val relays = ConcurrentHashMap<NormalizedRelayUrl, Relay>()

    @Volatile
    private var closed = false

    fun getOrCreate(url: NormalizedRelayUrl): Relay {
        check(!closed) { "RelayHub has been closed" }
        return relays.getOrPut(url) {
            Relay(url = url, policyBuilder = defaultPolicy)
        }
    }

    fun getOrCreate(url: String): Relay = getOrCreate(RelayUrlNormalizer.normalize(url))

    fun get(url: NormalizedRelayUrl): Relay? = relays[url]

    fun urls(): Set<NormalizedRelayUrl> = relays.keys.toSet()

    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket = InProcessWebSocket(getOrCreate(url).server, out)

    /**
     * Idempotent. Sets the closed flag first so concurrent
     * `getOrCreate` calls fail-fast — otherwise a relay created
     * between iteration and clear would leak (its store would never
     * be closed).
     */
    override fun close() {
        closed = true
        relays.values.forEach { runCatching { it.close() } }
        relays.clear()
    }
}
