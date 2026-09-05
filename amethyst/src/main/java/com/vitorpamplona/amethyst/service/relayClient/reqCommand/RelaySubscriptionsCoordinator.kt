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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand

import com.vitorpamplona.amethyst.commons.relayClient.articles.ArticlesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.badges.BadgesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.calendars.CalendarsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.channel.ChannelFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.chess.ChessFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.communities.CommunityFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.communities.list.CommunitiesListFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.discover.DiscoveryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.emojipacks.BrowseEmojiSetsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.event.EventFinderFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.followPacks.FollowPacksFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.geohash.GeoHashFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.RepositoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.gitRepositories.GitRepositoriesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.highlights.HighlightsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.livestreams.LiveStreamsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.longs.LongsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.music.MusicPlaylistsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.music.MusicTracksFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.napplets.NappletsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nests.NestsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nsites.NsitesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.pictures.PicturesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.MyPodcastFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.OnePodcastFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.PodcastEpisodesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.podcasts.PodcastsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.polls.PollsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.polls.results.PollResponsesFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.products.ProductsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.profile.UserProfileFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.publicChats.PublicChatsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.relay.RelayFeedFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.relays.RelayInfoNip66FilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.shorts.ShortsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.softwareapps.SoftwareAppsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.url.UrlFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.video.VideoFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.wallet.OnchainZapsFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.workouts.WorkoutsFilterAssembler
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountForegroundFilterAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.ChannelFinderFilterAssemblyGroup
import com.vitorpamplona.amethyst.service.relayClient.searchCommand.SearchFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.apps.recommendations.datasource.ProfileAppRecommendationsFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.profile.datasource.ProfileBadgesFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.datasource.ChatroomFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.datasource.ConcordChannelHistoryFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.BuzzDmJoinedChatTailFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedChatTailFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupJoinedStateFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatHistoryFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenChatTailFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupOpenThreadsHistoryFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsDiscoveryFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupsOnRelayFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.datasource.ChatroomListFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.feed.datasource.FollowPackFeedFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.datasource.HashtagFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.datasource.HomeFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.datasource.ConnectedAppsFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestRoomFilterAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.datasource.NestRoomLivenessAssembler
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.ThreadFilterAssembler
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.IAuthStatus
import kotlinx.coroutines.CoroutineScope

