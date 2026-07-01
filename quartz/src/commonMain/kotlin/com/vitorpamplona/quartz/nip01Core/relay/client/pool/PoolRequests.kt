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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.ReqSubStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.RequestSubscriptionState
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Manages relay subscriptions for the entire pool in a way that only
 * sends new states to the relay if they have changed.
 *
 * This code also awaits a subscription to come to EOSE since many relays
 * have through switching subs while they are processing the past.
 */
@OptIn(ExperimentalAtomicApi::class)
class PoolRequests {
    /**
     * Desired subs and listeners
     *
     * These are changed immediately when the local code requests and should map
     * to what the app whants to do.
     */
    private val desiredSubs = LargeCache<String, Map<NormalizedRelayUrl, List<Filter>>>()
    private val desiredSubListeners = LargeCache<String, SubscriptionListener>()
    val desiredRelays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    /**
     * Relay states map what we think the relay is doing
     *
     * This is important to link responses with which filter was a sub replying to and
     * to figure out if we need to update the relay if a REQ has changed.
     */
    private val relayState = LargeCache<String, RequestSubscriptionState<NormalizedRelayUrl>>()

    fun subState(subId: String): RequestSubscriptionState<NormalizedRelayUrl> = relayState.getOrCreate(subId) { RequestSubscriptionState() }

    /**
     * Serializes every access to the subscription state machine
     * ([RequestSubscriptionState]) and the "should I send a REQ?" decision.
     *
     * A single subscription can span many relays, and each relay's
     * socket-reader thread delivers messages into this class concurrently while
     * the app thread adds/removes subscriptions — so the plain maps inside
     * [RequestSubscriptionState] are written from several threads at once. That
     * is both a memory hazard (concurrent map mutation) and, more importantly,
     * a logic hazard: the check-then-send in [decideCommandLocked] must be
     * atomic, otherwise two threads can both observe "no REQ in flight" and both
     * send a REQ for the same sub id.
     *
     * This is a tiny non-reentrant spin lock (the same [AtomicBoolean] primitive
     * used by BasicRelayClient's connecting mutex): the critical sections are a
     * handful of map operations, never any I/O. Listener callbacks and the
     * actual socket sends are ALWAYS performed outside the lock — they re-enter
     * this class through [onSent], so holding the lock across them would
     * self-deadlock.
     */
    private val stateLock = AtomicBoolean(false)

    private inline fun <R> withStateLock(block: () -> R): R {
        while (stateLock.exchange(true)) {
            // Another thread holds the lock. Spin-read until it looks free
            // (test-and-test-and-set: cheaper on the cache line than hammering
            // exchange) then retry the acquisition above.
            while (stateLock.load()) { }
        }
        try {
            return block()
        } finally {
            stateLock.store(false)
        }
    }

    /**
     * This is called when a sub is added or removed from this class and
     * should update the desired relay list to get the pool to connect
     * to new ones or disconnect to old ones if they are not needed anymore
     */
    private fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        desiredSubs.forEach { sub, perRelayFilters ->
            myRelays.addAll(perRelayFilters.keys)
        }

