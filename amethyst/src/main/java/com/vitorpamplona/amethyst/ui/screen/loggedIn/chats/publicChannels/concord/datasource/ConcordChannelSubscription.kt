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
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
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
