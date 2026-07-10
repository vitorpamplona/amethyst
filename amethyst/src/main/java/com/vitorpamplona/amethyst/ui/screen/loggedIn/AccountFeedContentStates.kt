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

import android.content.ComponentCallbacks2
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.ui.feeds.FeedContentState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.feeds.ChannelFeedContentState
import com.vitorpamplona.amethyst.ui.screen.TopNavFilterState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.articles.dal.ArticlesFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.dal.BadgesFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.CalendarAppointmentsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.dal.CalendarCollectionsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.dal.RelayGroupDiscoveryFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.dal.ChatroomListNewFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.list.dal.CommunitiesFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.DiscoverLongFormFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip28Chats.DiscoverChatFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip51FollowSets.DiscoverFollowSetsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip53LiveActivities.DiscoverLiveFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip72Communities.DiscoverCommunityFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip90DVMs.DiscoverNIP89FeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.DiscoverMarketplaceFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.dal.DraftEventsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.dal.BrowseEmojiSetsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.list.dal.FollowPacksFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.dal.GitRepositoriesFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeConversationsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeEverythingFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeLiveFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.livestreams.dal.LiveStreamsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.longs.dal.LongsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.dal.MusicPlaylistsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.dal.MusicTracksFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.dal.NestsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.CardFeedContentState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationSummaryState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.OpenPollsState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.dal.PictureFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.dal.PodcastEpisodesFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.dal.PodcastsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.dal.ClosedPollsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.dal.OpenPollsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.dal.PollsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.products.dal.ProductsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.publicChats.dal.PublicChatsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts.dal.ShortsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.dal.SoftwareAppsFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.dal.VideoFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.webBookmarks.dal.WebBookmarkFeedFilter
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.dal.WorkoutFeedFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class AccountFeedContentStates(
    val account: Account,
    val scope: CoroutineScope,
) {
    val homeLive = ChannelFeedContentState(HomeLiveFilter(account), scope)
    val homeNewThreads = FeedContentState(HomeNewThreadFeedFilter(account), scope, LocalCache)
    val homeReplies = FeedContentState(HomeConversationsFeedFilter(account), scope, LocalCache)
    val homeEverything = FeedContentState(HomeEverythingFeedFilter(account), scope, LocalCache)

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

    val pollsFeed = FeedContentState(PollsFeedFilter(account), scope, LocalCache)
    val openPollsFeed = FeedContentState(OpenPollsFeedFilter(account), scope, LocalCache)
    val closedPollsFeed = FeedContentState(ClosedPollsFeedFilter(account), scope, LocalCache)

    val badgesFeed = FeedContentState(BadgesFeedFilter(account), scope, LocalCache)

    val browseEmojiSetsFeed = FeedContentState(BrowseEmojiSetsFeedFilter(account), scope, LocalCache)
    val communitiesList = FeedContentState(CommunitiesFeedFilter(account), scope, LocalCache)

    val picturesFeed = FeedContentState(PictureFeedFilter(account), scope, LocalCache)
    val workoutsFeed = FeedContentState(WorkoutFeedFilter(account), scope, LocalCache)
    val gitRepositoriesFeed = FeedContentState(GitRepositoriesFeedFilter(account), scope, LocalCache)
    val relayGroupsDiscoveryFeed = FeedContentState(RelayGroupDiscoveryFeedFilter(account), scope, LocalCache)
    val calendarAppointmentsFeed = FeedContentState(CalendarAppointmentsFeedFilter(account), scope, LocalCache)
    val calendarCollectionsFeed = FeedContentState(CalendarCollectionsFeedFilter(account), scope, LocalCache)
    val productsFeed = FeedContentState(ProductsFeedFilter(account), scope, LocalCache)
    val shortsFeed = FeedContentState(ShortsFeedFilter(account), scope, LocalCache)
    val publicChatsFeed = FeedContentState(PublicChatsFeedFilter(account), scope, LocalCache)
    val followPacksFeed = FeedContentState(FollowPacksFeedFilter(account), scope, LocalCache)
    val liveStreamsFeed = FeedContentState(LiveStreamsFeedFilter(account), scope, LocalCache)
    val nestsFeed = FeedContentState(NestsFeedFilter(account), scope, LocalCache)
    val longsFeed = FeedContentState(LongsFeedFilter(account), scope, LocalCache)
    val articlesFeed = FeedContentState(ArticlesFeedFilter(account), scope, LocalCache)
    val musicTracksFeed = FeedContentState(MusicTracksFeedFilter(account), scope, LocalCache)
    val musicPlaylistsFeed = FeedContentState(MusicPlaylistsFeedFilter(account), scope, LocalCache)
    val podcastEpisodesFeed = FeedContentState(PodcastEpisodesFeedFilter(account), scope, LocalCache)
    val podcastsFeed = FeedContentState(PodcastsFeedFilter(account), scope, LocalCache)
    val softwareAppsFeed = FeedContentState(SoftwareAppsFeedFilter(account), scope, LocalCache)

    val notifications = CardFeedContentState(NotificationFeedFilter(account), scope)
    val notificationsFollowing = CardFeedContentState(NotificationFeedFilter(account, TopFilter.AllFollows), scope)
    val notificationsEveryone = CardFeedContentState(NotificationFeedFilter(account, TopFilter.Global), scope)

    val notificationsOpenPolls = OpenPollsState(account, scope)
    val notificationSummary = NotificationSummaryState(account)

    val feedListOptions = TopNavFilterState(account, scope)

    val drafts = FeedContentState(DraftEventsFeedFilter(account), scope, LocalCache)

    val webBookmarks = FeedContentState(WebBookmarkFeedFilter(account), scope, LocalCache)

    init {
        // Under real memory pressure (process on the system LRU list — the strongest trim
        // level the OS still delivers since API 34), trim every feed down to release the
        // strong Note references that would otherwise keep pruned cache objects alive.
        scope.launch(Dispatchers.IO) {
            Amethyst.instance.trimLevelEvents.collect { level ->
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                    trimFeedsToSize(200)
                }
            }
        }

        // Marmot group list changes (new group, group marked known, group
        // metadata synced) don't flow through LocalCache.newEventBundles, so
        // the additive update path can't see them. Force a full feed rebuild
        // whenever the list changes so empty groups appear and placeholder
        // rows get replaced by real messages.
        scope.launch(Dispatchers.IO) {
            account.marmotGroupList.groupListChanges.collect {
                dmKnown.invalidateData()
                dmNew.invalidateData()
            }
        }

        // Same for the NIP-29 joined-group list (kind 10009): joining/leaving changes the list but
        // doesn't flow through newEventBundles, so force a rebuild — otherwise a just-joined group
        // (whose messages haven't loaded yet) wouldn't appear on the Messages tab until a later event.
        scope.launch(Dispatchers.IO) {
            account.relayGroupList.liveRelayGroupList
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // Flipping the NIP-29 view mode (inline groups vs one row per relay) changes what the
        // Messages feed emits for joined groups, but no event flows through LocalCache — force a
        // full rebuild so the list switches shape immediately.
        scope.launch(Dispatchers.IO) {
            account.settings.relayGroupViewMode
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        // Pinning/unpinning a room only changes sort order, not membership, so no
        // chat event flows through LocalCache. Force a rebuild to re-sort. This
        // also fires when pins arrive via the synced AppSpecificData event.
        scope.launch(Dispatchers.IO) {
            account.settings.syncedSettings.chats.pinnedChatrooms
                .drop(1)
                .collect {
                    dmKnown.invalidateData()
                }
        }

        scope.launch(Dispatchers.IO) {
            account.hiddenUsers.flow.collect {
                dmKnown.invalidateData()
                dmNew.invalidateData()
                // Re-mute removes cards, not just adds them. CardFeedContentState's
                // refreshSuspended() takes an additive-only path when lastNotes is
                // populated, which keeps stale cards for notes that no longer pass
                // the filter. Clear first so the refresh hits the full-rebuild branch.
                notifications.clear()
                notifications.invalidateData()
                notificationsFollowing.clear()
                notificationsFollowing.invalidateData()
                notificationsEveryone.clear()
                notificationsEveryone.invalidateData()
            }
        }
    }

    suspend fun init() {
        notificationSummary.initializeSuspend()
    }

    fun updateFeedsWith(newNotes: Set<Note>) {
        checkNotInMainThread()

        homeLive.updateFeedWith(newNotes)
        homeNewThreads.updateFeedWith(newNotes)
        homeReplies.updateFeedWith(newNotes)
        homeEverything.updateFeedWith(newNotes)

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
        openPollsFeed.updateFeedWith(newNotes)
        closedPollsFeed.updateFeedWith(newNotes)

        badgesFeed.updateFeedWith(newNotes)

        browseEmojiSetsFeed.updateFeedWith(newNotes)
        communitiesList.updateFeedWith(newNotes)

        picturesFeed.updateFeedWith(newNotes)
        workoutsFeed.updateFeedWith(newNotes)
        gitRepositoriesFeed.updateFeedWith(newNotes)
        relayGroupsDiscoveryFeed.updateFeedWith(newNotes)
        productsFeed.updateFeedWith(newNotes)
        shortsFeed.updateFeedWith(newNotes)
        publicChatsFeed.updateFeedWith(newNotes)
        followPacksFeed.updateFeedWith(newNotes)
        liveStreamsFeed.updateFeedWith(newNotes)
        nestsFeed.updateFeedWith(newNotes)
        longsFeed.updateFeedWith(newNotes)
        articlesFeed.updateFeedWith(newNotes)
        musicTracksFeed.updateFeedWith(newNotes)
        musicPlaylistsFeed.updateFeedWith(newNotes)
        podcastEpisodesFeed.updateFeedWith(newNotes)
        podcastsFeed.updateFeedWith(newNotes)
        softwareAppsFeed.updateFeedWith(newNotes)

        calendarAppointmentsFeed.updateFeedWith(newNotes)
        calendarCollectionsFeed.updateFeedWith(newNotes)

        notifications.updateFeedWith(newNotes)
        if (account.settings.splitNotificationsEnabled.value) {
            notificationsFollowing.updateFeedWith(newNotes)
            notificationsEveryone.updateFeedWith(newNotes)
        }
        notificationSummary.invalidateInsertData(newNotes)

        drafts.updateFeedWith(newNotes)

        webBookmarks.updateFeedWith(newNotes)
    }

    fun deleteNotes(newNotes: Set<Note>) {
        checkNotInMainThread()

        homeLive.deleteFromFeed(newNotes)
        homeNewThreads.deleteFromFeed(newNotes)
        homeReplies.deleteFromFeed(newNotes)
        homeEverything.deleteFromFeed(newNotes)

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
        openPollsFeed.deleteFromFeed(newNotes)
        closedPollsFeed.deleteFromFeed(newNotes)

        badgesFeed.deleteFromFeed(newNotes)

        browseEmojiSetsFeed.deleteFromFeed(newNotes)
        communitiesList.deleteFromFeed(newNotes)

        picturesFeed.deleteFromFeed(newNotes)
        workoutsFeed.deleteFromFeed(newNotes)
        gitRepositoriesFeed.deleteFromFeed(newNotes)
        relayGroupsDiscoveryFeed.deleteFromFeed(newNotes)
        productsFeed.deleteFromFeed(newNotes)
        shortsFeed.deleteFromFeed(newNotes)
        publicChatsFeed.deleteFromFeed(newNotes)
        followPacksFeed.deleteFromFeed(newNotes)
        liveStreamsFeed.deleteFromFeed(newNotes)
        nestsFeed.deleteFromFeed(newNotes)
        longsFeed.deleteFromFeed(newNotes)
        articlesFeed.deleteFromFeed(newNotes)
        musicTracksFeed.deleteFromFeed(newNotes)
        musicPlaylistsFeed.deleteFromFeed(newNotes)
        podcastEpisodesFeed.deleteFromFeed(newNotes)
        podcastsFeed.deleteFromFeed(newNotes)
        softwareAppsFeed.deleteFromFeed(newNotes)

        calendarAppointmentsFeed.deleteFromFeed(newNotes)
        calendarCollectionsFeed.deleteFromFeed(newNotes)

        notifications.deleteFromFeed(newNotes)
        if (account.settings.splitNotificationsEnabled.value) {
            notificationsFollowing.deleteFromFeed(newNotes)
            notificationsEveryone.deleteFromFeed(newNotes)
        }
        notificationSummary.invalidateInsertData(newNotes)

        drafts.deleteFromFeed(newNotes)

        webBookmarks.deleteFromFeed(newNotes)
    }

    fun trimFeedsToSize(maxItems: Int) {
        homeNewThreads.trimToSize(maxItems)
        homeReplies.trimToSize(maxItems)
        homeEverything.trimToSize(maxItems)

        dmKnown.trimToSize(maxItems)
        dmNew.trimToSize(maxItems)

        videoFeed.trimToSize(maxItems)

        discoverFollowSets.trimToSize(maxItems)
        discoverReads.trimToSize(maxItems)
        discoverMarketplace.trimToSize(maxItems)
        discoverDVMs.trimToSize(maxItems)
        discoverLive.trimToSize(maxItems)
        discoverCommunities.trimToSize(maxItems)
        discoverPublicChats.trimToSize(maxItems)

        pollsFeed.trimToSize(maxItems)
        openPollsFeed.trimToSize(maxItems)
        closedPollsFeed.trimToSize(maxItems)

        badgesFeed.trimToSize(maxItems)
        browseEmojiSetsFeed.trimToSize(maxItems)
        communitiesList.trimToSize(maxItems)

        picturesFeed.trimToSize(maxItems)
        workoutsFeed.trimToSize(maxItems)
        gitRepositoriesFeed.trimToSize(maxItems)
        relayGroupsDiscoveryFeed.trimToSize(maxItems)
        calendarAppointmentsFeed.trimToSize(maxItems)
        calendarCollectionsFeed.trimToSize(maxItems)
        productsFeed.trimToSize(maxItems)
        shortsFeed.trimToSize(maxItems)
        publicChatsFeed.trimToSize(maxItems)
        followPacksFeed.trimToSize(maxItems)
        liveStreamsFeed.trimToSize(maxItems)
        nestsFeed.trimToSize(maxItems)
        longsFeed.trimToSize(maxItems)
        articlesFeed.trimToSize(maxItems)
        musicTracksFeed.trimToSize(maxItems)
        musicPlaylistsFeed.trimToSize(maxItems)
        podcastEpisodesFeed.trimToSize(maxItems)
        podcastsFeed.trimToSize(maxItems)
        softwareAppsFeed.trimToSize(maxItems)

        notifications.trimToSize(maxItems)
        notificationsFollowing.trimToSize(maxItems)
        notificationsEveryone.trimToSize(maxItems)

        drafts.trimToSize(maxItems)
        webBookmarks.trimToSize(maxItems)
    }

    fun destroy() {
        notifications.destroy()
        notificationsFollowing.destroy()
        notificationsEveryone.destroy()
        notificationSummary.destroy()

        feedListOptions.destroy()
    }
}
