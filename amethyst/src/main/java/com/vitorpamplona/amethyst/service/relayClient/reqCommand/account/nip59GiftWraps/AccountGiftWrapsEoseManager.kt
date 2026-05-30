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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.nip59GiftWraps

import com.vitorpamplona.amethyst.commons.relayClient.nip17Dm.filterGiftWrapsToPubkey
import com.vitorpamplona.amethyst.commons.relayClient.pagination.TimeWindowPagination
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.eoseManagers.PerUserEoseManager
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.subscriptions.Subscription
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AccountGiftWrapsEoseManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : PerUserEoseManager<AccountQueryState>(client, allKeys) {
    override fun user(key: AccountQueryState) = key.account.userProfile()

    // How far back in time gift wraps are requested, per account. Boot opens a small
    // window so the messages list is usable before the whole DM history is fetched and
    // decrypted; scrolling to the end of the list widens it via [loadMore].
    private val windows = mutableMapOf<HexKey, TimeWindowPagination>()

    private fun windowFor(user: User) =
        windows.getOrPut(user.pubkeyHex) {
            TimeWindowPagination().also {
                Log.d(TAG) { "opening initial gift-wrap window for pubkey=${user.pubkeyHex.take(8)}… since=${it.since} (${daysAgo(it.since)}d back)" }
            }
        }

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    override fun updateFilter(
        key: AccountQueryState,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // Only loads DMs if the account is writeable
        return if (key.account.isWriteable()) {
            val relays = key.account.dmRelays.flow.value
            val windowSince = windowFor(user(key)).since
            Log.d(TAG) {
                "updateFilter: pubkey=${user(key).pubkeyHex.take(8)}… requesting kind:1059 " +
                    "since=$windowSince (${daysAgo(windowSince)}d window) on ${relays.size} dmRelay(s): ${relays.map { it.url }}"
            }
            relays.flatMap { relay ->
                filterGiftWrapsToPubkey(
                    relay = relay,
                    pubkey = user(key).pubkeyHex,
                    since = windowSince,
                )
            }
        } else {
            Log.d(TAG) { "updateFilter: pubkey=${user(key).pubkeyHex.take(8)}… account not writeable, skipping" }
            emptyList()
        }
    }

    /**
     * Widens the gift-wrap time window for [user] one step back and re-issues the
     * subscription so older conversations stream in. Called when the messages list is
     * scrolled near its end.
     */
    fun loadMore(user: User) {
        val window = windowFor(user)
        val before = window.since
        window.loadMore()
        Log.d(TAG) {
            "loadMore: pubkey=${user.pubkeyHex.take(8)}… widening window since $before -> ${window.since} " +
                "(${daysAgo(window.since)}d back, was ${daysAgo(before)}d), re-issuing subscription"
        }
        _loadingMore.value = true
        invalidateFilters()
    }

    override fun newEose(
        key: AccountQueryState,
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        // A backfill window finished loading. Only log the transition, not every live event.
        if (_loadingMore.value) {
            Log.d(TAG) {
                "newEose: pubkey=${user(key).pubkeyHex.take(8)}… backfill window finished on ${relay.url}, clearing loadingMore"
            }
            _loadingMore.value = false
        }
        super.newEose(key, relay, time, filters)
    }

    private fun daysAgo(epochSeconds: Long) = (TimeUtils.now() - epochSeconds) / TimeUtils.ONE_DAY

    val userJobMap = mutableMapOf<User, List<Job>>()

    @OptIn(FlowPreview::class)
    override fun newSub(key: AccountQueryState): Subscription {
        val user = user(key)
        userJobMap[user]?.forEach { it.cancel() }
        userJobMap[user] =
            listOf(
                key.account.scope.launch(Dispatchers.IO) {
                    key.account.dmRelays.flow.collectLatest {
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

    companion object {
        // Shared log tag for the DM time-window pagination. Filter logcat by this
        // tag to watch the boot window and scroll-driven backfill in real time.
        private const val TAG = "DMPagination"
    }
}
