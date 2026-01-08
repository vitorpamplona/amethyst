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
package com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * Manages Nostr subscriptions using a [NostrClient], allowing subscriptions to be created, modified,
 * and synchronized with relay filters. Subscriptions are stored in a cache and processed through
 * [updateRelays] to update relay filters dynamically. Also tracks event statistics and EOSE (End of
 * Stored Events) events, and provides utility methods to interact with subscriptions like dismissal.
 *
 * Key responsibilities:
 * 1. Maintain a cache of active [Subscription] instances.
 * 2. Handle client events (onEvent, onEOSE) using [clientListener].
 * 3. Synchronize relay filters via [updateRelays] when subscriptions change.
 * 4. Provide methods to create, dismiss, and inspect subscriptions.
 *
 * Usage:
 * - Use [requestNewSubscription] to create subscriptions.
 * - Modify filters on [Subscription] at will and call [updateRelays] to apply changes.
 * - Update filters based on EOSE callbacks on each subscription
 * - Dismiss subscriptions with [dismissSubscription].
 */
class SubscriptionController(
    val client: INostrClient,
) {
    private val subscriptions = LargeCache<String, Subscription>()

    fun getSub(subId: String) = subscriptions.get(subId)

    fun requestNewSubscription(
        subId: String,
        listener: IRequestListener,
    ): Subscription = Subscription(subId, listener).also { subscriptions.put(it.id, it) }

    fun dismissSubscription(subId: String) = getSub(subId)?.let { dismissSubscription(it) }

    fun dismissSubscription(subscription: Subscription) {
        subscription.reset()
        subscriptions.remove(subscription.id)
        client.close(subscription.id)
    }

    fun updateRelays() {
        val currentFilters =
            subscriptions.associateWith { id, sub ->
                client.getReqFiltersOrNull(id)
            }

        subscriptions.forEach { id, sub ->
            updateRelaysIfNeeded(id, sub.listener, sub.filters(), currentFilters[id])
        }
    }

    fun updateRelaysIfNeeded(
        subId: String,
        listener: IRequestListener,
        newFilters: Map<NormalizedRelayUrl, List<Filter>>?,
        oldFilters: Map<NormalizedRelayUrl, List<Filter>>?,
    ) {
        if (oldFilters != null) {
            if (newFilters == null) {
                // was active and is not active anymore, just close.
                client.close(subId)
            } else {
                client.openReqSubscription(subId, newFilters, listener)
            }
        } else {
            if (newFilters == null) {
                // was not active and is still not active, does nothing
            } else {
                // was not active and becomes active, sends the entire filter.
                client.openReqSubscription(subId, newFilters, listener)
            }
        }
    }
}
