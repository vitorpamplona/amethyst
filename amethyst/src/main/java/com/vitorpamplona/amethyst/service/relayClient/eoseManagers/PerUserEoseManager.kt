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
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import kotlin.collections.distinctBy

/**
 * This query type creates a new relay subscription for every distinct
 * user that is subscribed into the keys. It is ideal for screens that
 * CANNOT be shared among multiple logged-in users.
 *
 * This class keeps EOSEs for each user for as long as possible and
 * does NOT share EOSEs with other users. The EOSEs are kept even when
 * the subscription disappears and comes back later.
 */
abstract class PerUserEoseManager<T>(
    client: NostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    private val latestEOSEs = EOSEAccountFast()
    private val userSubscriptionMap = mutableMapOf<User, String>()

    fun since(key: T) = latestEOSEs.since(user(key))

    fun newEose(
        key: T,
        relayUrl: String,
        time: Long,
    ) = latestEOSEs.newEose(user(key), relayUrl, time)

    open fun newSub(key: T): Subscription =
        orchestrator.requestNewSubscription { time, relayUrl ->
            newEose(key, relayUrl, time)
            if (invalidateAfterEose) {
                invalidateFilters()
            }
        }

    open fun endSub(
        key: User,
        subId: String,
    ) {
        orchestrator.dismissSubscription(subId)
    }

    fun findOrCreateSubFor(key: T): Subscription {
        val user = user(key)
        var subId = userSubscriptionMap[user]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[user] = it.id }
        } else {
            orchestrator.getSub(subId) ?: newSub(key).also { userSubscriptionMap[user] = it.id }
        }
    }

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueSubscribedAccounts = keys.distinctBy { user(it) }

        val updated = mutableSetOf<User>()

        uniqueSubscribedAccounts.forEach {
            val user = user(it)
            findOrCreateSubFor(it).typedFilters = updateFilter(it, since(it))?.ifEmpty { null }

            updated.add(user)
        }

        userSubscriptionMap.forEach {
            if (it.key !in updated) {
                endSub(it.key, it.value)
            }
        }
    }

    abstract fun updateFilter(
        key: T,
        since: Map<String, EOSETime>?,
    ): List<TypedFilter>?

    abstract fun user(key: T): User
}
