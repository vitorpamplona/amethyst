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

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.RelayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.client.single.local.LocalStoreRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * [RelayBuilder] that makes the on-device [LocalEventStore] just another relay
 * in the pool: [LocalEventStore.LOCAL_RELAY_URL] is served by a
 * [LocalStoreRelayClient] straight from SQLite (no socket, no JSON), and every
 * other URL is delegated to the real (websocket) [delegate] unchanged.
 *
 * Wrap the app's default relay builder with this before handing it to
 * [com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient].
 */
class LocalRelayBuilder(
    private val delegate: RelayBuilder,
    private val localStore: LocalEventStore,
) : RelayBuilder {
    override fun build(
        url: NormalizedRelayUrl,
        listener: RelayConnectionListener,
    ): IRelayClient =
        if (url == LocalEventStore.LOCAL_RELAY_URL) {
            LocalStoreRelayClient(url, localStore.store, listener, localStore.scope)
        } else {
            delegate.build(url, listener)
        }
}
