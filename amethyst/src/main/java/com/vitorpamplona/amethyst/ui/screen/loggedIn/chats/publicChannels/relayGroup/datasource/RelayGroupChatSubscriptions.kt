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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.KeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * Always-on preload of the relay-signed **state** (metadata/roster/roles/pins) of every joined group,
 * mounted once high in the logged-in tree ([com.vitorpamplona.amethyst.ui.screen.loggedIn.LoggedInPage])
 * — the NIP-29 analog of the always-on account/DM tail and [ConcordChannelPreload]. The query state is
 * keyed on the account (stable), so we watch the joined-group list and re-derive on every join/leave.
 */
@Composable
fun RelayGroupStatePreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().relayGroupState
    val state = remember(account) { RelayGroupStateQueryState(account) }

    val joined by account.relayGroupList.liveRelayGroupList.collectAsStateWithLifecycle()
    LaunchedEffect(joined) { dataSource.invalidateFilters() }

    KeyDataSourceSubscription(state, dataSource)
}

/**
 * Always-on preview **live tail** for joined groups' recent chat, mounted alongside [RelayGroupStatePreload]
 * — keeps the Messages-list previews reflecting the true newest message app-wide. Re-derives on join/leave.
 */
@Composable
fun RelayGroupPreviewTailPreload(accountViewModel: AccountViewModel) {
    val account = accountViewModel.account
    val dataSource = accountViewModel.dataSources().relayGroupPreviewTail
    val state = remember(account) { RelayGroupPreviewTailQueryState(account) }

    val joined by account.relayGroupList.liveRelayGroupList.collectAsStateWithLifecycle()
    LaunchedEffect(joined) { dataSource.invalidateFilters() }

    KeyDataSourceSubscription(state, dataSource)
}

/**
 * Mount on the open group chat screen to keep the *currently open* group's recent chat live — covers a
 * non-joined group opened by link (the batched preview tail is joined-only) and live updates. Lifecycle-
 * aware so it stops when the screen leaves.
 */
@Composable
fun RelayGroupChatTailSubscription(
    groupId: GroupId,
    dataSource: RelayGroupChatTailFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, groupId) { RelayGroupChatTailQueryState(account, groupId) }
    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}

/**
 * Mount on the open group chat screen to keep its backward-history pager bound and armed (older
 * kind-9/poll by `until`+`limit` on the host relay), the NIP-29 analog of [ConcordChannelHistorySubscription].
 */
@Composable
fun RelayGroupChatHistorySubscription(
    groupId: GroupId,
    dataSource: RelayGroupChatHistoryFilterAssembler,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val state = remember(account, groupId) { RelayGroupChatHistoryQueryState(account, groupId) }
    LifecycleAwareKeyDataSourceSubscription(state, dataSource)
}
