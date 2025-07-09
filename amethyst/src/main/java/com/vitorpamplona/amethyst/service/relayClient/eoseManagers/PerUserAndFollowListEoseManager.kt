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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relays.EOSEAccount
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.collections.distinctBy

/**
 * This query type creates a new relay subscription for every logged-in
 * user AND each follow list they select in the top of the screen.
 *
 * It is ideal for the home, video, discovery and notification screens
 * since they all have lists to choose from.
 *
 * This class keeps EOSEs for each user & list for as long as possible and
 * does NOT share EOSEs with other users. Changing the list will not make the
 * app reuse the EOSE because it assumes the filter is going to be different
 */
abstract class PerUserAndFollowListEoseManager<T>(
    client: NostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    private val latestEOSEs = EOSEAccount()
    private val userSubscriptionMap = mutableMapOf<User, String>()

    fun since(key: T) = latestEOSEs.since(user(key), list(key))

    fun newEose(
        key: T,
        relay: NormalizedRelayUrl,
        time: Long,
    ) = latestEOSEs.newEose(user(key), list(key), relay, time)

    open fun newSub(key: T): Subscription =
        requestNewSubscription { time, relayUrl ->
            newEose(key, relayUrl, time)
            if (invalidateAfterEose) {
                invalidateFilters()
            }
        }

    open fun endSub(
        key: User,
        subId: String,
    ) {
        dismissSubscription(subId)
        userSubscriptionMap.remove(key)
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
        val uniqueSubscribedAccounts = keys.distinctBy { user(it) }

        val updated = mutableSetOf<User>()

        uniqueSubscribedAccounts.forEach {
            val user = user(it)
            val sub = findOrCreateSubFor(it)
            val newFilters = updateFilter(it, since(it))?.ifEmpty { null }
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

    abstract fun list(key: T): String
}
