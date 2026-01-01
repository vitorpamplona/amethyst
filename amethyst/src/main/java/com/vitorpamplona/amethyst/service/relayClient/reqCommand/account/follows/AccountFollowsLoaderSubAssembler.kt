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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.follows

import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.BundledUpdate
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.IEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.EOSEAccountFast
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.SubscriptionController
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * This downloads the outbox events for all Follows of all users.
 * It needs to be super fast on startup.
 */
class AccountFollowsLoaderSubAssembler(
    val client: INostrClient,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val authStatus: IAuthStatus,
    val failureTracker: RelayOfflineTracker,
    val allKeys: () -> Set<AccountQueryState>,
) : IEoseManager {
    private val logTag = "AccountFollowsLoaderSubAssembler"
    private val orchestrator = SubscriptionController(client)

    // Refreshes observers in batches of 500ms
    private val bundler = BundledUpdate(500, Dispatchers.IO)

    /**
     * This assembler saves the EOSE per user key. That EOSE includes their metadata, etc
     * and reports, but only from trusted accounts (follows of all logged in users).
     */
    val hasTried: EOSEAccountFast<User> = EOSEAccountFast<User>(2000)

    // updates all filters
    override fun invalidateFilters(ignoreIfDoing: Boolean) {
        bundler.invalidate(ignoreIfDoing) {
            updateSubscriptions(allKeys())
            orchestrator.updateRelays()
        }
    }

    override fun destroy() {
        orchestrator.dismissSubscription(sub.id)
        bundler.cancel()
    }

    fun newEose(
        time: Long,
        relayUrl: NormalizedRelayUrl,
        filters: List<Filter>?,
    ) {
        filters?.forEach { filter ->
            filter.authors?.forEach { pubkey ->
                cache.getUserIfExists(pubkey)?.let { user ->
                    hasTried.newEose(user, relayUrl, time)
                }
            }
        }

        invalidateFilters(true)
    }

    val sub =
        orchestrator.requestNewSubscription(
            if (isDebug) logTag + newSubId() else newSubId(),
            object : IRequestListener {
                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    newEose(TimeUtils.now(), relay, forFilters)
                }

                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    if (isLive) {
                        newEose(TimeUtils.now(), relay, forFilters)
                    }
                }

                override fun onClosed(
                    message: String,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    // If the relay doesn't want to provide data (and it is not because of auth), cancel REQ
                    if (
                        !message.startsWith("auth") || authStatus.hasFinishedAuthentication(relay)
                    ) {
                        newEose(TimeUtils.now(), relay, forFilters)
                    }
                }
            },
        )

    fun updateFilterForAllAccounts(accounts: Collection<Account>): List<RelayBasedFilter>? {
        val users = mutableSetOf<User>()
        accounts.forEach { key ->
            key.kind3FollowList.userList.value.forEach { user ->
                if (user.authorRelayList() == null) {
                    users.add(user)
                }
            }
        }

        if (users.isEmpty()) return null

        println("AccountFollowNeeds ${users.size}")

        val connectedRelays = client.connectedRelaysFlow().value

        val perRelay = pickRelaysToLoadUsers(users, accounts, connectedRelays, failureTracker.cannotConnectRelays, hasTried)

        hasTried.removeEveryoneBut(users)

        return perRelay.mapNotNull { (relay, users) ->
            if (users.isNotEmpty()) {
                RelayBasedFilter(
                    relay = relay,
                    filter = Filter(kinds = listOf(AdvertisedRelayListEvent.KIND), authors = users.sorted()),
                )
            } else {
                null
            }
        }
    }

    fun updateSubscriptions(keys: Set<AccountQueryState>) {
        val uniqueSubscribedAccounts = keys.associate { it.account.userProfile() to it.account }

        val allFilters = updateFilterForAllAccounts(uniqueSubscribedAccounts.values)
        sub.updateFilters(allFilters?.groupByRelay())

        // adds new subscriptions
        uniqueSubscribedAccounts.forEach {
            if (it.value.userProfile() !in accountUpdatesJobMap.keys) {
                newWatcher(it.value.userProfile(), it.value.kind3FollowList.userList)
            }
        }

        // removes accounts that are not being subscribed anymore.
        // Cancel watchers for accounts no longer observed using a snapshot to avoid CME
        accountUpdatesJobMap.keys
            .toList()
            .filter { it !in uniqueSubscribedAccounts.keys }
            .forEach { endWatcher(it) }
    }

    private val accountUpdatesJobMap = mutableMapOf<User, Job>()

    @OptIn(FlowPreview::class)
    fun newWatcher(
        user: User,
        followList: Flow<List<User>>,
    ) {
        accountUpdatesJobMap[user]?.cancel()
        accountUpdatesJobMap[user] =
            scope.launch(Dispatchers.IO) {
                followList.sample(1000).collectLatest {
                    invalidateFilters(true)
                }
            }
    }

    fun endWatcher(key: User) {
        accountUpdatesJobMap[key]?.cancel()
        accountUpdatesJobMap.remove(key)
    }
}
