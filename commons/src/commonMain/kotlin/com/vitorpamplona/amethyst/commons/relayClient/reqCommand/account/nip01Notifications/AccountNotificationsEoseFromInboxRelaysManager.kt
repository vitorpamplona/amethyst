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
package com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip01Notifications

import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterNotificationsToPubkeys
import com.vitorpamplona.amethyst.commons.relayClient.account.nip01Notifications.filterSummaryNotificationsToPubkeys
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.CursorPlanner
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountQueryState
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.groupByRelay
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * The live notification tail, for **every** logged-in account, in one subscription.
 *
 * Notifications are `#p`-scoped, so a relay that serves several of the user's accounts can be asked
 * about all of them in one filter naming every pubkey — the query is identical in shape, only wider.
 * That is what keeps this within a relay's `max_subscriptions`: one REQ per relay instead of one per
 * (account, relay). With four accounts open, the per-account form pushed strfry relays past their
 * 20-subscription cap and they answered `ERROR: too many concurrent REQs` — a NOTICE, carrying no
 * subscription id, so the client could not even tell which REQ had been dropped. Those filters stayed
 * "live" in our books and silently never delivered.
 *
 * Gift wraps deliberately do **not** merge this way. They are unsolicited and their content is opaque
 * to the relay, so it cannot rate-limit them per recipient; a merged query would let one spammed
 * account consume the shared `limit` and starve every other account's DMs. Each account keeps its own
 * budget there — see [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.nip59GiftWraps.AccountGiftWrapsEoseManager].
 *
 * ## The `limit` here is shared, and deliberately not scaled
 *
 * [com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.metadata.AccountMetadataEoseManager]
 * multiplies its limits by the number of merged accounts; this one does not, and the difference is the
 * kind of event. Metadata is replaceable, so the limit is a safety bound and widening it costs nothing.
 * Notifications are a stream, so the limit is a page size: scaling it by four accounts would ask four
 * times the data of every relay on every cold start, and most would clamp it anyway (`max_limit` is 500
 * on strfry — already below the 2000 the summary filter asks for).
 *
 * So on a cold start a busy account can crowd a quiet one out of the shared newest-N. What recovers it
 * is [AccountNotificationsHistoryEoseManager], which pages backward per account and stays unmerged for
 * exactly that reason.
 *
 * ## The cursors live with the notes
 *
 * Each account's window comes from its own holders ([AccountQueryState.notificationCursorHolders]):
 * the notification feeds a screen renders, or the background tail. Accounts whose windows agree share
 * a filter; the rest get their own in the same REQ. So an account back with a new, empty feed is asked
 * from scratch without making the others refetch, and one that joins a relay inherits nothing.
 */
