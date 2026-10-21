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
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolCounts
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolEventOutbox
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.PoolRequests
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayPool
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.RateLimitGate
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MessageDecoder
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.AuthCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The INostrClient manages Nostr relay operations, subscriptions, and event delivery. It maintains:
 * - A RelayPool for managing connections to a collection of Nostr relays
 * - Active subscriptions tracking through PoolSubscriptionRepository
 * - An event outbox for managing unsent events and retry logic
 * - Automatic relay pool reconciliation based on subscription and event needs
 *
 * Core responsibilities include:
 * - Initializing and managing relay connections using WebSocket builders
 * - Coordinating subscription state across multiple relays
 * - Handling event and filter resending when relays disconnect and reconnect
 * - Aggregating relay status from the pool's state flow
 * - Maintaining listeners for propagating relay events and state changes
 *
 * Features:
 * - Reactive updating of relay sets based on subscription and outbox activity
 * - Relayer reconnection strategy that processes pending requests
 * - Filter comparison logic to detect significant subscription changes and avoid redundant requests
 * - Integration with RelayStats for tracking relay performance
 * - Thread-safe reconnection methods using coroutine flows
 *
 * The class combines flows from active subscriptions and the event outbox to ensure relays
 * are connected to all necessary endpoints. It also listens to relay state changes to update
 * subscriptions and message retries when connections are re-established.
 */