class RelaySubscriptionsCoordinator(
    cache: LocalCache,
    client: INostrClient,
    authenticator: IAuthStatus,
    failureTracker: RelayOfflineTracker,
    scope: CoroutineScope,
) {
    // main one: notifications, dms and account settings (always-on while logged in)
    val account = AccountFilterAssembler(client)

    // foreground-only account loaders (follows-outbox finder, random-relay notifications)
    val accountForeground = AccountForegroundFilterAssembler(client, cache, authenticator, failureTracker, scope)

    // always running, feed assemblers.
    val home = HomeFilterAssembler(client)
    val chatroomList = ChatroomListFilterAssembler(client)
    val video = VideoFilterAssembler(client)
    val discovery = DiscoveryFilterAssembler(client)

    // loaders of content that is not yet in the device.
    // they are active when looking at events, users, channels.
    val channelFinder = ChannelFinderFilterAssemblyGroup(client)
    val userFinder = UserFinderFilterAssembler(client, cache, failureTracker)
    val eventFinder = EventFinderFilterAssembler(client, cache, userFinder)

    // active when searching or tagging users.
    val search = SearchFilterAssembler(client, scope, cache)

    // active depending on the screen.
    val channel = ChannelFilterAssembler(client)

    // NIP-29 relay-group REQ assemblers, each named for WHEN it runs. There is deliberately no
    // "group chat" assembler: a group's kind-9 chat timeline is served by the shared [channel]
    // assembler above (same as NIP-28 public chats), so only the group-specific surfaces get their
    // own here.
    val relayGroupsOnRelay = RelayGroupsOnRelayFilterAssembler(client) // browsing one relay's channel list
    val relayGroupOpenThreads = RelayGroupOpenThreadsFilterAssembler(client) // a group's forum-threads tab (recent tail)
    val relayGroupOpenThreadsHistory = RelayGroupOpenThreadsHistoryFilterAssembler(client) // the Threads tab's backward history pager
    val relayGroupCardWarmup = RelayGroupCardWarmupFilterAssembler(client) // prefetching a group before it's opened
    val relayGroupsDiscovery = RelayGroupsDiscoveryFilterAssembler(client) // the cross-relay Discover feed

    // NIP-29 chat, split state-vs-content like the DM / Concord stacks (see
    // amethyst/plans/2026-07-18-nip29-group-chat-subscriptions.md).
    val relayGroupJoinedState = RelayGroupJoinedStateFilterAssembler(client) // always-on: joined groups' metadata/roster/roles/pins
    val relayGroupJoinedChatTail = RelayGroupJoinedChatTailFilterAssembler(client) // always-on: batched #h recent-tail for Messages previews
    val buzzDmJoinedChatTail = BuzzDmJoinedChatTailFilterAssembler(client) // always-on: batched #h recent-tail for the viewer's Buzz DM channels
    val relayGroupOpenChatTail = RelayGroupOpenChatTailFilterAssembler(client) // the open group's recent chat (covers non-joined)
    val relayGroupOpenChatHistory = RelayGroupOpenChatHistoryFilterAssembler(client) // the open group's on-demand backward history pager

    // Concord Channels (encrypted communities). One assembler keeps every joined community's
    // control + channel planes live (kind-1059 by derived stream address).
    val concordChannels = ConcordChannelFilterAssembler(client)

    // On-demand backward history pager for whichever Concord Channel screen is open (older wraps by
    // until+limit, per relay), the Concord analog of the per-conversation NIP-04 history.
    val concordChannelHistory = ConcordChannelHistoryFilterAssembler(client)

    val chatroom = ChatroomFilterAssembler(client)
    val community = CommunityFilterAssembler(cache, client)
    val gitRepository = RepositoryFilterAssembler(cache, client)
    val thread = ThreadFilterAssembler(client)
    val profile = UserProfileFilterAssembler(cache, client)
    val hashtags = HashtagFilterAssembler(client)
    val geohashes = GeoHashFilterAssembler(client)
    val urls = UrlFilterAssembler(client)
    val relayFeed = RelayFeedFilterAssembler(client)
    val relayInfoNip66 = RelayInfoNip66FilterAssembler(client)
    val followPacks = FollowPackFeedFilterAssembler(client)
    val followPacksList = FollowPacksFilterAssembler(client)
    val chess = ChessFilterAssembler(client)

    val polls = PollsFilterAssembler(client)

    // Votes for the poll whose results screen is open.
    val pollResponses = PollResponsesFilterAssembler(cache, client)
    val pictures = PicturesFilterAssembler(client)
    val workouts = WorkoutsFilterAssembler(client)
    val gitRepositories = GitRepositoriesFilterAssembler(client)
    val highlights = HighlightsFilterAssembler(client)
    val calendars = CalendarsFilterAssembler(client)
    val products = ProductsFilterAssembler(client)
    val shorts = ShortsFilterAssembler(client)
    val publicChats = PublicChatsFilterAssembler(client)
    val liveStreams = LiveStreamsFilterAssembler(client)
    val nests = NestsFilterAssembler(client)
    val nestRoom = NestRoomFilterAssembler(client)
    val nestRoomLiveness = NestRoomLivenessAssembler(client)
    val longs = LongsFilterAssembler(client)
    val articles = ArticlesFilterAssembler(client)
    val musicTracks = MusicTracksFilterAssembler(client)
    val musicPlaylists = MusicPlaylistsFilterAssembler(client)
    val podcastEpisodes = PodcastEpisodesFilterAssembler(client)
    val podcasts = PodcastsFilterAssembler(client)
    val onePodcast = OnePodcastFilterAssembler(cache, client)
    val myPodcast = MyPodcastFilterAssembler(cache, client)
    val softwareApps = SoftwareAppsFilterAssembler(client)
    val napplets = NappletsFilterAssembler(client)
    val connectedApps = ConnectedAppsFilterAssembler(client)
    val nsites = NsitesFilterAssembler(client)
    val badges = BadgesFilterAssembler(client)
    val profileBadges = ProfileBadgesFilterAssembler(client)
    val profileAppRecommendations = ProfileAppRecommendationsFilterAssembler(client)
    val browseEmojiSets = BrowseEmojiSetsFilterAssembler(client)
    val communitiesList = CommunitiesListFilterAssembler(client)

    // active when sending zaps via NWC
    val nwc = NWCPaymentFilterAssembler(client)

    // active when the wallet's on-chain transactions screen is on top.
    val onchainZaps = OnchainZapsFilterAssembler(cache, client)

    // active while the user is browsing the NIP-87 mint picker. Subscribes to
    // kind:38172 cashu mint announcements + kind:38000 cashu-scoped
    // recommendations on the configured relay set.
    val cashuMintDirectory = CashuMintDirectoryFilterAssembler(client)

    val all =
        listOf(
            relayGroupsOnRelay,
            relayGroupOpenThreads,
            relayGroupOpenThreadsHistory,
            relayGroupCardWarmup,
            relayGroupsDiscovery,
            relayGroupJoinedState,
            relayGroupJoinedChatTail,
            buzzDmJoinedChatTail,
            relayGroupOpenChatTail,
            relayGroupOpenChatHistory,
            concordChannels,
            concordChannelHistory,
            account,
            accountForeground,
            home,
            chatroomList,
            video,
            discovery,
            polls,
            pollResponses,
            pictures,
            workouts,
            gitRepositories,
            highlights,
            calendars,
            products,
            shorts,
            publicChats,
            followPacksList,
            liveStreams,
            nests,
            nestRoom,
            nestRoomLiveness,
            longs,
            articles,
            musicTracks,
            musicPlaylists,
            podcastEpisodes,
            podcasts,
            onePodcast,
            myPodcast,
            softwareApps,
            badges,
            profileBadges,
            profileAppRecommendations,
            browseEmojiSets,
            communitiesList,
            channelFinder,
            eventFinder,
            userFinder,
            search,
            channel,
            chatroom,
            community,
            gitRepository,
            thread,
            profile,
            hashtags,
            geohashes,
            urls,
            relayFeed,
            relayInfoNip66,
            chess,
            nwc,
            onchainZaps,
            cashuMintDirectory,
        )

    fun destroy() = all.forEach { it.destroy() }
}
