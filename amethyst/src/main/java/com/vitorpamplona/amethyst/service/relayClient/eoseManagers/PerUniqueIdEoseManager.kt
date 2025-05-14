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

import com.vitorpamplona.amethyst.service.relays.EOSEFollowList
import com.vitorpamplona.ammolite.relays.NostrClient
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.datasources.Subscription
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import kotlin.collections.distinctBy

/**
 * This query type creates a new relay subscription for every SubID.id()
 * that is subscribed into the keys. It is ideal for screen loading that
 * can be shared among multiple logged-in users.
 *
 * This class keeps EOSEs for each SubID.id() for as long as possible and
 * shares all EOSEs among all users.
 */
abstract class PerUniqueIdEoseManager<T>(
    client: NostrClient,
    allKeys: () -> Set<T>,
    val invalidateAfterEose: Boolean = false,
) : BaseEoseManager<T>(client, allKeys) {
    // long term EOSE cache
    private val latestEOSEs = EOSEFollowList()

    // map between each query Id and each subscription id
    private val userSubscriptionMap = mutableMapOf<String, String>()

    fun since(key: T) = latestEOSEs.since(id(key))

    fun newEose(
        key: T,
        relayUrl: String,
        time: Long,
    ) {
        latestEOSEs.newEose(id(key), relayUrl, time)
        if (invalidateAfterEose) {
            invalidateFilters()
        }
    }

    open fun newSub(key: T): Subscription =
        orchestrator.requestNewSubscription { time, relayUrl ->
            newEose(key, relayUrl, time)
        }

    open fun endSub(
        key: String,
        subId: String,
    ) {
        orchestrator.dismissSubscription(subId)
    }

    fun findOrCreateSubFor(key: T): Subscription {
        val id = id(key)
        var subId = userSubscriptionMap[id]
        return if (subId == null) {
            newSub(key).also { userSubscriptionMap[id] = it.id }
        } else {
            orchestrator.getSub(subId) ?: newSub(key).also { userSubscriptionMap[id] = it.id }
        }
    }

    override fun updateSubscriptions(keys: Set<T>) {
        val uniqueSubscribedAccounts = keys.distinctBy { id(it) }

        val updated = mutableSetOf<String>()

        uniqueSubscribedAccounts.forEach {
            val mainKey = id(it)
            findOrCreateSubFor(it).typedFilters = updateFilter(it, since(it))?.ifEmpty { null }

            updated.add(mainKey)
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

    abstract fun id(key: T): String
}
