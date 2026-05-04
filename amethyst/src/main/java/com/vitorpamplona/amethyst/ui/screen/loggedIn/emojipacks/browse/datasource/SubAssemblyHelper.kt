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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource

import com.vitorpamplona.amethyst.model.topNavFeeds.IFeedTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.allFollows.AllFollowsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.model.topNavFeeds.noteBased.muted.MutedAuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.service.relays.SincePerRelayMap
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.subassemblies.filterBrowseEmojiSetsByAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.subassemblies.filterBrowseEmojiSetsByFollows
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.subassemblies.filterBrowseEmojiSetsByHashtag
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.subassemblies.filterBrowseEmojiSetsByMutedAuthors
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.datasource.subassemblies.filterBrowseEmojiSetsGlobal
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter

fun makeBrowseEmojiSetsFilter(
    feedSettings: IFeedTopNavPerRelayFilterSet,
    since: SincePerRelayMap?,
    defaultSince: Long? = null,
): List<RelayBasedFilter> =
    when (feedSettings) {
        is AllFollowsTopNavPerRelayFilterSet -> filterBrowseEmojiSetsByFollows(feedSettings, since, defaultSince)
        is AuthorsTopNavPerRelayFilterSet -> filterBrowseEmojiSetsByAuthors(feedSettings, since, defaultSince)
        is MutedAuthorsTopNavPerRelayFilterSet -> filterBrowseEmojiSetsByMutedAuthors(feedSettings, since, defaultSince)
        is GlobalTopNavPerRelayFilterSet -> filterBrowseEmojiSetsGlobal(feedSettings, since, defaultSince)
        is HashtagTopNavPerRelayFilterSet -> filterBrowseEmojiSetsByHashtag(feedSettings, since, defaultSince)
        else -> emptyList()
    }
