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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
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

    /**
     * [resetBackoff] for [url] alone: its next dial goes out at once, every other relay keeps
     * its wait. For a caller that knows better than the pool's session-length rule that the
     * relay is up — [fetchAllPages][com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages],
     * whose page the relay hung up on while serving it. A no-op for a client without a pool.
     */
    fun resetBackoff(url: NormalizedRelayUrl) { }

    /**
     * Puts [url] back in the connection pool if it is no longer there, without dialing it.
     *
     * [reconnect] can only act on relays the pool still holds, so a caller that means to
     * revive one it stopped hearing from has to restore that precondition first —
     * otherwise the reconnect iterates past an empty pool and does nothing at all, which
     * is indistinguishable from a relay that was asked and stayed silent.
     *
     * A relay leaves the pool when nothing wants it any more (no subscription, no count,
     * no pending publish). That is normally the right call and normally permanent, so
     * this is deliberately narrow: it restores membership and leaves dialing, backoff and
     * filter syncing to [reconnect]. Defaults to a no-op — a client with no pool has
     * nothing to restore and should not be forced to implement one.
     */
    fun ensureInPool(url: NormalizedRelayUrl) { }

    /**
     * Keeps [url] connected whatever else wants it: the client dials it, holds the socket while
     * nothing is subscribed, counted or pending there, and redials it after the relay hangs up,
     * as it does for a relay with an open subscription. For a caller that talks to one relay in
     * bursts, a publisher forwarding batches say, which otherwise loses its socket between two of
     * them and pays a fresh dial (and, on a NIP-42 relay, a fresh AUTH) for the next.
     *
     * Counted: each call needs its own [releaseConnection], and the relay is let go when the last
     * one is released. Defaults to a no-op for a client without a pool.
     */
    fun keepConnected(url: NormalizedRelayUrl) { }

    /** Undoes one [keepConnected]; once none is left, [url] stays only while something else wants it. */
    fun releaseConnection(url: NormalizedRelayUrl) { }

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
    )

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
     * [publish] with [event]'s JSON already in hand: [eventJson] goes on the wire verbatim, on
     * the first send and every re-send, instead of [event] being serialized again. For bulk
     * publishers that read events as JSON (an export file, a store row) and parsed them only to
     * know their id. [eventJson] must be [event]'s own JSON object; see [EventCmd.eventJson].
     *
     * Clients that cannot send it verbatim serialize [event] as [publish] does.
     */
    fun publishRaw(
        event: Event,
        eventJson: String,
        relayList: Set<NormalizedRelayUrl>,
    ) = publish(event, relayList)

    /**
     * Returns the relays that have not yet acknowledged [eventId] with an OK,
     * or null if the event is not tracked (never published, or already fully done).
     * Use to poll for delivery confirmation after [publish].
     */
    fun pendingPublishRelaysFor(eventId: HexKey): Set<NormalizedRelayUrl>?

    fun addConnectionListener(listener: RelayConnectionListener)

    fun removeConnectionListener(listener: RelayConnectionListener)

    /**
     * The NIP-42 responders attached to this client — the things that will actually
     * answer a relay's AUTH challenge (in practice a
     * [com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator],
     * which registers itself from its own constructor).
     *
     * Read by the fetch accessories: a relay refusing a REQ with `auth-required:` is
     * worth waiting on only if something is going to answer it, and this is the only
     * place that fact is knowable from a bare client. Empty means "nobody is
     * answering AUTH here", and the accessories then treat the refusal as terminal —
     * the pre-existing behaviour.
     *
     * A [Set], not a single slot, because one client can carry more than one
     * responder (an account signer plus a derived stream-key signer, say) and a
     * last-one-wins field would silently hide whichever registered first.
     *
     * Defaults to empty so a client that keeps no registry — a test double, a
     * narrowing wrapper — needs no change. A delegating wrapper
     * (`INostrClient by delegate`) forwards this automatically.
     */
    fun authResponders(): Set<IAuthStatus> = emptySet()

    /** Registers a NIP-42 responder. Default: no-op, for clients that keep no registry. */
    fun addAuthResponder(responder: IAuthStatus) { }

    /** Unregisters a NIP-42 responder. Default: no-op. */
    fun removeAuthResponder(responder: IAuthStatus) { }

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

    /**
     * Size of the pool's REQ / COUNT state registries — the maps every relay
     * connect, disconnect and connection failure scans in full.
     *
     * These track LIVE subscriptions, so a healthy client keeps them near the number of
     * subscriptions it believes it holds. A host that drives many relays from one client
     * should alarm on unbounded growth here: registry size multiplies the cost of every
     * relay lifecycle event, so a leak shows up as connect-path CPU long before it shows
     * up as memory.
     */
    fun registrySizes(): RegistrySizes = RegistrySizes(0, 0)

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

/** @see INostrClient.registrySizes */
data class RegistrySizes(
    val liveRequests: Int,
    val liveCounts: Int,
)
