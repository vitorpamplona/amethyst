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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.OutboxLoaderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Bundle of every flow a top-nav-filter consumer needs: the current
 * [TopFilter], the resolved follow list, the per-relay routing for relay
 * subscriptions, and a setter callback. Screens hand this to their TopBar,
 * their DAL filter (via [topFilterFlow] + [liveFollowListsFlow]), and their
 * relay subscription assembler (via [topFilterFlow] + [followsPerRelayFlow]).
 *
 * Use the account-default source (built from `account.settings.defaultXxxFollowList`
 * and the matching `account.liveXxxFollowLists*` flows) for the regular tab entry,
 * and [rememberLocalTopNavFilterSource] for a per-route override scoped to a
 * single back-stack entry.
 */
@Stable
class TopNavFilterSource(
    val topFilterFlow: StateFlow<TopFilter>,
    val liveFollowListsFlow: StateFlow<IFeedTopNavFilter>,
    val followsPerRelayFlow: StateFlow<IFeedTopNavPerRelayFilterSet>,
    val onChange: (TopFilter) -> Unit,
)

/**
 * Per-screen-instance TopNavFilterSource: builds a local [MutableStateFlow]
 * seeded with [initial], then derives the resolved follow list and per-relay
 * routing from it. The internal flows run on the supplied [scope] — callers
 * should pass `rememberCoroutineScope()` so the pipeline cancels when the
 * screen leaves composition, instead of `account.scope` which would keep
 * the flows alive until logout.
 */
@Composable
fun rememberLocalTopNavFilterSource(
    account: Account,
    initial: TopFilter,
    scope: CoroutineScope,
    cache: LocalCache = LocalCache,
): TopNavFilterSource =
    remember(account, initial, scope) {
        val filter = MutableStateFlow(initial)
        val live = account.topNavFilterFlow(filter, scope)
        val perRelay = OutboxLoaderState(live, cache, scope).flow
        TopNavFilterSource(
            topFilterFlow = filter,
            liveFollowListsFlow = live,
            followsPerRelayFlow = perRelay,
            onChange = { filter.tryEmit(it) },
        )
    }
