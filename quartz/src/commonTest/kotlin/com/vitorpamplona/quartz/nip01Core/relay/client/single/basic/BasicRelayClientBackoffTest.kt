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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.EmptyConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for the per-relay backoff bypass in [BasicRelayClient].
 *
 * A forced reconnect (`ignoreRetryDelays = true`) must only skip the exponential
 * backoff when the relay's transport config actually changed since the last failed
 * attempt. This is what stops Tor relays from reconnect-fail-reconnecting on every
 * infrastructure event while Tor is still bootstrapping (the SOCKS port is unchanged,
 * so the config token is unchanged, so the backoff is honored).
 */
class BasicRelayClientBackoffTest {
    private val url = NormalizedRelayUrl("wss://relay.test")

    /**
     * Fake transport: every [WebSocket.connect] immediately reports a Tor-style
     * connection failure, returning the relay to the disconnected state. [config]
     * is the value-comparable transport token the builder reports; tests mutate it
     * to simulate Tor coming up / the proxy changing.
     */
    private class FakeBuilder(
        var config: Any?,
    ) : WebsocketBuilder {
        var connectAttempts = 0

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket =
            object : WebSocket {
                override fun needsReconnect() = true

                override fun connect() {
                    connectAttempts++
                    // Simulate a Tor SOCKS port that isn't listening yet. The "failed to
                    // connect to /127.0.0.1" message is the ignored-error path, so no long
                    // backoff is forced — only the normal doubling applies.
                    out.onFailure(RuntimeException("failed to connect to /127.0.0.1:9050"), null, null)
                }

                override fun disconnect() {}

                override fun send(msg: String) = true
            }

        override fun connectionConfig(url: NormalizedRelayUrl): Any? = config
    }

    @Test
    fun forcedReconnectHonorsBackoffWhenConfigUnchanged() {
        val builder = FakeBuilder(config = "tor:9050:booting")
        val relay = BasicRelayClient(url, builder, EmptyConnectionListener)

        // First forced attempt: nothing has connected yet, so it fires and fails.
        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        assertEquals(1, builder.connectAttempts)

        // Tor is still booting: same config token. A second forced reconnect must NOT
        // bypass the backoff (the failing situation hasn't changed), so no new attempt.
        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        assertEquals(1, builder.connectAttempts)
    }

    @Test
    fun forcedReconnectBypassesBackoffWhenConfigChanged() {
        val builder = FakeBuilder(config = "tor:9050:booting")
        val relay = BasicRelayClient(url, builder, EmptyConnectionListener)

        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        assertEquals(1, builder.connectAttempts)

        // Tor finished bootstrapping: the proxy this relay would use changed. A forced
        // reconnect must now bypass the backoff and try immediately.
        builder.config = "tor:17392:active"
        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        assertEquals(2, builder.connectAttempts)
    }

    @Test
    fun untrackedBuilderAlwaysHonorsForcedReconnect() {
        // A builder that doesn't track configs (returns null) keeps the legacy behavior:
        // a forced reconnect always tries immediately.
        val builder = FakeBuilder(config = null)
        val relay = BasicRelayClient(url, builder, EmptyConnectionListener)

        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        relay.connectAndSyncFiltersIfDisconnected(ignoreRetryDelays = true)
        assertEquals(2, builder.connectAttempts)
    }
}
