/**
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

import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.ReqSubStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.RequestSubscriptionState
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
    private val desiredSubListeners = LargeCache<String, IRequestListener>()
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
        listener: IRequestListener?,
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
        relayState.forEach { subId, state ->
            state.connecting(url)
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
                subState(cmd.subId).onOpenReq(relay, cmd.filters)
                desiredSubListeners.get(cmd.subId)?.onStartReq(
                    relay = relay.url,
                    forFilters = cmd.filters,
                )
            }
            is CloseCmd -> {
                subState(cmd.subId).onCloseReq(relay)
                desiredSubListeners.get(cmd.subId)?.onCloseReq(
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
                val state = relayState.get(msg.subId)
                state?.onNewEvent(relay.url)
                desiredSubListeners.get(msg.subId)?.onEvent(
                    event = msg.event,
                    isLive = state?.currentState(relay.url) == ReqSubStatus.LIVE,
                    relay = relay.url,
                    forFilters = state?.lastKnownFilterStates(relay.url),
                )
            }
            is EoseMessage -> {
                val state = relayState.get(msg.subId)
                state?.onEose(relay.url)
                desiredSubListeners.get(msg.subId)?.onEose(
                    relay = relay.url,
                    forFilters = state?.lastKnownFilterStates(relay.url),
                )

                // send a newer version when done
                sendToRelayIfChanged(msg.subId, relay.url) { cmd ->
                    relay.sendOrConnectAndSync(cmd)
                }
            }
            is ClosedMessage -> {
                val state = relayState.get(msg.subId)
                state?.onClosed(relay.url)

                desiredSubListeners.get(msg.subId)?.onClosed(
                    message = msg.message,
                    relay = relay.url,
                    forFilters = state?.lastKnownFilterStates(relay.url),
                )

                // send a newer version when done
                sendToRelayIfChanged(msg.subId, relay.url) { cmd ->
                    // don't send a close if just closed
                    if (cmd !is CloseCmd) {
                        relay.sendOrConnectAndSync(cmd)
                    }
                }
            }
        }
    }

    /**
     * When the relay disconnects
     */
    fun onDisconnected(url: NormalizedRelayUrl) {
        relayState.forEach { subId, state ->
            state.disconnected(url)
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
            } else {
                null
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
        relayState.forEach { subId, state ->
            desiredSubListeners.get(subId)?.onCannotConnect(
                message = errorMessage,
                relay = url,
                forFilters = state.lastKnownFilterStates(url),
            )
        }
    }

    fun sendToRelayIfChanged(
        subId: String,
        relaysToUpdate: Set<NormalizedRelayUrl>,
        sync: (NormalizedRelayUrl, Command) -> Unit,
    ) {
        relaysToUpdate.forEach { relay ->
            sendToRelayIfChanged(subId, relay) { cmd ->
                if (cmd is ReqCmd) {
                    val currentState = relayState.get(subId)?.currentState(relay)

                    if (currentState == ReqSubStatus.SENT || currentState == ReqSubStatus.QUERYING_PAST) {
                        // sending multiple REQs triggers multiple EOSEs back and we then don't know which
                        // one is which.
                    } else {
                        sync(relay, cmd)
                    }
                } else {
                    sync(relay, cmd)
                }
            }
        }
    }

    fun sendToRelayIfChanged(
        subId: String,
        relay: NormalizedRelayUrl,
        sync: (Command) -> Unit,
    ) {
        val state = relayState.get(subId)
        val oldFilters = state?.currentFilters(relay)
        val newFilters = desiredSubs.get(subId)?.get(relay)
        sendToRelayIfChanged(subId, oldFilters, newFilters, sync)
    }

    fun sendToRelayIfChanged(
        subId: String,
        oldFilters: List<Filter>?,
        newFilters: List<Filter>?,
        sync: (Command) -> Unit,
    ) {
        if (newFilters.isNullOrEmpty()) {
            // some relays are not in this sub anymore. Stop their subscriptions
            if (!oldFilters.isNullOrEmpty()) {
                // only update if the old filters are not already closed.
                sync(CloseCmd(subId))
            }
        } else if (oldFilters.isNullOrEmpty()) {
            // new relays were added. Start a new sub in them
            sync(ReqCmd(subId, newFilters))
        } else if (FiltersChanged.needsToResendRequest(oldFilters, newFilters)) {
            // filters were changed enough (not only an update in since) to warn a new update
            sync(ReqCmd(subId, newFilters))
        } else {
            // They are the same don't do anything.
        }
    }
}
