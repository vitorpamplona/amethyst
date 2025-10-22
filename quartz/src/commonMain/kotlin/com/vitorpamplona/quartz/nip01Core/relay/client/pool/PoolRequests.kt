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

class PoolRequests {
    /**
     * Desired subs and listeners are changed immediately when the local code requests
     */
    private val desiredSubs = LargeCache<String, Map<NormalizedRelayUrl, List<Filter>>>()
    private val desiredSubListeners = LargeCache<String, IRequestListener>()
    val desiredRelays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    /**
     * relay states are kept and only removed after everything is processed.
     */
    private val relayState = LargeCache<String, RequestSubscriptionState<NormalizedRelayUrl>>()

    fun subState(subId: String): RequestSubscriptionState<NormalizedRelayUrl> = relayState.getOrCreate(subId) { RequestSubscriptionState() }

    private fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        desiredSubs.forEach { sub, perRelayFilters ->
            myRelays.addAll(perRelayFilters.keys)
        }

        if (desiredRelays.value != myRelays) {
            desiredRelays.tryEmit(myRelays)
        }
    }

    fun activeFiltersFor(url: NormalizedRelayUrl): Map<String, List<Filter>> {
        val myRelays = mutableMapOf<String, List<Filter>>()
        desiredSubs.forEach { sub, perRelayFilters ->
            val filters = perRelayFilters[url]
            if (filters != null) {
                myRelays.put(sub, filters)
            }
        }
        return myRelays
    }

    /**
     * Adds a new filter to the pool, and returns the relays that need to be updated.
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
     * Removes the sub from the pool, and returns the relays that need to be updated.
     */
    fun remove(subId: String): Set<NormalizedRelayUrl> =
        if (desiredSubs.containsKey(subId)) {
            // saves old relays
            val oldRelays = desiredSubs.get(subId)?.keys ?: emptySet()
            // update filters
            desiredSubs.remove(subId)
            // update listener
            desiredSubListeners.remove(subId)
            // remove states
            relayState.remove(subId)
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
    fun onConnecting(url: NormalizedRelayUrl) {
        relayState.forEach { subId, state ->
            state.connecting(url)
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

    fun onSent(
        relay: NormalizedRelayUrl,
        cmd: Command,
    ) {
        when (cmd) {
            is ReqCmd -> subState(cmd.subId).onOpenReq(relay, cmd.filters)
            is CloseCmd -> subState(cmd.subId).onCloseReq(relay)
        }
    }

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
                    forFilters = state?.currentFilters(relay.url),
                )
            }
            is EoseMessage -> {
                val state = relayState.get(msg.subId)
                state?.onEose(relay.url)
                desiredSubListeners.get(msg.subId)?.onEose(
                    relay = relay.url,
                    forFilters = state?.currentFilters(relay.url),
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
                    forFilters = state?.currentFilters(relay.url),
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

    fun onDisconnected(url: NormalizedRelayUrl) {
        relayState.forEach { subId, state ->
            state.disconnected(url)
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
                forFilters = state.currentFilters(url),
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
        val oldFilters = relayState.get(subId)?.currentFilters(relay)
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
