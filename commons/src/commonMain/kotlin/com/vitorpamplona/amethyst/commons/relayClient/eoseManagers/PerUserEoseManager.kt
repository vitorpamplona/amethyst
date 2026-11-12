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
package com.vitorpamplona.amethyst.commons.relayClient.eoseManagers

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.AccountScopedQuery
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.attributedTo
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * This query type creates a new relay subscription for every distinct
 * user that is subscribed into the keys. It is ideal for screens that
 * CANNOT be shared among multiple logged-in users.
 *
 * The cursors live on the holders the keys name ([cursorHoldersOf]): what holds the notes the
 * subscription loads. A key that names none is asked from scratch.
 */
abstract class PerUserEoseManager<T>(
    client: INostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    private val userSubscriptionMap = mutableMapOf<User, String>()

    open fun newEose(
        key: T,
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>? = null,
    ) {
        if (invalidateAfterEose) {
            invalidateFilters()
        }
    }

    open fun newSub(key: T): Subscription =
        requestNewSubscription(
            object : SubscriptionListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(key, relay, TimeUtils.now(), forFilters)
                }

                override suspend fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(key, relay, TimeUtils.now(), forFilters)
                    }
                }
            },
        )

    open fun endSub(
        key: User,
        subId: String,
    ) {
        dismissSubscription(subId)
        userSubscriptionMap.remove(key)
        unwatchChatFeeds(key)
    }

    fun findOrCreateSubFor(key: T): Subscription {
        val user = user(key)
        val subId = userSubscriptionMap[user]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[user] = it.id }
        } else {
            getSubscription(subId) ?: newSub(key).also { userSubscriptionMap[user] = it.id }
        }
    }

    override fun updateSubscriptions(keys: Set<T>) {
        val keysByUser = keys.groupBy { user(it) }

        val updated = mutableSetOf<User>()

        keysByUser.forEach { (user, userKeys) ->
            val it = userKeys.first()
            watchChatFeeds(user, it)
            // Every key of this user shares the subscription, so every holder they name gets its answers.
            val (filters, cursors) = filtersFor(userKeys) { s -> updateFilter(it, s) }
            val newFilters =
                filters
                    ?.ifEmpty { null }
                    // Attribute to the account that owns this subscription, once, here — rather than
                    // threading a pubkey through every filter builder underneath. Builders that already
                    // know their account keep what they set.
                    ?.let { f -> accountPubKeyOf(it)?.let { pk -> f.attributedTo(pk) } ?: f }

            val sub = findOrCreateSubFor(it)
            serve(sub.id, cursors)
            sub.updateFilters(newFilters?.groupByRelay())

            updated.add(user)
        }

        userSubscriptionMap.filter { it.key !in updated }.forEach {
            endSub(it.key, it.value)
        }
    }

    abstract fun updateFilter(
        key: T,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter>?

    abstract fun user(key: T): User

    /**
     * The account behind [key], when the key is account-scoped. Null for keys about other users.
     *
     * Keyed on [AccountScopedQuery] rather than a concrete query-state type: the home feed uses
     * HomeQueryState, notifications use AccountQueryState, and checking one concrete class filed the
     * other under "not attributed" despite both being built from a single account's data.
     */
    private fun accountPubKeyOf(key: Any?): String? = (key as? AccountScopedQuery)?.account?.userProfile()?.pubkeyHex
}