class AccountNotificationsEoseFromInboxRelaysManager(
    client: INostrClient,
    allKeys: () -> Set<AccountQueryState>,
) : SingleSubEoseManager<AccountQueryState>(client, allKeys) {
    override fun distinct(key: AccountQueryState) = key.account.userProfile()

    override fun cursorHoldersOf(key: AccountQueryState) = key.notificationCursorHolders

    private fun build(
        relay: NormalizedRelayUrl,
        pubkeys: List<HexKey>,
        since: Long?,
    ) = filterSummaryNotificationsToPubkeys(relay = relay, pubkeys = pubkeys, since = since) +
        filterNotificationsToPubkeys(relay = relay, pubkeys = pubkeys, since = since)

    /**
     * One filter set per inbox relay, naming every account that reads from it whose window agrees.
     *
     * Each account's window is planned against its own holders ([cursorHoldersOf]); [since], the
     * manager's shared map, is not used. Accounts with the same window for a filter share it, so the
     * usual case (every account caught up, or every account new) is still one filter per kind set.
     */
    override fun updateFilter(
        keys: List<AccountQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        // The split tabs read the same REQ. They are lazy, so while the split is on they are woken
        // here: otherwise what this loads for them would have no holder until a tab was opened.
        keys.forEach { key ->
            val feeds = key.feedContentStates ?: return@forEach
            if (key.account.settings.splitNotificationsEnabled.value) {
                feeds.notificationsFollowing.activate()
                feeds.notificationsEveryone.activate()
            }
        }

        val accountsPerRelay = mutableMapOf<NormalizedRelayUrl, MutableList<AccountQueryState>>()
        keys.forEach { key ->
            key.account.notificationRelays.flow.value.forEach { relay ->
                accountsPerRelay.getOrPut(relay) { mutableListOf() }.add(key)
            }
        }

        return accountsPerRelay.flatMap { (relay, accounts) ->
            // A floor, not paging (that is [AccountNotificationsHistoryEoseManager]): `since` means
            // *newer than*, so a feed that already holds a full page needs nothing older from here. Null
            // until it does, and for a background account, which has no feed: then the first fetch has
            // no floor at all. These filters are `#p`-scoped with a relay-side `limit`, so an all-time
            // query is one index scan returning the newest `limit` events. (A one-week fallback here once
            // emptied the tab for anyone quiet for a week.)
            val members =
                accounts.map { key ->
                    CursorPlanner.Member(
                        id = key.account.userProfile().pubkeyHex,
                        floor = key.feedContentStates?.notifications?.lastNoteCreatedAtIfFilled(),
                        holders = cursorsNamedBy(listOf(key)),
                    )
                }

            // NIP-29 group activity (reactions/replies to my messages) is deliberately NOT requested
            // here. It lives on the group's host relay and used to be one `#h` filter per relay carrying
            // every joined group id — but this subscription also carries the inbox filters, which have
            // no `#h` at all, and `block/buzz` downgrades any subscription with a channel-less (or
            // multi-channel) filter to "global", a class that by design never receives channel-scoped
            // events. So those filters answered the stored query at EOSE and then went deaf until the
            // next launch.
            //
            // Group and Buzz-DM activity now rides the per-channel subscriptions that are already scoped
            // to exactly one channel — RelayGroupJoinedChatTailSubAssembler and
            // BuzzDmJoinedChatTailSubAssembler, both mounted app-wide from LoggedInPage, so coverage is
            // unchanged and delivery is live.
            CursorPlanner.windowPerMember(members, cursorSource) { pubkeys, since -> build(relay, pubkeys, since) }
        }
    }

    /**
     * Per-account watchers, rebuilt as accounts come and go.
     *
     * There is only one subscription now, so these cannot hang off a per-key `newSub`. They are keyed
     * by user and reconciled here: an account that leaves has its jobs cancelled, and one that arrives
     * gets its own. Re-entrancy is safe — the watchers call `invalidateFilters()`, which lands back
     * here and finds every account already watched.
     *
     * This one re-plans the filter when the account's notification relays change.
     */
    private val userJobMap = mutableMapOf<User, Job>()

    // Per user with a screen: re-plans when that screen's notifications feed loads deeper, which
    // lowers the cold-start floor. Keyed to the feed it watches, because the subscription outlives
    // the screen: a new screen for the same account must be watched instead, and a background
    // account (no screen) has nothing to watch.
    private val depthJobMap = mutableMapOf<User, Pair<StateFlow<Long?>, Job>>()

    override fun updateSubscriptions(keys: Set<AccountQueryState>) {
        // A user can have a screen's key and a background key at once: the screen's carries the feed.
        val wanted =
            keys.groupBy { it.account.userProfile() }.mapValues { (_, userKeys) ->
                userKeys.firstOrNull { it.feedContentStates != null } ?: userKeys.first()
            }

        (userJobMap.keys - wanted.keys).toList().forEach { user ->
            userJobMap.remove(user)?.cancel()
        }

        wanted.forEach { (user, key) ->
            if (user !in userJobMap) {
                userJobMap[user] =
                    key.account.scope.launch(Dispatchers.IO) {
                        key.account.notificationRelays.flow.sample(1000).collectLatest {
                            invalidateFilters()
                        }
                    }
            }

            // Holds the flow, not the feed set: the feed set belongs to the screen.
            val depth = key.feedContentStates?.notifications?.lastNoteCreatedAtWhenFullyLoaded
            val watching = depthJobMap[user]
            if (watching?.first !== depth) {
                Log.d("NotificationsInbox") { "${user.pubkeyHex.take(8)}: ${if (depth == null) "no screen, depth watch stopped" else "watching a new screen's notifications depth"}" }
                watching?.second?.cancel()
                if (depth == null) {
                    depthJobMap.remove(user)
                } else {
                    depthJobMap[user] =
                        depth to
                        key.account.scope.launch(Dispatchers.IO) {
                            depth.sample(5000).collectLatest {
                                invalidateFilters()
                            }
                        }
                }
            }
        }

        (depthJobMap.keys - wanted.keys).toList().forEach { user ->
            depthJobMap.remove(user)?.second?.cancel()
        }

        // One key per account; every account's holders get what the REQ answers.
        val accounts = wanted.values.toList()
        serve(sub.id, cursorsNamedBy(accounts))
        sub.updateFilters(updateFilter(accounts, null).ifEmpty { null }?.groupByRelay())
    }

    override fun destroy() {
        userJobMap.values.forEach { it.cancel() }
        userJobMap.clear()
        depthJobMap.values.forEach { it.second.cancel() }
        depthJobMap.clear()
        super.destroy()
    }
}
