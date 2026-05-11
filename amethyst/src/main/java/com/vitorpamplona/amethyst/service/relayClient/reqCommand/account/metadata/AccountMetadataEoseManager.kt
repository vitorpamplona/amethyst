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

import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal fun preferredMetadataRelays(
    homeRelays: Set<NormalizedRelayUrl>,
    outboxRelays: Set<NormalizedRelayUrl>,
): Set<NormalizedRelayUrl> = outboxRelays.ifEmpty { homeRelays }

class AccountMetadataEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> =
        preferredMetadataRelays(
            key.account.homeRelays.flow.value,
            key.account.outboxRelays.flow.value,
        ).flatMap {
            val since = since?.get(it)?.time
            listOf(
                filterAccountInfoAndListsFromKey(it, user(key).pubkeyHex, since),
                filterFollowsAndMutesFromKey(it, user(key).pubkeyHex, since),
                filterBookmarksAndReportsFromKey(it, user(key).pubkeyHex, since),
                filterLastPostsFromKey(it, user(key).pubkeyHex, since ?: TimeUtils.oneMonthAgo()),
                filterBasicAccountInfoFromKeys(it, key.otherAccounts.minus(key.account.userProfile().pubkeyHex).toList(), since),
            ).flatten()
        }

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    combine(
                        key.account.homeRelays.flow,
                        key.account.outboxRelays.flow,
                        ::preferredMetadataRelays,
                    ).collectLatest {
                        // External clients can publish authored replaceables to relays that
                        // are writable for the account but are not part of the narrower home
                        // relay view. Watching the preferred relay set keeps list-like
                        // metadata, including private-only kind:30000 lists, discoverable.
                        //
                        // updateFilter decides which relays to query; this path only
                        // invalidates when the source relay sets change.
                        invalidateFilters()
                    }
                },
            )

        return super.newSub(key)
    }

    override fun endSub(
        key: User,
        subId: String,
    ) {
        super.endSub(key, subId)
        userJobMap[key]?.forEach { it.cancel() }
    }
}