        if (desiredRelays.value != myRelays) {
            desiredRelays.tryEmit(myRelays)
        }
    }

    /**
     * Returns all the active filters for a relay, per subscription
     */
    fun activeFiltersFor(url: NormalizedRelayUrl): Map<String, List<Filter>> {
        val myRelays = mutableMapOf<String, List<Filter>>()
        desiredSubs.forEach { sub, perRelayFilters ->
            val filters = perRelayFilters[url]
            if (filters != null) {
                myRelays[sub] = filters
            }
        }
        return myRelays
    }

    /**
     * Adds a new subscription to the pool, and returns which relays
     * MIGHT need to be updated.
     */
    fun addOrUpdate(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ): Set<NormalizedRelayUrl> {
        // saves old relays
        val oldRelays = desiredSubs.get(subId)?.keys ?: emptySet()
        // update filters
        desiredSubs.put(subId, filters)
        // update listener
        if (listener != null) {
            desiredSubListeners.put(subId, listener)
        }
        // create sub state
        subState(subId)
        // update relays for pool
        updateRelays()
        // return all affected relays
        return oldRelays + filters.keys
    }

    /**
     * Removes the sub from the pool, and returns which relays
     * MIGHT need to be updated.
     */
    fun remove(subId: String): Set<NormalizedRelayUrl> =
        if (desiredSubs.containsKey(subId)) {
            // saves old relays
            val oldRelays = desiredSubs.get(subId)?.keys ?: emptySet()
            // update filters
            desiredSubs.remove(subId)
            // update listener
            desiredSubListeners.remove(subId)
            // update relays for pool
            updateRelays()
            // return all affected relays
            oldRelays
        } else {
            emptySet()
        }

    fun getSubscriptionFiltersOrNull(subId: String): Map<NormalizedRelayUrl, List<Filter>>? = desiredSubs.get(subId)

    // --------------------------
    // State management functions
    // --------------------------

    /**
     * When a connecting, updates the state of all subs
     */
    fun onConnecting(url: NormalizedRelayUrl) {
        // Change states to connecting.
        withStateLock {
            relayState.forEach { subId, state ->
                state.connecting(url)
            }
        }
    }

    /**
     * When a new command is sent to this relay, updates the state
     */
    fun onSent(
        relay: NormalizedRelayUrl,
        cmd: Command,
    ) {
        when (cmd) {
            is ReqCmd -> {
                withStateLock {
                    subState(cmd.subId).onOpenReq(relay, cmd.filters)
                }
                desiredSubListeners.get(cmd.subId)?.onSubscriptionStarted(
                    relay = relay.url,
                    forFilters = cmd.filters,
                )
            }

            is CloseCmd -> {
                withStateLock {
                    subState(cmd.subId).onSubscriptionClosed(relay)
                }
                desiredSubListeners.get(cmd.subId)?.onSubscriptionClosed(
                    relay = relay.url,
                )
            }
        }
    }

    /**
     * When a new message is received by the relay, updates the sub
     */
    fun onIncomingMessage(
        relay: IRelayClient,
        msg: Message,
    ) {
        when (msg) {
            is EventMessage -> {
                var isLive = false
                var forFilters: List<Filter>? = null
                withStateLock {
                    val state = relayState.get(msg.subId)
                    state?.onNewEvent(relay.url)
                    isLive = state?.currentState(relay.url) == ReqSubStatus.LIVE
                    forFilters = state?.lastKnownFilterStates(relay.url)
                }
                desiredSubListeners.get(msg.subId)?.onEvent(
                    event = msg.event,
                    isLive = isLive,
                    relay = relay.url,
                    forFilters = forFilters,
                )
            }

            is EoseMessage -> {
                var forFilters: List<Filter>? = null
                val cmd =
                    withStateLock {
                        val state = relayState.get(msg.subId)
                        state?.onEose(relay.url)
                        forFilters = state?.lastKnownFilterStates(relay.url)
                        // Decide (and pre-mark) the resend while still holding the
                        // lock, so a concurrent subscribe/unsubscribe on the app
                        // thread can't also decide to send a REQ for this sub.
                        decideCommandLocked(msg.subId, relay.url)
                    }
                desiredSubListeners.get(msg.subId)?.onEose(
                    relay = relay.url,
                    forFilters = forFilters,
                )

                // send a newer version when done
                if (cmd != null) {
                    relay.sendOrConnectAndSync(cmd)
                }
            }

            is ClosedMessage -> {
                var forFilters: List<Filter>? = null
                val cmd =
                    withStateLock {
                        val state = relayState.get(msg.subId)
                        state?.onClosed(relay.url)
                        forFilters = state?.lastKnownFilterStates(relay.url)
                        decideCommandLocked(msg.subId, relay.url)
                    }
                desiredSubListeners.get(msg.subId)?.onClosed(
                    message = msg.message,
                    relay = relay.url,
                    forFilters = forFilters,
                )

                // send a newer version when done, but don't send a close if just closed
                if (cmd != null && cmd !is CloseCmd) {
                    relay.sendOrConnectAndSync(cmd)
                }
            }
        }
    }

    /**
     * When the relay disconnects
     */
    fun onDisconnected(url: NormalizedRelayUrl) {
        withStateLock {
            relayState.forEach { subId, state ->
                state.disconnected(url)
            }
        }
    }

    fun syncState(
        relay: NormalizedRelayUrl,
        sync: (Command) -> Unit,
    ) {
        desiredSubs.forEach { subId, filters ->
            val filters = filters[relay]
            if (!filters.isNullOrEmpty()) {
                sync(ReqCmd(subId, filters))
            }
        }
    }

    /**
     * If cannot connect, closes subs
     */
    fun onCannotConnect(
        url: NormalizedRelayUrl,
        errorMessage: String,
    ) {
        // Snapshot the affected subs (and their last-known filters) under the
        // lock, then notify listeners outside it.
        val toNotify =
            withStateLock {
                val list = mutableListOf<Pair<String, List<Filter>?>>()
                relayState.forEach { subId, state ->
                    // These are all my subs.. need to figure out which relays have them
                    val subs = desiredSubs.get(subId)
                    if (subs != null && url in subs.keys) {
                        list.add(subId to state.lastKnownFilterStates(url))
                    }
                }
                list
            }

        toNotify.forEach { (subId, forFilters) ->
            desiredSubListeners.get(subId)?.onCannotConnect(
                relay = url,
                message = errorMessage,
                forFilters = forFilters,
            )
        }
    }

    fun sendToRelayIfChanged(
        subId: String,
        relaysToUpdate: Set<NormalizedRelayUrl>,
        sync: (NormalizedRelayUrl, Command) -> Unit,
    ) {
        relaysToUpdate.forEach { relay ->
            // Decide + pre-mark atomically under the lock, then send outside it.
            val cmd = withStateLock { decideCommandLocked(subId, relay) }
            if (cmd != null) {
                sync(relay, cmd)
            }
        }
    }

    /**
     * Decides which command (if any) must be sent to [relay] to bring it in line
     * with the desired filters for [subId], and — for a REQ — pre-marks the
     * subscription state as SENT before returning.
     *
     * Pre-marking is what makes the check-then-send atomic: a second thread that
     * runs this method for the same sub sees the SENT state (or the
     * already-updated filters) and declines to send a duplicate REQ. Two REQs on
     * one sub id race on the wire and produce duplicate EOSEs/events (or, if a
     * CLOSE interleaves, an empty result that silently truncates a paged
     * download) — that is the bug this guards against.
     *
     * MUST be called while holding [withStateLock].
     */
    private fun decideCommandLocked(
        subId: String,
        relay: NormalizedRelayUrl,
    ): Command? {
        val state = relayState.get(subId)
        val oldFilters = state?.currentFilters(relay)
        val newFilters = desiredSubs.get(subId)?.get(relay)

        return when {
            newFilters.isNullOrEmpty() -> {
                // some relays are not in this sub anymore. Stop their subscriptions
                // only if the old filters are not already closed.
                if (!oldFilters.isNullOrEmpty()) CloseCmd(subId) else null
            }

            oldFilters.isNullOrEmpty() || FiltersChanged.needsToResendRequest(oldFilters, newFilters) -> {
                // A REQ is warranted: a brand new sub, or the filters changed
                // enough (not just a `since` bump) to need a resend. But if a REQ
                // is already in flight, don't send another — multiple REQs on one
                // sub id trigger multiple EOSEs and we can no longer tell which
                // reply belongs to which REQ. The pending change is picked up
                // later by the EOSE handler, which runs this method again once the
                // sub reaches LIVE.
                val current = state?.currentState(relay)
                if (current == ReqSubStatus.SENT || current == ReqSubStatus.QUERYING_PAST) {
                    null
                } else {
                    // Pre-mark SENT + filters so a concurrent decider skips.
                    subState(subId).onOpenReq(relay, newFilters)
                    ReqCmd(subId, newFilters)
                }
            }

            else -> {
                // Filters are effectively the same; nothing to do.
                null
            }
        }
    }

    fun destroy() {
        relayState.clear()
        desiredSubs.clear()
        desiredSubListeners.clear()
        desiredRelays.tryEmit(emptySet())
    }
}