class NostrClient(
    private val websocketBuilder: WebsocketBuilder,
    private val parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    /**
     * Per-frame decode strategy shared by every relay connection. Pass a
     * [com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CachingEventDecoder]
     * to skip re-parsing duplicate EVENT frames (the same event arriving via
     * another subscription or another relay reuses the already-parsed Event;
     * dispatch semantics are unchanged).
     */
    private val decoder: MessageDecoder = MessageDecoder.Default,
) : INostrClient,
    RelayConnectionListener,
    AutoCloseable {
    /** Scope for all subscriptions. */
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())

    private val relayPool: RelayPool =
        RelayPool(
            websocketBuilder,
            this,
            decoder,
            scope,
            object : RateLimitGate.Owner {
                override fun onHeld(
                    relay: NormalizedRelayUrl,
                    cmd: Command,
                ) {
                    if (cmd is ReqCmd) activeRequests.onHeld(relay, cmd)
                }

                override fun onRelease(
                    relay: NormalizedRelayUrl,
                    held: Command,
                ): Command? = if (held is ReqCmd) activeRequests.refreshHeld(relay, held) else held
            },
        )

    private val activeRequests: PoolRequests = PoolRequests()
    private val activeCounts: PoolCounts = PoolCounts()
    private val eventOutbox: PoolEventOutbox = PoolEventOutbox()

    // Copy-on-write sets, swapped whole: registration is rare, while [listeners] is read on
    // every relay message. The swap is a compare-and-set (`update`), not `x = x.plus(y)`:
    // registrations race — every publish, COUNT and negentropy pass adds and removes its own
    // listener from an IO thread — and a plain read-modify-write loses one of two concurrent
    // writes, leaving a caller deaf to its own OKs (it waits out its timeout) or a removed
    // listener running on every message for good.
    private val listeners = MutableStateFlow(setOf<RelayConnectionListener>())

    // Same shape: registration happens a handful of times at wiring, while reads happen on
    // every fetch that meets an auth-gated relay.
    private val authResponders = MutableStateFlow(setOf<IAuthStatus>())

    // controls the state of the client in such a way that if it is active
    // new filters will be sent to the relays and a potential reconnect can
    // be triggered.
    // Default: STARTS active
    //
    // A StateFlow (not a plain Boolean) so the keep-alive loop below can
    // suspend without any scheduled timer while the client is inactive —
    // e.g. the whole time the app sits in the background after the host
    // calls [disconnect].
    private val isActiveFlow = MutableStateFlow(true)

    /** Relays [keepConnected] holds, with how many holds each has; wanted like a subscription's. */
    private val keptRelays = MutableStateFlow(mapOf<NormalizedRelayUrl, Int>())

    /**
     * Whatches for any changes in the relay list from subscriptions or outbox
     * and updates the relayPool as needed.
     */
    @OptIn(FlowPreview::class)
    private val allRelays =
        combine(
            activeRequests.desiredRelays,
            activeCounts.relays,
            eventOutbox.relays,
            keptRelays,
        ) { reqs, counts, outbox, kept ->
            reqs + counts + outbox + kept.keys
        }.sample(300)
            .onEach {
                // The sample only says when to look. What the pool keeps is read now, and read
                // again before each drop: a publish that came in after the sample has already sent
                // its EVENT on the socket a stale set would close, losing that EVENT's OK until
                // the keep-alive sweep redialed (a minute, measured on a one-at-a-time publisher).
                val added = relayPool.updatePool(wantedRelays(), ::isWanted)
                // A relay the pool takes back in has work waiting (a REQ, a COUNT or an EVENT the
                // outbox still holds), and joining the pool does not dial it.
                if (added && isActive()) reconnect(onlyIfChanged = true)
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                wantedRelays(),
            )

    /** Every relay a subscription, a count, a pending publish or a [keepConnected] wants, as of now. */
    private fun wantedRelays(): Set<NormalizedRelayUrl> = activeRequests.desiredRelays.value + activeCounts.relays.value + eventOutbox.relays.value + keptRelays.value.keys

    /** [wantedRelays] for one relay, without building the union. */
    private fun isWanted(relay: NormalizedRelayUrl): Boolean =
        relay in activeRequests.desiredRelays.value ||
            relay in activeCounts.relays.value ||
            relay in eventOutbox.relays.value ||
            relay in keptRelays.value

    // Reconnects all relays that may have disconnected
    override fun connect() {
        isActiveFlow.value = true
        relayPool.connect()
    }

    override fun disconnect() {
        isActiveFlow.value = false
        relayPool.disconnect()
        // The host is putting us down (typically the app backgrounding). Whatever the
        // decoder cached has no dedup value left across that gap, but it would keep
        // costing memory for the whole background stretch, so release it now rather
        // than leaving a timer running to do it later.
        decoder.clearCache()
    }

    override fun isActive() = isActiveFlow.value

    class Reconnect(
        val onlyIfChanged: Boolean,
        val ignoreRetryDelays: Boolean,
    )

    val refreshConnection = MutableStateFlow(Reconnect(false, false))

    @OptIn(FlowPreview::class)
    val debouncingConnection =
        refreshConnection
            .debounce(200)
            .onEach {
                if (it.onlyIfChanged) {
                    relayPool.reconnectIfNeedsTo(it.ignoreRetryDelays)
                } else {
                    relayPool.disconnect()
                    relayPool.connect()
                }
            }.stateIn(
                scope,
                SharingStarted.Eagerly,
                false,
            )

    /**
     * Periodically wakes up disconnected relays. Without this, a relay
     * that hit a long backoff (5 min, e.g. host unreachable or a server
     * error code) would stay disconnected forever in the absence of any
     * subscription change. The per-relay [BasicRelayClient] backoff still
     * gates the actual reconnect attempt, so dead relays are not hammered.
     *
     * While the client is inactive (the host called [disconnect], e.g. the
     * app moved to the background) the loop suspends on [isActiveFlow]
     * instead of ticking: no timer fires at all until [connect] flips the
     * flag back, saving periodic CPU wake-ups for the whole background
     * stretch.
     */
    private val keepAliveJob =
        scope.launch {
            while (true) {
                isActiveFlow.first { it }
                delay(KEEP_ALIVE_INTERVAL_MS)
                if (this@NostrClient.isActive()) {
                    relayPool.reconnectIfNeedsTo(ignoreRetryDelays = false)
                }
            }
        }

    /**
     * Ages out the decoder's cache on a clock.
     *
     * A caching decoder bounds what it holds by capacity, which caps the cost during a
     * burst but never ends it: its generations only rotate inside an insert, so a
     * client that drops to a trickle keeps everything from the initial burst forever.
     *
     * Deliberately NOT idle-triggered. Measured on device: relays keep pushing events
     * down open subscriptions indefinitely, so the decoder is never idle in the sense
     * of "saw no frames" — an idle check returned false on every tick across 240s of
     * a flat heap. Aging unconditionally is what actually releases the memory.
     *
     * Ids survive one tick and die on the next, so [DECODER_AGE_OUT_MS] bounds their
     * lifetime at ~2x that. Suspends on [isActiveFlow] like [keepAliveJob], so no timer
     * fires while the client is down — [disconnect] clears outright on the way there.
     *
     * 30s is measured, not guessed. Sweeping the interval on device and comparing hit
     * rate at a matched ~19k frames (runs pull different volumes, so raw endpoints are
     * not comparable):
     *
     * ```
     *   tick    hit rate   parses   ids still cached at rest
     *    10s      44.9%    10,663      ~2
     *    30s      58.5%     7,569      ~2
     *    60s      60.4%     7,520      ~3
     *   120s      60.6%     7,555     7,438  (one tick in 3 min -> never releases)
     * ```
     *
     * Hit rate saturates by 30s, so a shorter tick only buys re-parses (10s costs 41%
     * more), and a longer one only holds memory. The knee is where the tick stops being
     * the binding constraint and [CachingEventDecoder.capacity] takes over: at the
     * observed ~400 frames/s that crossover is 8192/400 ~= 20s, so 30s sits just past
     * it. Re-measure if capacity or typical frame rates change.
     */
    private val decoderTrimJob =
        scope.launch {
            while (true) {
                isActiveFlow.first { it }
                delay(DECODER_AGE_OUT_MS)
                decoder.ageOutCache()
            }
        }

    companion object {
        private const val KEEP_ALIVE_INTERVAL_MS = 60_000L
        private const val DECODER_AGE_OUT_MS = 30_000L
    }

    override fun reconnect(
        onlyIfChanged: Boolean,
        ignoreRetryDelays: Boolean,
    ) {
        refreshConnection.tryEmit(Reconnect(onlyIfChanged, ignoreRetryDelays))
    }

    override fun resetBackoff() {
        // Applied eagerly rather than through refreshConnection: that flow debounces,
        // so a reconnect emitted within 200ms would replace this one and lose the reset.
        relayPool.resetBackoff()
    }

    override fun resetBackoff(url: NormalizedRelayUrl) {
        relayPool.getRelay(url)?.resetBackoff()
    }

    override fun ensureInPool(url: NormalizedRelayUrl) {
        // Membership only. Connecting is reconnect()'s job, and going through the pool's
        // own create keeps the relay client identical to the one publish would have made.
        relayPool.createRelayIfAbsent(url)
    }

    override fun keepConnected(url: NormalizedRelayUrl) {
        keptRelays.update { it + (url to (it[url] ?: 0) + 1) }
        // Joining the pool (published, so availableRelaysFlow lists it) does not dial; the
        // reconnect does, as it does for a new subscription.
        relayPool.addRelay(url)
        if (isActive()) reconnect(onlyIfChanged = true)
    }

    override fun releaseConnection(url: NormalizedRelayUrl) {
        // The next sample drops the relay if nothing else wants it.
        keptRelays.update { kept ->
            val holds = kept[url] ?: return@update kept
            if (holds <= 1) kept - url else kept + (url to holds - 1)
        }
    }

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        val relaysToUpdate = activeRequests.addOrUpdate(subId, filters, listener)

        if (isActive()) {
            activeRequests.sendToRelayIfChanged(subId, relaysToUpdate) { relay, cmd ->
                if (cmd is CloseCmd || cmd is AuthCmd) {
                    relayPool.sendIfConnected(relay, cmd)
                } else {
                    relayPool.sendOrConnectAndSync(relay, cmd)
                }
            }

            // wakes up all the other relays
            // makes sure the relay wakes up if it was disconnected by the server
            // upon connection, the relay will run the default Sync and update all
            // filters, including this one.
            reconnect(true)
        }
    }

    override fun count(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ) {
        val relaysToUpdate = activeCounts.addOrUpdate(subId, filters)

        if (isActive()) {
            activeCounts.sendToRelayIfChanged(subId, relaysToUpdate) { relay, cmd ->
                if (cmd is CloseCmd || cmd is AuthCmd) {
                    relayPool.sendIfConnected(relay, cmd)
                } else {
                    relayPool.sendOrConnectAndSync(relay, cmd)
                }
            }

            // wakes up all the other relays
            reconnect(true)
        }
    }

    override fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
    ) = publish(event, relayList, null)

    override fun publishRaw(
        event: Event,
        eventJson: String,
        relayList: Set<NormalizedRelayUrl>,
    ) = publish(event, relayList, eventJson)

    private fun publish(
        event: Event,
        relayList: Set<NormalizedRelayUrl>,
        eventJson: String?,
    ) {
        val relaysToUpdate = eventOutbox.markAsSending(event, relayList, eventJson)

        if (isActive()) {
            eventOutbox.sendToRelayIfChanged(event, relaysToUpdate) { relay, cmd ->
                relayPool.sendOrConnectAndSync(relay, cmd)
            }

            // wakes up all the other relays
            reconnect(true)
        }
    }

    override fun unsubscribe(subId: String) {
        val relaysToUpdateReqs = activeRequests.remove(subId)
        val relaysToUpdateCounts = activeCounts.remove(subId)

        // The flush must run even while INACTIVE, because it is also what releases the
        // subscription's state row. The app backgrounds constantly and tears feeds down
        // while it is down there; gating this on isActive() would leak a row per teardown
        // for the whole background stretch, and every one of them would then be scanned
        // on every relay connect once the app comes back.
        //
        // What is gated is the wire traffic: while inactive the pool is disconnected, so
        // sending is both impossible and pointless (the relay dropped the subscription
        // when the socket died) -- and routing through the pool would materialize a relay
        // client for a relay we deliberately stopped talking to.
        val send: (NormalizedRelayUrl, Command) -> Unit =
            if (isActive()) relayPool::sendIfConnected else { _, _ -> }

        activeRequests.sendToRelayIfChanged(subId, relaysToUpdateReqs, send)
        activeCounts.sendToRelayIfChanged(subId, relaysToUpdateCounts, send)
    }

    override fun syncFilters(relay: IRelayClient) {
        if (isActive()) {
            // Inline, not launched. On a fresh connection this runs from the socket's onOpen,
            // while the relay client holds every other send until it returns, so whatever was
            // registered while the socket was opening goes out ahead of what is sent once it is
            // up. Launched, the replay raced those sends: a REQ registered before the connection
            // opened could reach the relay after an EVENT sent right after it did, and kind 24133
            // is ephemeral, so a NIP-46 request sent that way reached no bunker (measured in
            // NostrSignerRemotePrivateZapTest on a loaded iOS simulator: the wire carried
            // [REQ remote, EVENT] and only then the bunker's REQ).
            //
            // A back-off a relay stated outlives the socket it was stated on: a reconnect
            // replays everything, so the replay goes through the same gate.
            activeRequests.syncState(relay.url, relay::sendFromSync)
            activeCounts.syncState(relay.url, relay::sendFromSync)
            eventOutbox.syncState(relay.url, relay::sendFromSync)
        }
    }

    /**
     * when a new connection starts, resets the state
     */
    override fun onConnecting(relay: IRelayClient) {
        activeRequests.onConnecting(relay.url)
        listeners.value.forEach { it.onConnecting(relay) }
    }

    /**
     * Relay just connected. Use this to send all
     * filters and events you need.
     */
    override fun onConnected(
        relay: IRelayClient,
        pingMillis: Int,
        compressed: Boolean,
    ) {
        syncFilters(relay)
        listeners.value.forEach { it.onConnected(relay, pingMillis, compressed) }
    }

    override fun onSent(
        relay: IRelayClient,
        cmdStr: String,
        cmd: Command,
        success: Boolean,
    ) {
        if (success) {
            activeRequests.onSent(relay.url, cmd)
            activeCounts.onSent(relay.url, cmd)
            eventOutbox.onSent(relay.url, cmd)?.let { gaveUp ->
                listeners.value.forEach { it.onEventGaveUp(relay, gaveUp) }
            }
        }
        listeners.value.forEach { it.onSent(relay, cmdStr, cmd, success) }
    }

    override suspend fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        activeRequests.onIncomingMessage(relay, msg)
        activeCounts.onIncomingMessage(relay, msg)
        eventOutbox.onIncomingMessage(relay.url, msg) { authMayLand(relay.url) }

        // NIP-01: a `rate-limited:` refusal may say how long to back off. Hand the refused REQ
        // or EVENT straight back to the relay, whose [RateLimitGate] holds it until the back-off
        // passes; without one, it stays parked as before. COUNTs are one-shot and not re-sent.
        when (msg) {
            is ClosedMessage -> {
                val backOff = msg.retryAfterMs
                if (backOff != null && RateLimitGate.honors(backOff) && activeCounts.getSubscriptionFiltersOrNull(msg.subId) == null) {
                    activeRequests.retryRateLimited(relay.url, setOf(msg.subId), relay::sendOrConnectAndSync)
                }
            }

            is OkMessage -> {
                val backOff = msg.retryAfterMs
                if (!msg.success && backOff != null && RateLimitGate.honors(backOff)) {
                    eventOutbox.retryRateLimited(relay.url, setOf(msg.eventId), relay::sendOrConnectAndSync)
                }
            }

            else -> {}
        }

        listeners.value.forEach { it.onIncomingMessage(relay, msgStr, msg) }
    }

    /**
     * Relay just diconnected.
     */
    override fun onDisconnected(relay: IRelayClient) {
        activeRequests.onDisconnected(relay.url)
        listeners.value.forEach { it.onDisconnected(relay) }

        // If the client is still active and the relay is still in the
        // desired set (i.e. there are subscriptions, counts or pending
        // outbox events that want it), reconnect it. Without this, a
        // server-initiated close leaves the relay disconnected until a
        // subscription change or an explicit reconnect() arrives.
        // The reconnect path is debounced and goes through
        // reconnectIfNeedsTo, which respects each relay's exponential
        // backoff so we don't hammer dead relays.
        // Live, not allRelays.value: that is a 300ms sample, and a relay that drops within one
        // sample of joining would not be in it yet.
        if (isActive() && isWanted(relay.url)) {
            reconnect(onlyIfChanged = true)
            retryWhenBackoffEnds(relay)
        }
    }

    /** One pending retry per relay; a newer disconnect replaces the older timer. */
    private val backoffRetries = LargeCache<NormalizedRelayUrl, Job>()

    /**
     * The reconnect above is gated by the relay's backoff: a relay that dropped soon after its
     * last dial (1s, doubling) is skipped, and nothing asked again until the keep-alive sweep,
     * up to a minute later. A subscription's REQ or a publish's EVENT waited that long. Ask
     * again exactly when the backoff ends, as long as the client is active and still wants
     * the relay (offline, the host calls [disconnect], so no retry storms a dead network).
     */
    private fun retryWhenBackoffEnds(relay: IRelayClient) {
        val waitSeconds = relay.secondsUntilReconnect() ?: return
        if (waitSeconds <= 0) return
        backoffRetries.get(relay.url)?.cancel()
        backoffRetries.put(
            relay.url,
            scope.launch {
                delay(waitSeconds * 1000)
                backoffRetries.remove(relay.url)
                if (isActive() && isWanted(relay.url)) reconnect(onlyIfChanged = true)
            },
        )
    }

    override fun onCannotConnect(
        relay: IRelayClient,
        errorMessage: String,
    ) {
        activeRequests.onCannotConnect(relay.url, errorMessage)
        activeCounts.onCannotConnect(relay.url, errorMessage)
        eventOutbox.onCannotConnect(relay.url, errorMessage)

        listeners.value.forEach { it.onCannotConnect(relay, errorMessage) }
    }

    override fun getOrCreateRelay(url: NormalizedRelayUrl): IRelayClient = relayPool.getOrCreateRelay(url)

    /**
     * Whether an AUTH for [relay] can still land, so that its `auth-required:` refusal means
     * "later": someone answers challenges, and this relay's last AUTH did not fail.
     */
    private fun authMayLand(relay: NormalizedRelayUrl): Boolean = authResponders.value.any { it.authSnapshot(relay).phase != RelayAuthSnapshot.Phase.AUTH_FAILED }

    override fun authResponders(): Set<IAuthStatus> = authResponders.value

    override fun addAuthResponder(responder: IAuthStatus) {
        authResponders.update { it + responder }
    }

    override fun removeAuthResponder(responder: IAuthStatus) {
        authResponders.update { it - responder }
    }

    override fun addConnectionListener(listener: RelayConnectionListener) {
        listeners.update { it + listener }
    }

    fun hasConnectionListener(listener: RelayConnectionListener): Boolean = listener in listeners.value

    override fun removeConnectionListener(listener: RelayConnectionListener) {
        listeners.update { it - listener }
    }

    override fun activeRequests(url: NormalizedRelayUrl): Map<String, List<Filter>> = activeRequests.activeFiltersFor(url)

    override fun activeCounts(url: NormalizedRelayUrl): Map<String, List<Filter>> = activeCounts.activeFiltersFor(url)

    override fun registrySizes() =
        RegistrySizes(
            liveRequests = activeRequests.activeSubscriptionCount(),
            liveCounts = activeCounts.activeQueryCount(),
        )

    override fun activeOutboxCache(url: NormalizedRelayUrl): Set<HexKey> = eventOutbox.activeOutboxCacheFor(url)

    override fun activeOutboxEvents(url: NormalizedRelayUrl): List<Event> = eventOutbox.activeOutboxEventsFor(url)

    override fun pendingPublishRelaysFor(eventId: HexKey): Set<NormalizedRelayUrl>? = eventOutbox.pendingRelaysFor(eventId)

    override fun getReqFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = activeRequests.getSubscriptionFiltersOrNull(subId)

    override fun getCountFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = activeCounts.getSubscriptionFiltersOrNull(subId)

    override fun connectedRelaysFlow() = relayPool.connectedRelays

    override fun availableRelaysFlow() = relayPool.availableRelays

    override fun close() {
        disconnect()
        listeners.value = emptySet()
        activeCounts.destroy()
        activeRequests.destroy()
        eventOutbox.destroy()
        scope.cancel()
    }
}
