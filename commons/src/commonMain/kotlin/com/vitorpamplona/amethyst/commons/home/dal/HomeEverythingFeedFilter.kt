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
package com.vitorpamplona.amethyst.commons.home.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.UniverseFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.rebuildFeed
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filterIntoSet
import com.vitorpamplona.amethyst.commons.model.observables.CandidateSet
import com.vitorpamplona.amethyst.commons.model.observables.IFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByOutboxTopNavFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.muted.MutedAuthorsByProxyTopNavFilter
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent

/**
 * Combined home feed: every event accepted by either the New Threads filter
 * or the Conversations (replies) filter, in one list.
 */
class HomeEverythingFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>(),
    UniverseFeedFilter {
    private val newThreads = HomeNewThreadFeedFilter(account)
    private val conversations = HomeConversationsFeedFilter(account)

    override val universe = IFilter { newThreads.universe.matches(it) || conversations.universe.matches(it) }

    // Walks the cache, as the two feeds it joins.
    override val candidates: CandidateSet? get() = null

    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + account.settings.defaultHomeFollowList.value

    override fun showHiddenKey(): Boolean =
        account.liveHomeFollowLists.value is MutedAuthorsByOutboxTopNavFilter ||
            account.liveHomeFollowLists.value is MutedAuthorsByProxyTopNavFilter

    // One walk for both checks: asking each feed for its own walked the cache twice.
    override fun feed(): List<Note> {
        val isThread = newThreads.acceptor()
        val isReply = conversations.acceptor()
        return sort(
            rebuildFeed("home/everything") {
                LocalCache.notes.filterIntoSet { _, note ->
                    // As the two feeds walk: threads skip the notes map's addressable copies.
                    ((note.event?.kind ?: 99999) < 10000 && isThread(note)) || isReply(note)
                } +
                    LocalCache.addressables.filterIntoSet(kinds = HomeNewThreadFeedFilter.ADDRESSABLE_KINDS) { _, note ->
                        isThread(note)
                    }
            },
        )
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> = newThreads.applyFilter(newItems) + conversations.applyFilter(newItems)

    override fun sort(items: Set<Note>): List<Note> =
        items
            .distinctBy {
                if (it.event is RepostEvent || it.event is GenericRepostEvent) {
                    it.replyTo?.lastOrNull()?.idHex ?: it.idHex
                } else {
                    it.idHex
                }
            }.sortedByDefaultFeedOrder()
}
