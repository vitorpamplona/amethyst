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
package com.vitorpamplona.quartz.nip01Core.relay.sockets

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

interface WebsocketBuilder {
    fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket

    /**
     * Whether the transport for [url] is ready to dial right now. Returning false makes
     * [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient.connect]
     * skip the dial entirely — no socket, no backoff growth — until a later reconnect pass
     * finds it ready.
     *
     * The motivating case: a Tor-routed relay while Tor's SOCKS proxy isn't up yet. Without
     * this gate the pool hammers the dead proxy with doomed dials during the whole Tor
     * bootstrap window. The caller is responsible for re-triggering a reconnect once the
     * transport becomes ready (e.g. on the Tor status flipping to Active).
     *
     * Defaults to true so non-proxied builders need no change.
     */
    fun canConnect(url: NormalizedRelayUrl): Boolean = true
}
