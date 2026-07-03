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

/**
 * Manages relay subscriptions for the entire pool in a way that only
 * sends new states to the relay if they have changed.
 *
 * This code also awaits a subscription to come to EOSE since many relays
 * have through switching subs while they are processing the past.
 */
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

    /*
     * Locking model: every compound access to a subscription's state machine
     * ([RequestSubscriptionState]) — including the check-then-send decision in
     * [decideCommandLocked] — runs inside THAT subscription's own lock
     * ([RequestSubscriptionState.withLock]).
     *
     * A single subscription can span many relays, and each relay's
     * socket-reader thread delivers messages into this class concurrently
     * while the app thread adds/removes subscriptions — so the plain maps
     * inside [RequestSubscriptionState] are written from several threads at
     * once. That is both a memory hazard (concurrent map mutation) and a
     * logic hazard: two threads must never both observe "no REQ in flight"
     * and both send a REQ for the same sub id.
     *
     * The lock is per subscription, not global, because different subIds
     * share no wire state: EVENT frames for different subs coming from
     * different relay consumer threads must not serialize on each other (a
     * global lock here measured negative scaling under 4 concurrent relay
     * feeders). Listener callbacks and the actual socket sends are ALWAYS
     * performed outside the lock — they re-enter this class through
     * [onSent], so holding the lock across them would self-deadlock, and the
     * lock is non-reentrant. Never hold two subscriptions' locks at once.
     */

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
        // Change states to connecting. One sub's lock at a time.
        relayState.forEach { subId, state ->
            state.withLock { state.connecting(url) }
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
                subState(cmd.subId).let { state ->
                    state.withLock { state.onOpenReq(relay, cmd.filters) }
                }
                desiredSubListeners.get(cmd.subId)?.onSubscriptionStarted(
                    relay = relay.url,
                    forFilters = cmd.filters,
                )
            }

            is CloseCmd -> {
                subState(cmd.subId).let { state ->
                    state.withLock { state.onSubscriptionClosed(relay) }
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
                relayState.get(msg.subId)?.let { state ->
                    state.withLock {
                        state.onNewEvent(relay.url)
                        isLive = state.currentState(relay.url) == ReqSubStatus.LIVE
                        forFilters = state.lastKnownFilterStates(relay.url)
                    }
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
                    relayState.get(msg.subId)?.let { state ->
                        state.withLock {
                            state.onEose(relay.url)
                            forFilters = state.lastKnownFilterStates(relay.url)
                            // Decide (and pre-mark) the resend while still holding the
                            // lock, so a concurrent subscribe/unsubscribe on the app
                            // thread can't also decide to send a REQ for this sub.
                            decideCommandLocked(state, msg.subId, relay.url)
                        }
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
                    relayState.get(msg.subId)?.let { state ->
                        state.withLock {
                            state.onClosed(relay.url)
                            forFilters = state.lastKnownFilterStates(relay.url)
                            decideCommandLocked(state, msg.subId, relay.url)
                        }
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
        relayState.forEach { subId, state ->
            state.withLock { state.disconnected(url) }
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
        // Snapshot the affected subs (and their last-known filters) under each
        // sub's own lock, then notify listeners outside it.
        val toNotify = mutableListOf<Pair<String, List<Filter>?>>()
        relayState.forEach { subId, state ->
            // These are all my subs.. need to figure out which relays have them
            val subs = desiredSubs.get(subId)
            if (subs != null && url in subs.keys) {
                toNotify.add(subId to state.withLock { state.lastKnownFilterStates(url) })
            }
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
        val state = subState(subId)
        relaysToUpdate.forEach { relay ->
            // Decide + pre-mark atomically under the sub's lock, then send outside it.
            val cmd = state.withLock { decideCommandLocked(state, subId, relay) }
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
     * MUST be called while holding [state]'s lock ([RequestSubscriptionState.withLock]);
     * [state] must be [subId]'s state instance. Takes the instance instead of
     * re-resolving it so it never re-enters the (non-reentrant) lock.
     */
    private fun decideCommandLocked(
        state: RequestSubscriptionState<NormalizedRelayUrl>,
        subId: String,
        relay: NormalizedRelayUrl,
    ): Command? {
        val oldFilters = state.currentFilters(relay)
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
                val current = state.currentState(relay)
                if (current == ReqSubStatus.SENT || current == ReqSubStatus.QUERYING_PAST) {
                    null
                } else {
                    // Pre-mark SENT + filters so a concurrent decider skips.
                    state.onOpenReq(relay, newFilters)
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
