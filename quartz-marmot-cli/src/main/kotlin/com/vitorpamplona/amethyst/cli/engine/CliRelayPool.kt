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
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class CliRelayPool : AutoCloseable {
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

    private val websocketBuilder = BasicOkHttpWebSocket.Builder { httpClient }

    val client = NostrClient(websocketBuilder)

    private val subscriptionCounter = AtomicInteger(0)
    private var relayUrls = setOf<NormalizedRelayUrl>()

    fun connect(relayUrlStrings: List<String>) {
        relayUrls = relayUrlStrings.map { NormalizedRelayUrl(it) }.toSet()
        client.connect()
    }

    fun disconnect() {
        client.disconnect()
    }

    override fun close() {
        client.close()
    }

    fun send(
        event: Event,
        relays: Set<NormalizedRelayUrl> = relayUrls,
    ) {
        client.publish(event, relays)
    }

    fun subscribe(
        filters: List<Filter>,
        listener: SubscriptionListener,
    ): String {
        val subId = "sub_${subscriptionCounter.incrementAndGet()}"
        val filterMap = relayUrls.associateWith { filters }
        client.subscribe(subId = subId, filters = filterMap, listener = listener)
        return subId
    }

    fun unsubscribe(subId: String) {
        client.unsubscribe(subId)
    }

    fun connectedRelays(): Set<NormalizedRelayUrl> = client.connectedRelaysFlow().value

    fun availableRelays(): Set<NormalizedRelayUrl> = relayUrls
}
