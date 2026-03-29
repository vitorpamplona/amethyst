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

import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.Account
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.OpenPollsState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.dal.PollsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.dal.VideoFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.webBookmarks.dal.WebBookmarkFeedFilter
import kotlinx.coroutines.CoroutineScope

class AccountFeedContentStates(
    val account: Account,
    val scope: CoroutineScope,
) {
    val homeLive = ChannelFeedContentState(HomeLiveFilter(account), scope)
    val homeNewThreads = FeedContentState(HomeNewThreadFeedFilter(account), scope, account.cache)
    val homeReplies = FeedContentState(HomeConversationsFeedFilter(account), scope, account.cache)

    val dmKnown = FeedContentState(ChatroomListKnownFeedFilter(account), scope, account.cache)
    val dmNew = FeedContentState(ChatroomListNewFeedFilter(account), scope, account.cache)

    val videoFeed = FeedContentState(VideoFeedFilter(account), scope, account.cache)

    val discoverFollowSets = FeedContentState(DiscoverFollowSetsFeedFilter(account), scope, account.cache)
    val discoverReads = FeedContentState(DiscoverLongFormFeedFilter(account), scope, account.cache)
    val discoverMarketplace = FeedContentState(DiscoverMarketplaceFeedFilter(account), scope, account.cache)
    val discoverDVMs = FeedContentState(DiscoverNIP89FeedFilter(account), scope, account.cache)
    val discoverLive = FeedContentState(DiscoverLiveFeedFilter(account), scope, account.cache)
    val discoverCommunities = FeedContentState(DiscoverCommunityFeedFilter(account), scope, account.cache)
    val discoverPublicChats = FeedContentState(DiscoverChatFeedFilter(account), scope, account.cache)

    val pollsFeed = FeedContentState(PollsFeedFilter(account), scope, account.cache)

    val notifications = CardFeedContentState(NotificationFeedFilter(account), scope)
    val notificationsOpenPolls = OpenPollsState(account, scope)
    val notificationSummary = NotificationSummaryState(account)

    val feedListOptions = TopNavFilterState(account, scope)

    val drafts = FeedContentState(DraftEventsFeedFilter(account), scope, account.cache)

    val webBookmarks = FeedContentState(WebBookmarkFeedFilter(account), scope, account.cache)

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

        pollsFeed.updateFeedWith(newNotes)

        notifications.updateFeedWith(newNotes)
        notificationSummary.invalidateInsertData(newNotes)

        drafts.updateFeedWith(newNotes)

        webBookmarks.updateFeedWith(newNotes)
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

        pollsFeed.deleteFromFeed(newNotes)

        notifications.deleteFromFeed(newNotes)
        notificationSummary.invalidateInsertData(newNotes)

        drafts.deleteFromFeed(newNotes)

        webBookmarks.deleteFromFeed(newNotes)
    }

    fun destroy() {
        notifications.destroy()
        notificationSummary.destroy()

        feedListOptions.destroy()
    }
}
