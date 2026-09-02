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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.metadata

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterBasicAccountInfoFromKeys
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterBookmarksAndReportsFromKey
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterFollowsAndMutesFromKey
import com.vitorpamplona.amethyst.commons.relayClient.account.metadata.filterLastPostsFromKey
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.MergedAuthorTracker
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Each account's own profile, lists and recent posts — for **every** logged-in account, in one
 * subscription.
 *
 * Every filter here is `authors`-keyed, so a relay that several accounts read from can be asked
 * about all of them at once by widening `authors` rather than opening a REQ per account. This was
 * the largest single contributor to blowing a relay's `max_subscriptions`: seven filters in a
 * subscription, repeated once per account.
 *
 * The per-account `limit`s are summed rather than shared. These are mostly replaceable events, so
 * the limit is a safety bound rather than a page size, and scaling it by the number of accounts
 * keeps each one exactly the headroom it had alone.
 */
class AccountMetadataEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : SingleSubEoseManager<AccountQueryState>(client, allKeys) {
    override fun distinct(key: AccountQueryState) = key.account.userProfile()

    fun relayFlow(query: AccountQueryState) = query.account.homeRelays.flow

    override fun updateFilter(
        keys: List<AccountQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        val accountsPerRelay = mutableMapOf<NormalizedRelayUrl, MutableList<AccountQueryState>>()
        keys.forEach { key ->
            relayFlow(key).value.forEach { relay ->
                accountsPerRelay.getOrPut(relay) { mutableListOf() }.add(key)
            }
        }

        return accountsPerRelay.flatMap { (relay, accounts) ->
            val pubkeys = accounts.map { it.account.userProfile().pubkeyHex }

            // An account joining a relay this subscription already covers must not inherit the cursor
            // the earlier accounts earned — it would never ask for its own profile, follows or lists.
            // This matters more here than for notifications: there is no backward pager to rescue it,
            // so the account would go without until the next launch cleared the in-memory cursor.
            // Drop the stored cursor AND ignore it for this pass, so the refetch does not depend on
            // `since` being a live view of the map we just mutated.
            val gained = authorsPerRelay.gainedAuthors(relay, pubkeys)
            if (gained) clearEoseFor(relay)

            val relaySince = if (gained) null else since?.get(relay)?.time

            // The account-switcher avatars: other logged-in accounts this screen wants to name.
            // Screens supply them; the background registry does not, so this is usually empty.
            val otherAccounts = accounts.flatMapTo(mutableSetOf()) { it.otherAccounts }.minus(pubkeys.toSet())

            listOf(
                filterAccountInfoAndListsFromKey(relay, pubkeys, relaySince),
                filterFollowsAndMutesFromKey(relay, pubkeys, relaySince),
                filterBookmarksAndReportsFromKey(relay, pubkeys, relaySince),
                filterLastPostsFromKey(relay, pubkeys, relaySince ?: TimeUtils.oneMonthAgo()),
                filterBasicAccountInfoFromKeys(relay, otherAccounts.toList(), relaySince, pubkeys),
            ).flatten()
        }
    }

    private val authorsPerRelay = MergedAuthorTracker()

    /** Per-account relay watchers, reconciled as accounts come and go. See the notifications manager. */
    private val userJobMap = mutableMapOf<User, List<Job>>()

    override fun updateSubscriptions(keys: Set<AccountQueryState>) {
        val wanted = keys.associateBy { it.account.userProfile() }

        (userJobMap.keys - wanted.keys).toList().forEach { user ->
            userJobMap.remove(user)?.forEach { it.cancel() }
        }

        wanted.forEach { (user, key) ->
            if (user !in userJobMap) {
                userJobMap[user] =
                    listOf(
                        key.account.scope.launch(Dispatchers.IO) {
                            relayFlow(key).collectLatest { invalidateFilters() }
                        },
                    )
            }
        }

        super.updateSubscriptions(keys)
    }

    override fun destroy() {
        authorsPerRelay.clear()
        userJobMap.values.forEach { jobs -> jobs.forEach { it.cancel() } }
        userJobMap.clear()
        super.destroy()
    }
}
