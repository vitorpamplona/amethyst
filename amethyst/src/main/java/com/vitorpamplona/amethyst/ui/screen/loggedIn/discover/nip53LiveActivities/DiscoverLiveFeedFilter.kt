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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities

import com.vitorpamplona.amethyst.commons.ui.feeds.IOnlineChecker
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.community.SingleCommunityTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.amethyst.service.OnlineChecker
import com.vitorpamplona.amethyst.ui.dal.FilterByListParams

/**
 * Adapter that makes [OnlineChecker] available as [IOnlineChecker] for commons.
 */
private object OnlineCheckerAdapter : IOnlineChecker {
    override fun isCachedAndOffline(url: String?): Boolean = OnlineChecker.isCachedAndOffline(url)
}

/**
 * Extracts the authors set from the discovery top filter, if available.
 */
private fun extractDiscoveryAuthors(account: Account): Set<String>? {
    val topFilter = account.liveDiscoveryFollowLists.value
    return when (topFilter) {
        is AuthorsByOutboxTopNavFilter -> topFilter.authors
        is MutedAuthorsByOutboxTopNavFilter -> topFilter.authors
        is AllFollowsByOutboxTopNavFilter -> topFilter.authors
        is SingleCommunityTopNavFilter -> topFilter.authors
        is AuthorsByProxyTopNavFilter -> topFilter.authors
        is MutedAuthorsByProxyTopNavFilter -> topFilter.authors
        is AllFollowsByProxyTopNavFilter -> topFilter.authors
        else -> null
    }
}

/**
 * App-specific wrapper that creates a [DiscoverLiveFeedFilter][com.vitorpamplona.amethyst.commons.ui.feeds.DiscoverLiveFeedFilter]
 * with Account-specific configuration.
 */
@Suppress("ktlint:standard:function-naming")
fun DiscoverLiveFeedFilter(account: Account) =
    com.vitorpamplona.amethyst.commons.ui.feeds.DiscoverLiveFeedFilter(
        userPubkeyHex = account.userProfile().pubkeyHex,
        followListCode = { account.settings.defaultDiscoveryFollowList.value.code },
        showHidden = {
            val fl = account.settings.defaultDiscoveryFollowList.value
            fl is TopFilter.MuteList ||
                (fl is TopFilter.PeopleList && fl.address == account.blockPeopleList.getBlockListAddress())
        },
        filterParamsFactory = {
            FilterByListParams.create(
                followLists = account.liveDiscoveryFollowLists.value,
                hiddenUsers = account.hiddenUsers.flow.value,
            )
        },
        followingKeySet = { account.kind3FollowList.flow.value.authors },
        discoveryAuthors = { extractDiscoveryAuthors(account) },
        onlineChecker = OnlineCheckerAdapter,
        cacheProvider = LocalCache,
    )
