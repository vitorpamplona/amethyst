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
package com.vitorpamplona.quartz.testrelay

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.server.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of [TestRelay] instances keyed by relay URL. Implements
 * [WebsocketBuilder] so it can be plugged into
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient] in place of
 * `BasicOkHttpWebSocket.Builder` to redirect every outbound connection to an
 * in-memory relay.
 *
 * Usage:
 * ```
 * val hub = TestRelayHub()
 * val relay = hub.getOrCreate("ws://test.relay/")
 * runBlocking { relay.preload(listOf(event1, event2)) }
 * val client = NostrClient(hub, scope)
 * ```
 *
 * Unknown URLs auto-create an empty relay so a single hub can transparently
 * back any number of test endpoints.
 */
class TestRelayHub(
    private val defaultPolicy: () -> IRelayPolicy = { EmptyPolicy },
) : WebsocketBuilder,
    AutoCloseable {
    private val relays = ConcurrentHashMap<NormalizedRelayUrl, TestRelay>()

    fun getOrCreate(url: NormalizedRelayUrl): TestRelay =
        relays.getOrPut(url) {
            TestRelay(url = url, policyBuilder = defaultPolicy)
        }

    fun getOrCreate(url: String): TestRelay = getOrCreate(RelayUrlNormalizer.normalize(url))

    fun get(url: NormalizedRelayUrl): TestRelay? = relays[url]

    fun urls(): Set<NormalizedRelayUrl> = relays.keys.toSet()

    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket = InProcessWebSocket(getOrCreate(url), out)

    override fun close() {
        relays.values.forEach { it.close() }
        relays.clear()
    }
}
