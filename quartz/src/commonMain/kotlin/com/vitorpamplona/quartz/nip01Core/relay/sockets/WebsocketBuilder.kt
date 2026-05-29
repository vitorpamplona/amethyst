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
     * Returns an opaque, value-comparable token describing the transport
     * configuration (proxy, timeouts, ...) this builder would currently use for
     * [url]. [com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.BasicRelayClient]
     * stores the token of its last connection attempt and, when a reconnect asks
     * to ignore the backoff, only grants the bypass if this token changed since
     * that attempt. That way a relay that keeps failing under the *same* config
     * (e.g. a Tor relay while Tor is still bootstrapping and the SOCKS port is not
     * yet listening) keeps honoring its exponential backoff instead of being
     * hammered on every unrelated infrastructure event.
     *
     * The default returns `null`, which the client treats as "untracked" and
     * therefore always honors the requested bypass (legacy behavior).
     */
    fun connectionConfig(url: NormalizedRelayUrl): Any? = null
}
