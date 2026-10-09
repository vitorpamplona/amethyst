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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.testing.FakeRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/*
 * Scripted INostrClient fakes shared by the relay-accessory tests. FakePagingRelay (its own file)
 * serves a corpus page by page; these script a relay's answers more directly.
 */

/** Answers each COUNT with the messages [script] gives for its relay, each after its delay. */
internal class ScriptedCountClient(
    private val scope: CoroutineScope,
    private val script: (NormalizedRelayUrl, String) -> List<Pair<Long, Message>>,
) : INostrClient by EmptyNostrClient() {
    private val listeners = CopyOnWriteArrayList<RelayConnectionListener>()

    override fun addConnectionListener(listener: RelayConnectionListener) {
        listeners.add(listener)
    }

    override fun removeConnectionListener(listener: RelayConnectionListener) {
        listeners.remove(listener)
    }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        val relay = filters.keys.single()
        val relayClient = FakeRelayClient(relay)
        scope.launch {
            for ((delayMs, msg) in script(relay, subId)) {
                delay(delayMs)
                listeners.forEach { it.onIncomingMessage(relayClient, "", msg) }
            }
        }
    }
}

/** Drops the connection under its first [drops] `REQ`s, then serves [corpus] by id. */
internal class DropsFirst(
    private val scope: CoroutineScope,
    private val corpus: List<Event>,
    private val drops: Int,
    private val extra: List<Event> = emptyList(),
) : INostrClient by EmptyNostrClient() {
    @Volatile var requests = 0

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        val (relay, relayFilters) = filters.entries.single()
        val req = ++requests
        scope.launch {
            if (req <= drops) {
                listener?.onCannotConnect(relay, "WebSocket Failure: EOFException", relayFilters)
            } else {
                (corpus.filter { e -> relayFilters.any { it.match(e) } } + extra).forEach { listener?.onEvent(it, false, relay, relayFilters) }
                listener?.onEose(relay, relayFilters)
            }
        }
    }
}
