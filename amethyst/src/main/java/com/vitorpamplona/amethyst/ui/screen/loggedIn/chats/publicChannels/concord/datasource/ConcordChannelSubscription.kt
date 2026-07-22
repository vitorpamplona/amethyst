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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.ui.navigation.bottombars.BottomBarEntry
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * Mount on any screen that lists the user's joined Concord Channels (the Messages
 * tab, the Concord home) to keep their planes live and their folded metadata in
 * the LocalCache channel index.
 *
 * The query state is keyed on the account (stable), so the assembler wouldn't
 * re-run its filter derivation on its own when a community folds or the joined set
 * changes. We watch [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager.revision]
 * — bumped on every join/leave and every Control-Plane fold — and on each change:
 *  1. refresh the LocalCache [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel]
 *     rows from the freshly-folded state, so list/chat UIs see the new name,
 *     channels and membership, then
 *  2. invalidate the assembler so a newly-revealed channel plane is subscribed
 *     (its Chat Plane address is only known after the Control Plane folds).
 */
@Composable
fun ConcordChannelSubscription(
    dataSource: ConcordChannelFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account) { ConcordChannelQueryState(account) }

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        // The channel-index refresh (community name/icon, membership, ban pruning) runs
        // account-wide from Account on this same revision, so the Messages tab has chips even
        // when this screen was never opened. Here we only need to re-derive the subscription
        // filters, since a newly-folded channel plane must now be subscribed.
        dataSource.invalidateFilters()
    }

    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Always-on account-level preload of every joined community's Control (and folded Chat) planes,
 * mounted once high in the logged-in tree ([com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage])
 * — the Concord analog of the always-on account/DM gift-wrap tail
 * ([com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssemblerSubscription]).
 *
 * Concord control-plane wraps are addressed to *derived stream keys*, not `#p=self`, so the always-on
 * DM tail never picks them up — without this, communities only fold (and thus reveal their channels,
 * metadata/icon and membership) while a Concord screen happens to be open. Uses the non-lifecycle
 * [KeyDataSourceSubscription] so the planes stay requested app-wide, exactly like DMs, and keeps the
 * same [com.vitorpamplona.amethyst.commons.model.concord.ConcordSessionManager.revision] watch so a
 * fresh fold subscribes its newly-revealed channel planes.
 *
 * Preloading a community's planes requires its keys, which come from the private kind-13302 list —
 * so a community pinned to the bottom bar whose list never reached the cache preloads nothing and
 * shows a blank tab + server screen ("doesn't load at all"). That list often lives only on the
 * community's own relays (Armada/Vector publish it there, never to the user's outbox), so before we
 * can preload we may first have to fetch it: [bootstrapPinnedCommunities] imports the list for any
 * pinned community we don't yet know, from the relays saved on its tab. Once it folds into the cache,
 * [com.vitorpamplona.amethyst.commons.model.concord.ConcordChannelListState.liveCommunities] surfaces
 * the entry, the tab/screen fill in, and the plane preload above picks it up.
 */
@Composable
fun ConcordChannelPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().concordChannels
    val state = remember(account) { ConcordChannelQueryState(account) }

    val revision by account.concordSessions.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        dataSource.invalidateFilters()
    }

    bootstrapPinnedCommunities(accountViewModel)

    KeyDataSourceSubscription(state, dataSource)
}

/**
 * Fetch the private kind-13302 list of any Concord community pinned to the bottom bar whose list we
 * don't already have, from the relays saved on its tab (see
 * [com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel.importConcordCommunities]). Runs
 * app-wide as part of [ConcordChannelPreload], so a pinned community loads without the user ever
 * opening the Concord hub (which has its own import). Keyed on the exact set of missing communities,
 * so the (slow, stock-relay) fetch runs when a new gap appears — a freshly pinned community we can't
 * yet resolve — and not on every recomposition; it stops once every pinned community is known.
 */
@Composable
private fun bootstrapPinnedCommunities(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val items by accountViewModel.settings.uiSettingsFlow.bottomBarItems
        .collectAsStateWithLifecycle()
    val communities by account.concordChannelList.liveCommunities.collectAsStateWithLifecycle()

    val missingPinned =
        remember(items, communities) {
            val known = communities.mapTo(HashSet()) { it.id }
            items
                .filterIsInstance<BottomBarEntry.Concord>()
                .map { it.communityId }
                .filterTo(sortedSetOf()) { it !in known }
        }

    LaunchedEffect(missingPinned) {
        if (missingPinned.isNotEmpty()) accountViewModel.importConcordCommunities()
    }
}
