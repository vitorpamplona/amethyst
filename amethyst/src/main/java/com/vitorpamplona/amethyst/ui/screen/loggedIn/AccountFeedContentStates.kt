/**
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

import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.feeds.ChannelFeedContentState
import com.vitorpamplona.amethyst.ui.screen.TopNavFilterState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.DiscoverLongFormFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats.DiscoverChatFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.DiscoverFollowSetsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.DiscoverLiveFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities.DiscoverCommunityFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.DiscoverMarketplaceFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.dal.DraftEventsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeLiveFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CardFeedContentState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.dal.VideoFeedFilter
import kotlinx.coroutines.CoroutineScope

class AccountFeedContentStates(
    val account: Account,
    val scope: CoroutineScope,
) {
    val homeLive = ChannelFeedContentState(HomeLiveFilter(account), scope)
    val homeNewThreads = FeedContentState(HomeNewThreadFeedFilter(account), scope, LocalCache)
    val homeReplies = FeedContentState(HomeConversationsFeedFilter(account), scope, LocalCache)

    val dmKnown = FeedContentState(ChatroomListKnownFeedFilter(account), scope, LocalCache)
    val dmNew = FeedContentState(ChatroomListNewFeedFilter(account), scope, LocalCache)

    val videoFeed = FeedContentState(VideoFeedFilter(account), scope, LocalCache)

    val discoverFollowSets = FeedContentState(DiscoverFollowSetsFeedFilter(account), scope, LocalCache)
    val discoverReads = FeedContentState(DiscoverLongFormFeedFilter(account), scope, LocalCache)
    val discoverMarketplace = FeedContentState(DiscoverMarketplaceFeedFilter(account), scope, LocalCache)
    val discoverDVMs = FeedContentState(DiscoverNIP89FeedFilter(account), scope, LocalCache)
    val discoverLive = FeedContentState(DiscoverLiveFeedFilter(account), scope, LocalCache)
    val discoverCommunities = FeedContentState(DiscoverCommunityFeedFilter(account), scope, LocalCache)
    val discoverPublicChats = FeedContentState(DiscoverChatFeedFilter(account), scope, LocalCache)

    val notifications = CardFeedContentState(NotificationFeedFilter(account), scope)
    val notificationSummary = NotificationSummaryState(account)

    val feedListOptions = TopNavFilterState(account, scope)

    val drafts = FeedContentState(DraftEventsFeedFilter(account), scope, LocalCache)

    suspend fun init() {
        notificationSummary.initializeSuspend()
    }

    fun updateFeedsWith(newNotes: Set<Note>) {
        checkNotInMainThread()

        homeLive.updateFeedWith(newNotes)
        homeNewThreads.updateFeedWith(newNotes)
        homeReplies.updateFeedWith(newNotes)

        dmKnown.updateFeedWith(newNotes)
        dmNew.updateFeedWith(newNotes)

        videoFeed.updateFeedWith(newNotes)

        discoverMarketplace.updateFeedWith(newNotes)
        discoverFollowSets.updateFeedWith(newNotes)
        discoverReads.updateFeedWith(newNotes)
        discoverDVMs.updateFeedWith(newNotes)
        discoverLive.updateFeedWith(newNotes)
        discoverCommunities.updateFeedWith(newNotes)
        discoverPublicChats.updateFeedWith(newNotes)

        notifications.updateFeedWith(newNotes)
        notificationSummary.invalidateInsertData(newNotes)

        drafts.updateFeedWith(newNotes)
    }

    fun deleteNotes(newNotes: Set<Note>) {
        checkNotInMainThread()

        homeLive.deleteFromFeed(newNotes)
        homeNewThreads.deleteFromFeed(newNotes)
        homeReplies.deleteFromFeed(newNotes)

        dmKnown.updateFeedWith(newNotes)
        dmNew.updateFeedWith(newNotes)

        videoFeed.deleteFromFeed(newNotes)

        discoverMarketplace.deleteFromFeed(newNotes)
        discoverFollowSets.deleteFromFeed(newNotes)
        discoverReads.deleteFromFeed(newNotes)
        discoverDVMs.deleteFromFeed(newNotes)
        discoverLive.deleteFromFeed(newNotes)
        discoverCommunities.deleteFromFeed(newNotes)
        discoverPublicChats.deleteFromFeed(newNotes)

        notifications.deleteFromFeed(newNotes)
        notificationSummary.invalidateInsertData(newNotes)

        drafts.deleteFromFeed(newNotes)
    }

    fun destroy() {
        notifications.destroy()
        notificationSummary.destroy()

        feedListOptions.destroy()
    }
}
