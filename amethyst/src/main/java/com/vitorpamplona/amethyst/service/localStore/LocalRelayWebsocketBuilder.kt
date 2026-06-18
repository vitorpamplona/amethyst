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
package com.vitorpamplona.amethyst.service.localStore

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.inprocess.InProcessWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder

/**
 * [WebsocketBuilder] that turns the on-device [LocalEventStore] into just
 * another relay in the pool. Connections to [LocalEventStore.LOCAL_RELAY_URL]
 * are served in-process (no socket) by an [InProcessWebSocket] over the local
 * relay's [NostrServer][com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer];
 * every other URL is delegated to the real (OkHttp) transport unchanged.
 *
 * Wrap the app's normal websocket builder with this before handing it to
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient] — from then on
 * any subscription/publish that includes the local URL is answered from SQLite.
 */
class LocalRelayWebsocketBuilder(
    private val delegate: WebsocketBuilder,
    private val localStore: LocalEventStore,
) : WebsocketBuilder {
    override fun build(
        url: NormalizedRelayUrl,
        out: WebSocketListener,
    ): WebSocket =
        if (url == LocalEventStore.LOCAL_RELAY_URL) {
            InProcessWebSocket(localStore.server, out)
        } else {
            delegate.build(url, out)
        }

    // The in-process relay is always reachable; defer to the real transport otherwise.
    override fun canConnect(url: NormalizedRelayUrl): Boolean = url == LocalEventStore.LOCAL_RELAY_URL || delegate.canConnect(url)
}
