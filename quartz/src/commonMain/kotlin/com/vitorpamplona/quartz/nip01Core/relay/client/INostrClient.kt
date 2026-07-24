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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface INostrClient : AutoCloseable {
    fun connectedRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>>

    fun availableRelaysFlow(): StateFlow<Set<NormalizedRelayUrl>>

    fun connect()

    fun disconnect()

    fun reconnect(
        onlyIfChanged: Boolean = false,
        ignoreRetryDelays: Boolean = false,
    )

    /**
     * Clears the accumulated reconnect backoff of every relay in the pool, so the next
     * [reconnect] dials immediately instead of serving out a penalty earned on a network
     * or transport that is no longer in use. See [IRelayClient.resetBackoff].
     *
     * Kept separate from [reconnect] on purpose: implementations debounce reconnect
     * requests, and folding this into a coalescing command would let a later request
     * silently drop the reset.
     */
    fun resetBackoff() { }

    fun isActive(): Boolean

    /**
     * Sends all current filters, events, etc to the relay.
     * This is called every time the relay connects
     * and when auth is successful
     */
    fun syncFilters(relay: IRelayClient)

    fun subscribe(
        subId: String = newSubId(),
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener? = null,
        reason: String = "",
    )

    /**
     * Maps each currently-active subscription id to a short, human-readable
     * explanation of why it is open (e.g. "Your DMs", "Notifications"). Only
     * subscriptions that were opened with a non-blank reason appear here, and
     * an entry is removed as soon as its subscription is closed — so the map
     * reflects what the live relay connections are actually doing right now.
     * Consumed by the always-on notification service. The default is an empty,
     * never-changing flow for clients that don't track reasons.
     */
    fun subscriptionReasonsFlow(): StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    fun count(
        subId: String = newSubId(),
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    )

    fun unsubscribe(subId: String)

    fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    )

    /**
     * Returns the relays that have not yet acknowledged [eventId] with an OK,
     * or null if the event is not tracked (never published, or already fully done).
     * Use to poll for delivery confirmation after [publish].
     */
    fun pendingPublishRelaysFor(eventId: HexKey): Set<NormalizedRelayUrl>?

    fun addConnectionListener(listener: RelayConnectionListener)

    fun removeConnectionListener(listener: RelayConnectionListener)

    /**
     * Returns the [IRelayClient] for [url], creating and registering it in the
     * connection pool if it is not there yet.
     *
     * Most callers should never need this — [subscribe]/[count]/[publish] manage
     * the pool for you. It exists for accessories that must drive a single relay
     * directly, such as NIP-77 negentropy (which sends `NEG-OPEN` and walks the
     * reconciliation rounds on one connection). The default implementation throws;
     * only a real pool-backed client can hand out relay clients.
     */
    fun getOrCreateRelay(url: NormalizedRelayUrl): IRelayClient = throw UnsupportedOperationException("This INostrClient does not expose relay clients")

    fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>?

    fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>?

    fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>>

    fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>>

    fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey>

    /** The events still pending delivery to [url] (full events, not just ids). */
    fun activeOutboxEvents(url: NormalizedRelayUrl): List<Event>
}

class EmptyNostrClient : INostrClient {
    override fun connectedRelaysFlow() = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    override fun availableRelaysFlow() = MutableStateFlow(emptySet<NormalizedRelayUrl>())

    override fun connect() { }

    override fun disconnect() { }

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) { }

    override fun isActive() = false

    override fun syncFilters(relay: IRelayClient) { }

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
        reason: String,
    ) { }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) { }

    override fun unsubscribe(subId: String) { }

    override fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) { }

    override fun pendingPublishRelaysFor(eventId: HexKey): Set<NormalizedRelayUrl>? = null

    override fun addConnectionListener(listener: RelayConnectionListener) {}

    override fun removeConnectionListener(listener: RelayConnectionListener) {}

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = null

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = emptyMap()

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = emptySet()

    override fun activeOutboxEvents(url: NormalizedRelayUrl): List<Event> = emptyList()

    override fun close() {}
}
