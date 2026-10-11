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
package com.vitorpamplona.amethyst.commons.relayClient.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.flow.StateFlow

/**
 * Loads the relay lists (and profiles) of the authors the selected top-nav feed reads by the
 * outbox model, for as long as the feed is in composition. Without it, a people-list member that
 * the user does not follow keeps no kind:10002 in the cache, so the feed never asks their relays.
 */
@Composable
fun FeedAuthorsOutboxSubscription(
    topNavFilter: StateFlow<IFeedTopNavFilter>,
    account: Account,
    dataSource: FeedAuthorsOutboxFilterAssembler,
) {
    val filter by topNavFilter.collectAsStateWithLifecycle()
    val authors = filter.outboxAuthors()

    // Keyed on the author set, not the filter: the filter is rebuilt whenever the list event
    // updates, and resubscribing the same members would only churn the finder.
    DisposableEffect(authors, account, dataSource) {
        val states = authors.map { UserFinderQueryState(account.cache.getOrCreateUser(it), account) }
        dataSource.subscribe(states)
        onDispose { dataSource.unsubscribe(states) }
    }
}

@Composable
fun FeedAuthorsOutboxSubscription(
    topNavFilter: StateFlow<IFeedTopNavFilter>,
    accountViewModel: AccountViewModel,
) {
    FeedAuthorsOutboxSubscription(topNavFilter, accountViewModel.account, accountViewModel.dataSources().feedAuthorsOutbox)
}
