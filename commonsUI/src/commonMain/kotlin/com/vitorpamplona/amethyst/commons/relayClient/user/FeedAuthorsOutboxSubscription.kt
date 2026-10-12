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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.commons.relayClient.subscriptions.LifecycleAwareKeyDataSourceSubscription
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Watches the authors the selected top-nav feed reads by the outbox model, for as long as the feed
 * is in composition: their relay lists and live metadata load through the regular [UserFinderFilterAssembler],
 * as feed authors ([UserFinderQueryState.isFeedAuthor]). Without it, a member of someone else's list
 * keeps no kind:10002 in the cache, so the feed never asks their relays.
 */
@Composable
fun FeedAuthorsOutboxSubscription(
    topNavFilter: StateFlow<IFeedTopNavFilter>,
    account: Account,
    dataSource: UserFinderFilterAssembler,
) {
    val filter by topNavFilter.collectAsStateWithLifecycle()
    val authors = filter.outboxAuthors()

    // Keyed on the author set, not the filter: the filter is rebuilt whenever the list event
    // updates, and resubscribing the same members would only churn the finder. A list can hold
    // thousands of members, so their users are looked up (and created) off the main thread.
    val states by produceState(emptyList<UserFinderQueryState>(), authors, account) {
        value =
            withContext(Dispatchers.Default) {
                authors.mapNotNull { pubkey ->
                    account.cache.checkGetOrCreateUser(pubkey)?.let { UserFinderQueryState(it, account, isFeedAuthor = true) }
                }
            }
    }

    // Stops asking 30s after the app leaves the foreground, like the feed's own subscription.
    LifecycleAwareKeyDataSourceSubscription(states, dataSource)
}

@Composable
fun FeedAuthorsOutboxSubscription(
    topNavFilter: StateFlow<IFeedTopNavFilter>,
    accountViewModel: AccountViewModel,
) {
    FeedAuthorsOutboxSubscription(topNavFilter, accountViewModel.account, accountViewModel.dataSources().userFinder)
}
