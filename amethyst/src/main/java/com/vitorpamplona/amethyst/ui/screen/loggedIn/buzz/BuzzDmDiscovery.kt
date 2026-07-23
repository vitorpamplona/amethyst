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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzDmChannels
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzWorkspaces
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RELAY_GROUP_METADATA_KINDS
import com.vitorpamplona.quartz.buzz.dvDmVisibility.DmVisibilityEvent
import com.vitorpamplona.quartz.buzz.notifications.MemberAddedNotificationEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.subscribeAsFlow
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Always-on discovery of the viewer's Buzz **DM channels** across every joined workspace relay, mounted
 * once high in the logged-in tree ([com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage]).
 *
 * The deployed relay does not expose a queryable DM list; it addresses each member a kind-44100
 * member-added notification (`#p` = me). This warm-auths a `#p=me` fetch of 44100 (+ the 30622 visibility
 * snapshot) across the joined relays, records the channels into [BuzzDmChannels], fetches each channel's
 * 39000-39003 directory (so its `t`=dm marker + participants land in `LocalCache`), and keeps a live
 * `#p=me` 44100 subscription open for new DMs. The companion [BuzzDmJoinedChatTailPreload] then keeps the
 * discovered channels' messages warm app-wide — which is what lets a Buzz DM show on the Notifications tab
 * and in push without the viewer opening the conversation first.
 *
 * This mirrors [BuzzDmListViewModel]'s discovery, but account-scoped and always-on rather than bound to
 * the open inbox screen; the inbox keeps its own scoped copy for its per-relay projection.
 */
@Composable
fun BuzzDmDiscoveryPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val joined by BuzzWorkspaces.flow.collectAsStateWithLifecycle()

    // Restart the whole discovery (initial warm-auth fetch + live 44100 subs) whenever the joined
    // workspace set changes; the LaunchedEffect scope owns the live subscriptions and cancels them on
    // account switch or dispose.
    LaunchedEffect(account, joined) {
        if (joined.isEmpty()) return@LaunchedEffect
        runBuzzDmDiscovery(account, joined)
    }
}

/**
 * Warm-auth the initial 44100/30622 `#p=me` read across [relays], record every discovered channel, fetch
 * their directories, then keep a live 44100 subscription per relay open until the caller's scope is
 * cancelled. Suspends for the lifetime of the live subscriptions.
 */
private suspend fun runBuzzDmDiscovery(
    account: Account,
    relays: Set<NormalizedRelayUrl>,
) = coroutineScope {
    val me = account.userProfile().pubkeyHex

    // A joined workspace is first-party: pre-approve NIP-42 so the `#p=me` DM reads authenticate (the
    // restore-from-disk path doesn't set this, unlike the inbox/import/console entry points).
    relays.forEach { account.relayAuthLedger.setDecision(it.url, RelayAuthDecision.ALLOW) }

    val discoveryFilters =
        listOf(
            Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(me))),
            Filter(kinds = listOf(DmVisibilityEvent.KIND), tags = mapOf("p" to listOf(me))),
        )
    // `#p`-gated reads: use the warm-auth fetch so an `auth-required` CLOSED authenticates and retries
    // rather than returning empty.
    account.client.fetchAllWithHooks(
        filters = relays.associateWith { discoveryFilters },
        timeoutMs = 8_000,
        pendingOnAuthRequired = true,
    ) { relay, event ->
        (event as? MemberAddedNotificationEvent)?.channel()?.let { BuzzDmChannels.record(me, it, relay) }
        false
    }
    fetchDmMetadata(account, me)

    relays.forEach { relay ->
        launch {
            val filter = Filter(kinds = listOf(MemberAddedNotificationEvent.KIND), tags = mapOf("p" to listOf(me)))
            account.client.subscribeAsFlow(relay, filter).collect { events ->
                var changed = false
                events.filterIsInstance<MemberAddedNotificationEvent>().forEach { e ->
                    e.channel()?.let { if (BuzzDmChannels.record(me, it, relay)) changed = true }
                }
                if (changed) fetchDmMetadata(account, me)
            }
        }
    }
}

/** Fetch the NIP-29 directory (39000-39003) of every known DM channel so its `t`=dm marker + roster load. */
private suspend fun fetchDmMetadata(
    account: Account,
    viewer: HexKey,
) {
    val byRelay =
        BuzzDmChannels
            .channelsFor(viewer)
            .entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, ids) -> listOf(Filter(kinds = RELAY_GROUP_METADATA_KINDS, tags = mapOf("d" to ids))) }
    if (byRelay.isEmpty()) return
    account.client.fetchAllWithHooks(filters = byRelay, timeoutMs = 8_000, pendingOnAuthRequired = true) { _, _ -> false }
}
