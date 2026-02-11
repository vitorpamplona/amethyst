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

import com.vitorpamplona.quartz.nip01Core.relay.client.counts.CountQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.counts.CountQueryStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow

class PoolCounts {
    private var queries = LargeCache<String, Map<NormalizedRelayUrl, List<Filter>>>()
    val relays = MutableStateFlow(setOf<NormalizedRelayUrl>())

    private val relayState = LargeCache<String, CountQueryState<NormalizedRelayUrl>>()

    fun subState(subId: String): CountQueryState<NormalizedRelayUrl> = relayState.getOrCreate(subId) { CountQueryState() }

    private fun updateRelays() {
        val myRelays = mutableSetOf<NormalizedRelayUrl>()
        queries.forEach { queryId, perRelayFilters ->
            myRelays.addAll(perRelayFilters.keys)
        }

        if (relays.value != myRelays) {
            relays.tryEmit(myRelays)
        }
    }

    fun activeFiltersFor(url: NormalizedRelayUrl): Map<String, List<Filter>> {
        val myRelays = mutableMapOf<String, List<Filter>>()
        queries.forEach { sub, perRelayFilters ->
            val filters = perRelayFilters.get(url)
            if (filters != null) {
                myRelays.put(sub, filters)
            }
        }
        return myRelays
    }

    /**
     * Adds a new query to the pool, and returns the relays that need to be updated.
     */
    fun addOrUpdate(
        subscriptionId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
    ): Set<NormalizedRelayUrl> {
        val oldRelays = queries.get(subscriptionId)?.keys ?: emptySet()
        queries.put(subscriptionId, filters)
        updateRelays()
        return oldRelays + filters.keys
    }

    /**
     * Removes the query from the pool, and returns the relays that need to be updated.
     */
    fun remove(queryId: String): Set<NormalizedRelayUrl> =
        if (queries.containsKey(queryId)) {
            val oldRelays = queries.get(queryId)?.keys ?: emptySet()
            queries.remove(queryId)
            updateRelays()
            oldRelays
        } else {
            emptySet()
        }

    fun getSubscriptionFiltersOrNull(queryId: String): Map<NormalizedRelayUrl, List<Filter>>? = queries.get(queryId)

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
        queries.forEach { subId, filters ->
            val filters = filters[relay]
            if (!filters.isNullOrEmpty()) {
                sync(CountCmd(subId, filters))
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
            is CountCmd -> subState(cmd.queryId).onQuery(relay, cmd.filters)
            is CloseCmd -> subState(cmd.subId).onCloseQuery(relay)
        }
    }

    fun onIncomingMessage(
        relay: IRelayClient,
        msg: Message,
    ) {
        when (msg) {
            is CountMessage -> {
                subState(msg.queryId).onCountReply(relay.url)
                sendToRelayIfChanged(msg.queryId, relay.url) { cmd ->
                    relay.sendOrConnectAndSync(cmd)
                }
            }

            is ClosedMessage -> {
                subState(msg.subId).onClosed(relay.url)
                sendToRelayIfChanged(msg.subId, relay.url) { cmd ->
                    relay.sendOrConnectAndSync(cmd)
                }
            }
        }
    }

    fun onDisconnected(url: NormalizedRelayUrl) {
        relayState.forEach { subId, state ->
            state.disconnected(url)
        }
    }

    fun sendToRelayIfChanged(
        queryId: String,
        relaysToUpdate: Set<NormalizedRelayUrl>,
        sync: (NormalizedRelayUrl, Command) -> Unit,
    ) {
        relaysToUpdate.forEach { relay ->
            val currentState = relayState.get(queryId)?.currentState(relay)

            if (currentState == CountQueryStatus.SENT) {
                // sending multiple REQs triggers multiple EOSEs back and we then don't know which
                // one is which.
            } else {
                sendToRelayIfChanged(queryId, relay) { cmd ->
                    sync(relay, cmd)
                }
            }
        }
    }

    fun sendToRelayIfChanged(
        queryId: String,
        relay: NormalizedRelayUrl,
        sync: (Command) -> Unit,
    ) {
        val oldFilters = relayState.get(queryId)?.currentFilters(relay)
        val newFilters = queries.get(queryId)?.get(relay)
        sendToRelayIfChanged(queryId, oldFilters, newFilters, sync)
    }

    fun sendToRelayIfChanged(
        queryId: String,
        oldFilters: List<Filter>?,
        newFilters: List<Filter>?,
        sync: (Command) -> Unit,
    ) {
        if (newFilters.isNullOrEmpty()) {
            // some relays are not in this sub anymore. Stop their subscriptions
            if (!oldFilters.isNullOrEmpty()) {
                // only update if the old filters are not already closed.
                sync(CloseCmd(queryId))
            }
        } else if (oldFilters.isNullOrEmpty()) {
            // new relays were added. Start a new sub in them
            sync(CountCmd(queryId, newFilters))
        } else if (FiltersChanged.needsToResendRequest(oldFilters, newFilters)) {
            // filters were changed enough (not only an update in since) to warn a new update
            sync(CountCmd(queryId, newFilters))
        } else {
            // They are the same don't do anything.
        }
    }

    /**
     * If cannot connect, closes subs
     */
    fun onCannotConnect(
        relay: NormalizedRelayUrl,
        errorMessage: String,
    ) {
        // mark as impossible to get count from this relay
    }
}
