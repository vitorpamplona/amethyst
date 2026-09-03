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
package com.vitorpamplona.amethyst.ui.navigation

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.nipACWebRtcCalls.CallState
import com.vitorpamplona.amethyst.commons.relayClient.event.LocalEventFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinder
import com.vitorpamplona.amethyst.commons.relayClient.user.LocalUserFinderAccount
import com.vitorpamplona.amethyst.service.crashreports.DisplayCrashMessages
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.RelayAuthPromptHost
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.service.resourceusage.DisplayResourceUsageAlert
import com.vitorpamplona.amethyst.service.resourceusage.ScreenTimeIntegrator
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataScreen
import com.vitorpamplona.amethyst.ui.actions.bolt12Offers.Bolt12OffersScreen
import com.vitorpamplona.amethyst.ui.actions.mediaServers.AllMediaServersScreen
import com.vitorpamplona.amethyst.ui.actions.mediaServers.BlossomBlobManagerScreen
import com.vitorpamplona.amethyst.ui.actions.mediaServers.BlossomImportScreen
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DisplayBlossomSyncProgress
import com.vitorpamplona.amethyst.ui.actions.paymentTargets.PaymentTargetsScreen
import com.vitorpamplona.amethyst.ui.broadcast.DisplayBroadcastProgress
import com.vitorpamplona.amethyst.ui.call.CallActivity
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.ui.layouts.rememberScreenLayoutSpec
import com.vitorpamplona.amethyst.ui.navigation.bottombars.LocalTabReselectCoordinator
import com.vitorpamplona.amethyst.ui.navigation.bottombars.TabReselectCoordinator
import com.vitorpamplona.amethyst.ui.navigation.bottombars.favoriteIds
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.navs.rememberNav
import com.vitorpamplona.amethyst.ui.navigation.routes.MediaFeedRoute
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.consumesSharesInPlace
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.ui.navigation.routes.isBaseRoute
import com.vitorpamplona.amethyst.ui.navigation.routes.isSameRoute
import com.vitorpamplona.amethyst.ui.note.PayViaIntentScreen
import com.vitorpamplona.amethyst.ui.note.UpdateReactionTypeScreen
import com.vitorpamplona.amethyst.ui.note.nip22Comments.ReplyCommentPostScreen
import com.vitorpamplona.amethyst.ui.note.share.ShareNoteAsImageFileScreen
import com.vitorpamplona.amethyst.ui.note.share.ShareNoteAsImageScreen
import com.vitorpamplona.amethyst.ui.note.share.ShareNoteAsQrScreen
import com.vitorpamplona.amethyst.ui.screen.AccountSessionManager
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.apps.recommendations.ProfileAppRecommendationsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.articles.ArticlesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.BadgesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.award.AwardBadgeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.profile.ProfileBadgesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display.BookmarkGroupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.ListOfBookmarkGroupsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.metadata.BookmarkGroupMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement.ArticleBookmarkListManagementScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement.PostBookmarkListManagementScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.old.OldBookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.podcasts.BookmarkedPodcastsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.repositories.BookmarkedRepositoriesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.BrowserScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.browser.WebAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.AgentAttestationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.AgentConsoleScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.AgentPersonaEditScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.AgentWorkBoardScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzCanvasScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzDmListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzForumPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzForumThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzInviteScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.BuzzNewDmScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.JobBoardScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz.WorkflowRunBoardScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarCollectionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarReminderSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.CalendarsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.create.NewCalendarCollectionScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.create.NewCalendarEventScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.calendars.detail.CalendarEventDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat.GeohashChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat.GeohashChatsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat.GeohashTeleportScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat.NewGeohashChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.CreateGroupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.EditGroupInfoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupInfoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup.MarmotGroupListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.minichat.MinichatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomByAuthorScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.NewGroupDMScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordChannelListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordCreateScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordEditScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordHomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordInviteLinksScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordInviteScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.concord.ConcordMembersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.EphemeralChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.metadata.NewEphemeralChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata.ChannelMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveActivityChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupBrowseScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupChannelListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupCreateScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupDiscoveryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupEditScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupMembersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.relayGroup.RelayGroupThreadsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.MessagesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.NewConversationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.share.ShareToDMScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessGameScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessLobbyScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.CommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.list.CommunitiesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity.EditCommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity.NewCommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.contactList.ContactListUsersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.DiscoverScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip23LongForm.LongFormPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.NewProductScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.DraftListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.DvmContentDiscoveryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.favorites.FavoriteAlgoFeedsListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabAccountWatcher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabPreloader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabThemeWatcher
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.FavoriteAppManifestPreloader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.browse.BrowseEmojiSetsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.display.EmojiPackScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list.ListOfEmojiPacksScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list.metadata.EmojiPackMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.membershipManagement.EmojiPackSelectionScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.membershipManagement.MyEmojiListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.FavoriteAppsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites.NostrAppScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.feed.FollowPackFeedScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.list.FollowPacksScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitNewIssueScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryCodeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryIssuesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryPullsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.GitRepositoryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepositories.GitRepositoriesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.highlights.HighlightsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.highlights.NewHighlightScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.VoiceReplyScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.nip75Goals.NewGoalScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.interestSets.display.InterestSetScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.interestSets.list.ListOfInterestSetsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.interestSets.list.metadata.InterestSetMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.keyBackup.AccountBackupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.lists.PeopleListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.packs.FollowPackScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.ListOfPeopleListsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.metadata.FollowPackMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.metadata.PeopleListMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.memberEdit.FollowListAndPackAndUserScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.livestreams.LiveStreamsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.longs.LongsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.AddToMusicPlaylistSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.MusicPlaylistsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.MusicTracksScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.NewMusicPlaylistScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.NewMusicTrackScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.ConnectedAppDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.ConnectedAppsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.napplets.NappletsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.NestsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.lobby.NestLobbyScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.newUser.ImportFollowListPickFollowsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.newUser.ImportFollowListSelectUserScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.publicMessages.NewPublicMessageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nsites.NsitesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pictures.PicturesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.pinnednotes.PinnedNotesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.PodcastEpisodesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.PodcastScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.PodcastsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring.EditPodcastShowScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring.NewPodcastEpisodeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring.NewPodcastTrailerScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring.PodcastAuthoringScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.PollPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.PollsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.polls.results.PollResultsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.privacy.PrivacyOptionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.products.ProductsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.payment.SendPaymentScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.publicChats.PublicChatsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.ShowQRScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.redirect.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relay.RelayFeedScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relayauth.RelayAuthSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.AllRelayListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.RelayInformationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.eventsync.EventSyncScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip43.RelayMembersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip86.RelayManagementScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.subscriptions.ActiveSubscriptionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.vanish.RequestToVanishScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.vanish.VanishEventsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.scheduledposts.ScheduledPostsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.AllSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.AudioVisualizerSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.BlockedUsersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.BottomBarSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.CallSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ComposeSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.DrawerSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.HiddenWordsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.HomeTabsSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.MessagesSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.MutedThreadsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NIP47SetupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NamecoinSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NotificationSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.OtsSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ProfileUiSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ReactionsSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.ResourceUsageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SpammingUsersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.UpdateZapAmountScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.UserSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.VideoPlayerSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.nip46.Nip46ConnectedAppsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.nip46.Nip46SignerScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.shorts.ShortsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.SoftwareAppDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.softwareapps.SoftwareAppsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.url.UrlPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.url.UrlScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls.NewHlsVideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.AddClinkDebitWalletScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.AddNwcWalletScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.AddWalletScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.CashuMintRecommendationsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.CashuMintsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.CashuWalletScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.CashuWalletSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.OnchainTransactionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.ReloadMintScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.TopUpMintScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletDetailScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletReceiveScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletSendScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.WalletTransactionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.wizard.CashuWalletCreatedScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet.wizard.CashuWalletWizardScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.webBookmarks.WebBookmarksScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.NewWorkoutScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.workouts.WorkoutsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip84Highlights.parse.SharedHighlightParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun AppNavigation(
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    val nav = rememberNav()

    // Shows the "log in to this relay?" dialog when a NIP-42 challenge needs the user to decide.
    // Hosted here rather than in LoggedInPage so one dialog serves the whole shell: challenges
    // arrive off the shared relay socket, not from whatever screen happens to be on top.
    RelayAuthPromptHost(accountViewModel)

    // One layout decision per window size for the whole shell: bottom bar vs rail vs
    // permanent drawer, plus the docked notification panel. Every screen, bar and panel
    // below reads the same spec through LocalScreenLayout. The provider wraps this whole
    // function body so anything added to AppNavigation later is inside it by construction.
    val screenLayout = rememberScreenLayoutSpec()
    val tabReselectCoordinator = remember { TabReselectCoordinator() }

    // Mirror the tier for the nav-transition specs, which run outside composition and so
    // can't read LocalScreenLayout (see NavTransitionTier).
    SideEffect { NavTransitionTier.isLargeScreen = screenLayout.isLargeScreen }

    CompositionLocalProvider(
        LocalScreenLayout provides screenLayout,
        LocalTabReselectCoordinator provides tabReselectCoordinator,
        // Provide the shared finder CompositionLocals so any commons composable that
        // uses the no-arg observeUser*/EventFinderFilterAssemblerSubscription(note)
        // overloads works when rendered on Android (they error() if unprovided). Android's
        // own UI uses the AccountViewModel overloads and doesn't strictly need these, but
        // providing them removes the runtime trap for shared composables reaching the
        // logged-in tree. (The :napplet process never renders these composables.)
        LocalUserFinder provides accountViewModel.dataSources().userFinder,
        LocalUserFinderAccount provides accountViewModel.account,
        LocalEventFinder provides accountViewModel.dataSources().eventFinder,
    ) {
        AccountSwitcherAndLeftDrawerLayout(accountViewModel, accountSessionManager, nav) {
            Box(Modifier.fillMaxSize()) {
                BuildNavigation(accountViewModel, nav)
                // Holds the system back gesture off while a tap-driven navigation is still
                // waiting for the keyboard to retract. Composed after BuildNavigation so it
                // outranks the NavHost's own handler (the last composed enabled handler wins).
                HoldBackWhileNavigating(nav)
                // Pull each pinned nsite/napplet's manifest into LocalCache (and keep a device-local copy)
                // so its favorite resolves as reliably as a pinned web app's URL — the data the embedded
                // preloader below and the full-screen launcher both need. Not API-gated: every device's
                // launcher benefits, and it's the only preload step that runs below API 30.
                FavoriteAppManifestPreloader(accountViewModel)
                // Persistent layer that keeps pinned embedded tabs (browser / nsite / napplet) warm by
                // holding their surfaces attached. Below the drawer (drawn by the layout above) and below
                // dialogs (separate windows). API 30+ only, matching the embedded-surface feature.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bottomBarItems by accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
                        .collectAsStateWithLifecycle()
                    // Move every embedded app to the new account on a switch. Mounted before the layer and
                    // the preloader so the previous account's sessions are dropped ahead of the first sweep
                    // (an embed WebView's storage profile is fixed at construction, so it must be rebuilt).
                    EmbeddedTabAccountWatcher()
                    EmbeddedTabLayer(bottomBarItems.favoriteIds())
                    // Warm every pinned tab at startup so the first tap is instant (content already local).
                    EmbeddedTabPreloader(accountViewModel)
                    // Rebuild the warm surfaces in the new theme when the app's DARK/LIGHT preference flips
                    // (an embed WebView's theme is fixed at construction, so it can't follow a live switch).
                    EmbeddedTabThemeWatcher()
                }
            }
        }

        TrackScreenTime(nav)
        NavigateIfIntentRequested(nav, accountViewModel, accountSessionManager)

        DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
        DisplayNotifyMessages(accountViewModel, nav)
        DisplayCrashMessages(accountViewModel, nav)
        DisplayResourceUsageAlert(accountViewModel, nav)
        DisplayBroadcastProgress(accountViewModel)
        DisplayBlossomSyncProgress()

        ObserveIncomingCalls(accountViewModel)
    }
}

/**
 * Swallows the system back gesture for as long as [Nav] has a transition parked in the
 * [com.vitorpamplona.amethyst.ui.navigation.navs.ImeSettler].
 *
 * `NavHost` starts its predictive-back animation against a copy of the back stack that it reads a
 * main-thread message after the gesture begins, then calls `prepareForTransition` on the top two
 * entries of that copy. A pop committing in between makes it transition an entry the NavController
 * has already dropped, and the `IllegalStateException("Cannot transition entry that is not in the
 * back stack")` that follows lands on the UI dispatcher with nothing to catch it.
 *
 * The window that a person can actually hit is the keyboard settle: tapping the back arrow with a
 * text field focused parks the pop for up to
 * [IME_SETTLE_TIMEOUT_MS][com.vitorpamplona.amethyst.ui.navigation.navs.IME_SETTLE_TIMEOUT_MS],
 * which looks like nothing happened, and the natural next move is to swipe back. The press isn't
 * lost work — the navigation the tap asked for is already on its way and lands at the end of the
 * settle.
 *
 * Own composable rather than an inline `BackHandler` so that reading the state doesn't put the
 * whole shell — the NavHost included — in the recomposition scope of a flag that flips on every
 * navigation.
 */
@Composable
private fun HoldBackWhileNavigating(nav: Nav) {
    BackHandler(enabled = nav.isNavigating) {
        // Deliberately empty: the transition the user already asked for is what happens next.
    }
}

@Composable
private fun ObserveIncomingCalls(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val callState by accountViewModel.callManager.state.collectAsState()

    LaunchedEffect(callState) {
        val state = callState
        if (state is CallState.IncomingCall || state is CallState.Offering) {
            CallActivity.launch(context)
        }
    }
}

/**
 * Feeds the resource-usage ledger with time-per-screen. Only the route's
 * base name crosses this boundary — [ScreenTimeIntegrator.screenNameOf]
 * strips every navigation argument first, so the ledger can say "Profile"
 * but never which profile.
 */
@Composable
private fun TrackScreenTime(nav: Nav) {
    DisposableEffect(nav.controller) {
        val listener =
            NavController.OnDestinationChangedListener { _, destination, _ ->
                Amethyst.instance.screenTime.onScreen(ScreenTimeIntegrator.screenNameOf(destination.route))
            }
        nav.controller.addOnDestinationChangedListener(listener)
        onDispose {
            nav.controller.removeOnDestinationChangedListener(listener)
            Amethyst.instance.screenTime.onScreen(null)
        }
    }
}

@Composable
fun BuildNavigation(
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    NavHost(
        navController = nav.controller,
        startDestination = Route.Home,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) },
    ) {
        composableCapped<Route.Home> { HomeScreen(accountViewModel, nav) }
        composable<Route.Message> { MessagesScreen(accountViewModel, nav) }
        composableArgs<Route.Video> { VideoScreen(accountViewModel, nav, it.attachments, it.message) }
        composableArgs<Route.Discover> { DiscoverScreen(it.initialTab, accountViewModel, nav) }
        composableArgs<Route.Notification> { NotificationScreen(it.scrollToEventId, accountViewModel, nav) }
        composableFromEnd<Route.Polls> { PollsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Communities> { CommunitiesScreen(accountViewModel, nav) }
        composableFromEnd<Route.NewCommunity> { NewCommunityScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.EditCommunity> { EditCommunityScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEnd<Route.Badges> { BadgesScreen(accountViewModel, nav) }
        composableFromEnd<Route.ProfileBadges> { ProfileBadgesScreen(accountViewModel, nav) }
        composableFromEnd<Route.ProfileAppRecommendations> { ProfileAppRecommendationsScreen(accountViewModel, nav) }
        composableFromBottomArgs<Route.AwardBadge> { AwardBadgeScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav) }
        composableFromEndArgs<Route.Pictures> { PicturesScreen(accountViewModel, nav, it.attachments, it.message) }
        composableFromEnd<Route.Workouts> { WorkoutsScreen(accountViewModel, nav) }
        composableFromEnd<Route.GitRepositories> { GitRepositoriesScreen(accountViewModel, nav) }

        composableFromEnd<Route.Highlights> { HighlightsScreen(accountViewModel, nav) }
        composableFromEnd<Route.SoftwareApps> { SoftwareAppsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Napplets> { NappletsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Nsites> { NsitesScreen(accountViewModel, nav) }
        composableFromEnd<Route.Browser>(capWidth = false) { BrowserScreen(accountViewModel, nav) }
        composableFromEnd<Route.FavoriteApps> { FavoriteAppsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.WebApp>(capWidth = false) { WebAppScreen(it.url, accountViewModel, nav) }
        composableFromEndArgs<Route.NostrApp>(capWidth = false) { NostrAppScreen(it.coordinate, accountViewModel, nav) }
        composableFromEnd<Route.ConnectedApps> { ConnectedAppsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.ConnectedAppDetail> { ConnectedAppDetailScreen(it.coordinate, accountViewModel, nav) }
        composableFromEndArgs<Route.Nip46Signer> { Nip46SignerScreen(accountViewModel, nav, it.connectUri) }
        composableFromEnd<Route.Nip46ConnectedApps> { Nip46ConnectedAppsScreen(accountViewModel, nav) }
        composableFromEnd<Route.RelayAuthSettings> { RelayAuthSettingsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.SoftwareAppDetail> { SoftwareAppDetailScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEnd<Route.Calendars> { CalendarsScreen(accountViewModel, nav) }
        composableFromEnd<Route.CalendarCollections> { CalendarCollectionsScreen(accountViewModel, nav) }
        composableFromEnd<Route.CalendarReminderSettings> { CalendarReminderSettingsScreen(nav) }
        composableFromEndArgs<Route.CalendarEventDetail> {
            CalendarEventDetailScreen(it.kind, it.pubKeyHex, it.dTag, accountViewModel, nav)
        }
        composableFromBottomArgs<Route.NewCalendarEvent> { NewCalendarEventScreen(nav, accountViewModel) }
        composableFromBottomArgs<Route.EditCalendarEvent> {
            NewCalendarEventScreen(nav, accountViewModel, editKind = it.kind, editPubKeyHex = it.pubKeyHex, editDTag = it.dTag)
        }
        composableFromBottomArgs<Route.NewCalendarCollection> { NewCalendarCollectionScreen(nav, accountViewModel, it.dTag) }
        composableFromEnd<Route.Products> { ProductsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.Shorts> { ShortsScreen(accountViewModel, nav, it.attachments, it.message) }
        composableFromEnd<Route.PublicChats> { PublicChatsScreen(accountViewModel, nav) }
        composableFromEnd<Route.RelayGroups> { RelayGroupDiscoveryScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzDmList> { BuzzDmListScreen(it.relayUrl, accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzNewDm> { BuzzNewDmScreen(it.relayUrl, accountViewModel, nav) }
        composableFromEnd<Route.FollowPacks> { FollowPacksScreen(accountViewModel, nav) }
        composableFromEnd<Route.LiveStreams> { LiveStreamsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Nests> { NestsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.AgentConsole> { AgentConsoleScreen(it.relayUrl, accountViewModel, nav) }
        composableFromEnd<Route.AgentAttestation> { AgentAttestationScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.AgentPersonaEdit> { AgentPersonaEditScreen(it.slug, accountViewModel, nav) }
        composableFromEndArgs<Route.NestLobby> { NestLobbyScreen(it.addressValue, accountViewModel, nav) }
        composableFromEnd<Route.Longs> { LongsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Articles> { ArticlesScreen(accountViewModel, nav) }
        composableFromEnd<Route.MusicTracks> { MusicTracksScreen(accountViewModel, nav) }
        composableFromEnd<Route.MusicPlaylists> { MusicPlaylistsScreen(accountViewModel, nav) }
        composableFromEnd<Route.PodcastEpisodes> { PodcastEpisodesScreen(accountViewModel, nav) }
        composableFromEnd<Route.Podcasts> { PodcastsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.Podcast> { PodcastScreen(it.pubkey, accountViewModel, nav) }
        composableFromEnd<Route.PodcastAuthoring> { PodcastAuthoringScreen(accountViewModel, nav) }
        composableFromEnd<Route.EditPodcastShow> { EditPodcastShowScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.NewPodcastEpisode> { NewPodcastEpisodeScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
        composableFromEnd<Route.NewPodcastTrailer> { NewPodcastTrailerScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.NewMusicTrack> { NewMusicTrackScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
        composableFromEndArgs<Route.NewMusicPlaylist> { NewMusicPlaylistScreen(editDTag = it.dTag, accountViewModel = accountViewModel, nav = nav) }
        composableFromEndArgs<Route.AddToMusicPlaylist> { AddToMusicPlaylistSheet(trackAddress = it.trackAddress, accountViewModel = accountViewModel, nav = nav) }
        composableFromEnd<Route.NewHlsVideo> { NewHlsVideoScreen(accountViewModel, nav) }
        composableCapped<Route.Chess> { ChessLobbyScreen(accountViewModel, nav) }

        composableFromEnd<Route.Wallet> { WalletScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.WalletSend> { WalletSendScreen(it.walletId, accountViewModel, nav) }
        composableFromEndArgs<Route.WalletReceive> { WalletReceiveScreen(it.walletId, accountViewModel, nav) }
        composableFromEndArgs<Route.WalletTransactions> { WalletTransactionsScreen(it.walletId, accountViewModel, nav) }
        composableFromEnd<Route.OnchainTransactions> { OnchainTransactionsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.WalletDetail> { WalletDetailScreen(it.walletId, accountViewModel, nav) }
        composableFromEnd<Route.WalletAdd> { AddWalletScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.WalletAddNwc> { AddNwcWalletScreen(accountViewModel, nav, it.nip47) }
        composableFromEnd<Route.CashuWalletMints> { CashuMintsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.WalletAddClinkDebit> { AddClinkDebitWalletScreen(accountViewModel, nav, it.ndebit) }
        composableFromEnd<Route.CashuWallet> { CashuWalletScreen(accountViewModel, nav) }
        composableFromEnd<Route.CashuWalletWizard> { CashuWalletWizardScreen(accountViewModel, nav) }
        composableFromEnd<Route.CashuWalletCreated> { CashuWalletCreatedScreen(nav) }
        composableFromEnd<Route.CashuWalletSettings> { CashuWalletSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.CashuMintRecommendations> { CashuMintRecommendationsScreen(accountViewModel, nav) }
        composableFromBottomArgs<Route.SendPayment> { SendPaymentScreen(it.userHex, it.method, it.lnAddressOverride, it.btcAddressOverride, accountViewModel, nav) }

        composableFromEnd<Route.Lists> { ListOfPeopleListsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.MyPeopleListView> { PeopleListScreen(it.dTag, accountViewModel, nav) }
        composableFromEndArgs<Route.MyFollowPackView> { FollowPackScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.PeopleListManagement> { FollowListAndPackAndUserScreen(it.userToAdd, accountViewModel, nav) }

        composableFromBottomArgs<Route.PeopleListMetadataEdit> { PeopleListMetadataScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.FollowPackMetadataEdit> { FollowPackMetadataScreen(it.dTag, accountViewModel, nav) }

        composableFromEnd<Route.BookmarkGroups> { ListOfBookmarkGroupsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.BookmarkGroupView> { BookmarkGroupScreen(it.dTag, it.bookmarkType, accountViewModel, nav) }
        composableFromBottomArgs<Route.BookmarkGroupMetadataEdit> { BookmarkGroupMetadataScreen(it.dTag, accountViewModel, nav) }

        composableFromEnd<Route.InterestSets> { ListOfInterestSetsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.InterestSetView> { InterestSetScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.InterestSetMetadataEdit> { InterestSetMetadataScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.PostBookmarkManagement> { PostBookmarkListManagementScreen(it.postId, accountViewModel, nav) }
        composableFromBottomArgs<Route.ArticleBookmarkManagement> { ArticleBookmarkListManagementScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }

        composableFromEnd<Route.EmojiPacks> { ListOfEmojiPacksScreen(accountViewModel, nav) }
        composableFromEnd<Route.MyEmojiList> { MyEmojiListScreen(accountViewModel, nav) }
        composableFromEnd<Route.BrowseEmojiSets> { BrowseEmojiSetsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.EmojiPackView> { EmojiPackScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.EmojiPackMetadataEdit> { EmojiPackMetadataScreen(it.dTag, accountViewModel, nav) }
        composableFromBottomArgs<Route.EmojiPackSelection> { EmojiPackSelectionScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }

        composableFromBottomArgs<Route.QRDisplay> { ShowQRScreen(it.pubkey, accountViewModel, nav) }

        composableFromBottomArgs<Route.ManualZapSplitPayment> { PayViaIntentScreen(it.paymentId, accountViewModel, nav) }

        composableFromBottomArgs<Route.ReloadMint> { ReloadMintScreen(it.requestId, accountViewModel, nav) }

        composableFromBottomArgs<Route.TopUpMint> { TopUpMintScreen(it.mintUrl, accountViewModel, nav) }

        composableFromBottomArgs<Route.EditProfile> { NewUserMetadataScreen(nav, accountViewModel) }
        composableCapped<Route.Search> { SearchScreen(accountViewModel, nav) }

        composableFromEnd<Route.AllSettings> { AllSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.AccountBackup> { AccountBackupScreen(accountViewModel, nav) }
        composableFromEnd<Route.SecurityFilters> { SecurityFiltersScreen(accountViewModel, nav) }
        composableFromEnd<Route.BlockedUsers> { BlockedUsersScreen(accountViewModel, nav) }
        composableFromEnd<Route.SpammingUsers> { SpammingUsersScreen(accountViewModel, nav) }
        composableFromEnd<Route.HiddenWords> { HiddenWordsScreen(accountViewModel, nav) }
        composableFromEnd<Route.MutedThreads> { MutedThreadsScreen(accountViewModel, nav) }
        composableFromEnd<Route.PrivacyOptions> { PrivacyOptionsScreen(nav) }
        composableFromEnd<Route.NamecoinSettings> { NamecoinSettingsScreen(nav) }
        composableFromEnd<Route.OtsSettings> { OtsSettingsScreen(nav) }
        composableFromEnd<Route.Bookmarks> { BookmarkListScreen(accountViewModel, nav) }
        composableFromEnd<Route.OldBookmarks> { OldBookmarkListScreen(accountViewModel, nav) }
        composableFromEnd<Route.PinnedNotes> { PinnedNotesScreen(accountViewModel, nav) }
        composableFromEnd<Route.BookmarkedRepositories> { BookmarkedRepositoriesScreen(accountViewModel, nav) }
        composableFromEnd<Route.BookmarkedPodcasts> { BookmarkedPodcastsScreen(accountViewModel, nav) }
        composableFromEnd<Route.WebBookmarks> { WebBookmarksScreen(accountViewModel, nav) }
        composableFromEnd<Route.Drafts> { DraftListScreen(accountViewModel, nav) }
        composableFromEnd<Route.ScheduledPosts> { ScheduledPostsScreen(accountViewModel, nav) }
        composableFromEnd<Route.Settings> { SettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.ComposeSettings> { ComposeSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.UserSettings> { UserSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.ReactionsSettings> { ReactionsSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.MessagesSettings> { MessagesSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.AudioVisualizerSettings> { AudioVisualizerSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.BottomBarSettings> { BottomBarSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.DrawerSettings> { DrawerSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.HomeTabsSettings> { HomeTabsSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.ProfileUiSettings> { ProfileUiSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.VideoPlayerSettings> { VideoPlayerSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.CallSettings> { CallSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.NotificationSettings> { NotificationSettingsScreen(accountViewModel, nav) }
        composableFromEnd<Route.ResourceUsage> { ResourceUsageScreen(accountViewModel, nav) }
        composableFromEnd<Route.ImportFollowsSelectUser> { ImportFollowListSelectUserScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.ImportFollowsPickFollows> {
            ImportFollowListPickFollowsScreen(it.userHex, accountViewModel, nav)
        }

        composableFromEndArgs<Route.Nip47NWCSetup> { NIP47SetupScreen(accountViewModel, nav, it.nip47) }
        composableFromEndArgs<Route.UpdateZapAmount> { UpdateZapAmountScreen(accountViewModel, nav, it.nip47) }
        composableFromEndArgs<Route.EditRelays> { AllRelayListScreen(accountViewModel, nav) }

        composableFromEndArgs<Route.ActiveSubscriptions> { ActiveSubscriptionsScreen(accountViewModel, nav) }
        composableFromEnd<Route.EventSync> { EventSyncScreen(accountViewModel, nav) }
        composableFromEnd<Route.RequestToVanish> { RequestToVanishScreen(accountViewModel, nav) }
        composableFromEnd<Route.VanishEvents> { VanishEventsScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.EditMediaServers> { AllMediaServersScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.ManageBlossomBlobs> { BlossomBlobManagerScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.ImportBlossomBlobs> { BlossomImportScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.EditNestsServers> {
            com.vitorpamplona.amethyst.ui.actions.nestsServers
                .NestsServersScreen(accountViewModel, nav)
        }
        composableFromEnd<Route.EditFavoriteAlgoFeeds> { FavoriteAlgoFeedsListScreen(accountViewModel, nav) }
        composableFromEnd<Route.EditPaymentTargets> { PaymentTargetsScreen(accountViewModel, nav) }
        composableFromEnd<Route.EditBolt12Offers> { Bolt12OffersScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.UpdateReactionType> { UpdateReactionTypeScreen(accountViewModel, nav) }

        composableFromEndArgs<Route.ContentDiscovery> { DvmContentDiscoveryScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.Profile> { ProfileScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.Note> { ThreadScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.ShareNoteAsImage> { ShareNoteAsImageScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.ShareNoteAsImageFile> { ShareNoteAsImageFileScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.ShareNoteAsQr> { ShareNoteAsQrScreen(it.id, accountViewModel, nav) }
        composableFromEndArgs<Route.ContactListUsers> { ContactListUsersScreen(it.noteId, accountViewModel, nav) }
        composableFromEndArgs<Route.PollResults> { PollResultsScreen(it.noteId, accountViewModel, nav) }
        composableFromEndArgs<Route.Hashtag> { HashtagScreen(it, accountViewModel, nav) }
        composableFromEndArgs<Route.Geohash> { GeoHashScreen(it, accountViewModel, nav) }
        composableFromEndArgs<Route.Url> { UrlScreen(it, accountViewModel, nav) }
        composableFromEndArgs<Route.RelayFeed> { RelayFeedScreen(it, accountViewModel, nav) }
        composableFromEndArgs<Route.ChessGame> { ChessGameScreen(it.gameId, accountViewModel, nav) }
        composableFromEndArgs<Route.RelayInfo> { RelayInformationScreen(it.url, accountViewModel, nav) }
        composableFromEndArgs<Route.RelayManagement> { RelayManagementScreen(it.url, accountViewModel, nav) }
        composableFromEndArgs<Route.RelayMembers> { RelayMembersScreen(it.url, accountViewModel, nav) }
        composableFromEndArgs<Route.Community> { CommunityScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.GitRepository> { GitRepositoryScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.GitRepositoryCode> { GitRepositoryCodeScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.GitRepositoryIssues> { GitRepositoryIssuesScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.GitRepositoryPulls> { GitRepositoryPullsScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.GitRepositoryNewIssue> { GitNewIssueScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
        composableFromEndArgs<Route.FollowPack> { FollowPackFeedScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }

        composableFromEndArgs<Route.Room> { ChatroomScreen(it.toKey(), it.message, it.attachment, it.replyId, it.draftId, it.expiresDays, accountViewModel, nav) }
        composableFromEndArgs<Route.RoomByAuthor> { ChatroomByAuthorScreen(it.id, null, accountViewModel, nav) }

        composableFromEnd<Route.MarmotGroupList> { MarmotGroupListScreen(accountViewModel, nav) }
        composableFromEndArgs<Route.MarmotGroupChat> {
            MarmotGroupChatScreen(
                nostrGroupId = it.nostrGroupId,
                draftMessage = it.message,
                replyToInnerNote = it.replyId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        composableFromEndArgs<Route.MarmotGroupInfo> { MarmotGroupInfoScreen(it.nostrGroupId, accountViewModel, nav) }

        composableFromBottom<Route.CreateMarmotGroup> { CreateGroupScreen(accountViewModel, nav) }
        composableFromBottomArgs<Route.MarmotGroupEditInfo> { EditGroupInfoScreen(it.nostrGroupId, accountViewModel, nav) }

        composableFromEndArgs<Route.PublicChatChannel> {
            PublicChatChannelScreen(it.id, it.draftId, it.replyTo, accountViewModel, nav)
        }

        composableFromEndArgs<Route.LiveActivityChannel> {
            LiveActivityChannelScreen(
                Address(it.kind, it.pubKeyHex, it.dTag),
                draftId = it.draftId,
                replyToId = it.replyTo,
                accountViewModel,
                nav,
            )
        }

        composableFromEndArgs<Route.EphemeralChat> {
            EphemeralChatScreen(
                id = it.id,
                relayUrl = it.relayUrl,
                draftId = it.draftId,
                replyToId = it.replyTo,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.GeohashChat> {
            GeohashChatScreen(
                geohash = it.geohash,
                teleported = it.teleported,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEnd<Route.GeohashChats> { GeohashChatsScreen(accountViewModel, nav) }

        composableFromBottomArgs<Route.NewGeohashChat> { NewGeohashChatScreen(accountViewModel, nav) }

        composableFromBottomArgs<Route.GeohashTeleport> { GeohashTeleportScreen(accountViewModel, nav) }

        composableFromEndArgs<Route.RelayGroup> {
            RelayGroupChatScreen(
                id = it.id,
                relayUrl = it.relayUrl,
                draftId = it.draftId,
                replyToId = it.replyTo,
                inviteCode = it.inviteCode,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.RelayGroupServer> {
            RelayGroupChannelListScreen(
                relayUrl = it.relayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.Concord> {
            ConcordChannelScreen(
                communityId = it.communityId,
                channelId = it.channelId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ChatMinichat> {
            MinichatScreen(
                rootId = it.rootId,
                concordCommunityId = it.concordCommunityId,
                concordChannelId = it.concordChannelId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ConcordServer> {
            ConcordChannelListScreen(
                communityId = it.communityId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ConcordMembers> {
            ConcordMembersScreen(
                communityId = it.communityId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ConcordInviteLinks> {
            ConcordInviteLinksScreen(
                communityId = it.communityId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ConcordEdit> {
            ConcordEditScreen(
                communityId = it.communityId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.ConcordInvite> {
            ConcordInviteScreen(
                link = it.link,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.BuzzInvite> { BuzzInviteScreen(it.link, accountViewModel, nav) }

        composableFromEnd<Route.Concords> { ConcordHomeScreen(accountViewModel, nav) }

        composableFromEnd<Route.ConcordCreate> { ConcordCreateScreen(accountViewModel, nav) }

        composableFromEndArgs<Route.RelayGroupMembers> {
            RelayGroupMembersScreen(
                id = it.id,
                relayUrl = it.relayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.RelayGroupThreads> {
            RelayGroupThreadsScreen(
                id = it.id,
                relayUrl = it.relayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
        composableFromEndArgs<Route.BuzzCanvas> { BuzzCanvasScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzJobBoard> { JobBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzWorkflowBoard> { WorkflowRunBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzAgentWork> { AgentWorkBoardScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
        composableFromBottomArgs<Route.BuzzForumPost> { BuzzForumPostScreen(it.channelId, it.relayUrl, accountViewModel, nav) }
        composableFromEndArgs<Route.BuzzForumThread> { BuzzForumThreadScreen(it.channelId, it.relayUrl, it.rootId, accountViewModel, nav) }

        composableFromEndArgs<Route.RelayGroupCreate> {
            RelayGroupCreateScreen(
                relayUrl = it.relayUrl,
                isForum = it.isForum,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.RelayGroupEdit> {
            RelayGroupEditScreen(
                id = it.id,
                relayUrl = it.relayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromEndArgs<Route.RelayGroupBrowse> {
            RelayGroupBrowseScreen(
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.ChannelMetadataEdit> { ChannelMetadataScreen(it.id, accountViewModel, nav) }
        composableFromBottomArgs<Route.NewEphemeralChat> { NewEphemeralChatScreen(accountViewModel, nav) }
        composableFromBottom<Route.NewConversation> { NewConversationScreen(nav) }
        composableFromBottomArgs<Route.NewGroupDM> { NewGroupDMScreen(it.message, it.attachment, accountViewModel, nav) }
        composableFromBottomArgs<Route.ShareToDM> { ShareToDMScreen(it.message, it.attachment, accountViewModel, nav) }

        composableArgs<Route.EventRedirect> { LoadRedirectScreen(it.id, accountViewModel, nav) }

        composableFromBottomArgs<Route.GeoPost> {
            GeoHashPostScreen(
                geohash = it.geohash,
                message = it.message,
                attachment = it.attachment,
                replyId = it.replyTo,
                quoteId = it.quote,
                draftId = it.draft,
                accountViewModel,
                nav,
            )
        }

        composableFromBottomArgs<Route.NewPublicMessage> {
            NewPublicMessageScreen(
                to = it.toKey(),
                replyId = it.replyId,
                draftId = it.draftId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottom<Route.NewGoal> {
            NewGoalScreen(
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.NewWorkout> {
            NewWorkoutScreen(
                prefill = it,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.HashtagPost> {
            HashtagPostScreen(
                hashtag = it.hashtag,
                message = it.message,
                attachment = it.attachment,
                replyId = it.replyTo,
                quoteId = it.quote,
                draftId = it.draft,
                accountViewModel,
                nav,
            )
        }

        composableFromBottomArgs<Route.UrlPost> {
            UrlPostScreen(
                url = it.url,
                message = it.message,
                attachment = it.attachment,
                replyId = it.replyTo,
                quoteId = it.quote,
                draftId = it.draft,
                accountViewModel,
                nav,
            )
        }

        composableFromBottomArgs<Route.GenericCommentPost> {
            ReplyCommentPostScreen(
                replyId = it.replyTo,
                message = it.message,
                attachment = it.attachment,
                quoteId = it.quote,
                draftId = it.draft,
                accountViewModel,
                nav,
            )
        }

        composableFromBottomArgs<Route.NewProduct> {
            NewProductScreen(
                message = it.message,
                attachment = it.attachment,
                quoteId = it.quote,
                draftId = it.draft,
                accountViewModel,
                nav,
            )
        }

        composableFromBottomArgs<Route.NewLongFormPost> {
            LongFormPostScreen(
                draftId = it.draft,
                versionId = it.version,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.NewShortNote> {
            ShortNotePostScreen(
                message = it.message,
                attachment = it.attachment,
                baseReplyToId = it.baseReplyTo,
                quoteId = it.quote,
                forkId = it.fork,
                versionId = it.version,
                draftId = it.draft,
                groupThreadId = it.groupThreadId,
                groupThreadRelayUrl = it.groupThreadRelayUrl,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.NewPoll> {
            PollPostScreen(
                message = it.message,
                draftId = it.draft,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.NewHighlight> {
            NewHighlightScreen(
                quote = it.quote,
                url = it.url,
                prefix = it.prefix,
                suffix = it.suffix,
                comment = it.comment,
                context = it.context,
                sourceAddress = it.sourceAddress,
                sourceEventId = it.sourceEventId,
                author = it.author,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        composableFromBottomArgs<Route.VoiceReply> {
            VoiceReplyScreen(
                replyToNoteId = it.replyToNoteId,
                recordingFilePath = it.recordingFilePath,
                mimeType = it.mimeType,
                duration = it.duration,
                amplitudesJson = it.amplitudes,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/** True for both share flavors: a single file/text (SEND) and a multi-file selection (SEND_MULTIPLE). */
private fun Intent.isShareAction(): Boolean = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE

/** The text Android sent with the share — a caption, a URL, or the whole payload of a text-only share. */
private fun Intent.sharedText(): String? = getStringExtra(Intent.EXTRA_TEXT)?.ifBlank { null }

/**
 * Every content URI the share carries, in the order the sending app listed them. SEND_MULTIPLE
 * puts them in a parcelable ArrayList; plain SEND has at most one. Only the media targets declare
 * SEND_MULTIPLE, so every other caller can just take the first.
 */
private fun Intent.sharedStreamUris(): List<String> =
    if (action == Intent.ACTION_SEND_MULTIPLE) {
        IntentCompat
            .getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            ?.map { it.toString() }
            .orEmpty()
    } else {
        listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString())
    }

/**
 * Opens a media feed on a share. Sharing again while an earlier share is still on screen replaces
 * it instead of stacking a second copy of the feed — but only when the entry on top is itself a
 * share (it carries attachments). A feed the user opened from the bottom bar carries none, so it
 * stays put underneath and keeps its tab-root marker.
 */
private inline fun <reified T> Nav.navToSharedFeed(route: T) where T : Route, T : MediaFeedRoute {
    val current = getRouteWithArguments(T::class, controller)
    if (current is MediaFeedRoute && current.attachments.isNotEmpty()) {
        popUpTo(route, T::class)
    } else {
        newStack(route)
    }
}

@Composable
private fun NavigateIfIntentRequested(
    nav: Nav,
    accountViewModel: AccountViewModel,
    accountSessionManager: AccountSessionManager,
) {
    accountViewModel.firstRoute?.let { newRoute ->
        accountViewModel.firstRoute = null
        val currentRoute = getRouteWithArguments(newRoute::class, nav.controller)
        if (!isSameRoute(currentRoute, newRoute)) {
            nav.newStack(newRoute)
        }
    }

    val activity = LocalContext.current.getActivity()

    if (activity.intent.isShareAction()) {
        val target = ShareIntentRouting.targetOf(activity.intent.component?.className)

        // avoids restarting the destination screen when the intent is for the screen.
        // Microsoft's swift key sends Gifs as new actions.
        // The media targets land on a feed the user may well be standing on already, so they can't
        // guard on the destination — they rely on the intent being consumed below instead.
        when (target) {
            ShareTarget.HIGHLIGHT -> if (isBaseRoute<Route.NewHighlight>(nav.controller)) return
            ShareTarget.DIRECT_MESSAGE -> if (isBaseRoute<Route.ShareToDM>(nav.controller)) return
            ShareTarget.NEW_POST -> if (isBaseRoute<Route.NewShortNote>(nav.controller)) return
            ShareTarget.PICTURE, ShareTarget.SHORT_VIDEO, ShareTarget.VIDEO -> Unit
        }

        // saves the intent to avoid processing again
        val message = remember { activity.intent.sharedText() }
        val attachments = remember { activity.intent.sharedStreamUris() }

        when (target) {
            ShareTarget.HIGHLIGHT -> {
                val parsed = message?.let { SharedHighlightParser.parse(it) }
                nav.newStack(
                    Route.NewHighlight(
                        quote = parsed?.quote,
                        url = parsed?.url,
                        prefix = parsed?.prefix,
                        suffix = parsed?.suffix,
                    ),
                )
            }
            // The single-file composers take the first URI: only the media targets declare
            // SEND_MULTIPLE, so anything else can only be carrying one.
            ShareTarget.DIRECT_MESSAGE -> nav.newStack(Route.ShareToDM(message = message, attachment = attachments.firstOrNull()))
            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))
            ShareTarget.NEW_POST -> nav.newStack(Route.NewShortNote(message = message, attachment = attachments.firstOrNull()))
        }

        // Consume the launch intent so a later recomposition can't re-fire
        // newStack for the same share (the isBaseRoute guard is a non-reactive
        // snapshot and stops guarding once we navigate past the destination,
        // e.g. into a chat via the one-shot picker). Clearing the action also
        // lets the else-branch register the onNewIntent listener for the rest
        // of this session.
        activity.intent.action = null
    } else {
        var newAccount by remember { mutableStateOf<String?>(null) }

        var currentIntentNextPage by remember {
            mutableStateOf(
                activity.intent
                    ?.data
                    ?.toString()
                    ?.ifBlank { null },
            )
        }

        currentIntentNextPage?.let { intentNextPage ->
            var actionableNextPage by remember {
                mutableStateOf(uriToRoute(intentNextPage, accountViewModel.account))
            }

            LaunchedEffect(intentNextPage) {
                if (actionableNextPage != null) {
                    actionableNextPage?.let { nextRoute ->
                        val npub = runCatching { URI(intentNextPage.removePrefix("nostr:")).findParameterValue("account") }.getOrNull()
                        if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                            accountSessionManager.checkAndSwitchUserSync(npub) { account ->
                                uriToRoute(intentNextPage, account)
                            }
                        } else {
                            val currentRoute = getRouteWithArguments(nextRoute::class, nav.controller)
                            if (!isSameRoute(currentRoute, nextRoute)) {
                                nav.newStack(nextRoute)
                            }
                            actionableNextPage = null
                        }
                    }
                } else if (intentNextPage.contains("ncryptsec1")) {
                    // login functions
                    Nip19Parser.tryParseAndClean(intentNextPage)?.let {
                        newAccount = it
                    }

                    actionableNextPage = null
                } else {
                    accountViewModel.toastManager.toast(
                        R.string.invalid_nip19_uri,
                        R.string.invalid_nip19_uri_description,
                        intentNextPage,
                    )
                }

                currentIntentNextPage = null
            }
        }

        val scope = rememberCoroutineScope()

        DisposableEffect(nav, activity) {
            val consumer =
                Consumer<Intent> { intent ->
                    if (intent.isShareAction()) {
                        val target = ShareIntentRouting.targetOf(intent.component?.className)
                        val message = intent.sharedText()
                        val attachments = intent.sharedStreamUris()
                        val attachment = attachments.firstOrNull()

                        // avoids restarting the destination screen when the intent is for the screen.
                        // Microsoft's swift key sends Gifs as new actions.
                        // The media targets land on a feed the user may well be standing on already, so
                        // they always navigate: the route carries the attachment, so the composer opens
                        // on the newly shared file even when the feed itself is already on screen.
                        when (target) {
                            ShareTarget.HIGHLIGHT ->
                                if (!isBaseRoute<Route.NewHighlight>(nav.controller)) {
                                    val parsed = message?.let { SharedHighlightParser.parse(it) }
                                    nav.newStack(
                                        Route.NewHighlight(
                                            quote = parsed?.quote,
                                            url = parsed?.url,
                                            prefix = parsed?.prefix,
                                            suffix = parsed?.suffix,
                                        ),
                                    )
                                }

                            ShareTarget.DIRECT_MESSAGE ->
                                if (!isBaseRoute<Route.ShareToDM>(nav.controller)) {
                                    nav.newStack(Route.ShareToDM(message = message, attachment = attachment))
                                }

                            ShareTarget.PICTURE -> nav.navToSharedFeed(Route.Pictures(attachments = attachments, message = message))
                            ShareTarget.SHORT_VIDEO -> nav.navToSharedFeed(Route.Shorts(attachments = attachments, message = message))
                            ShareTarget.VIDEO -> nav.navToSharedFeed(Route.Video(attachments = attachments, message = message))

                            ShareTarget.NEW_POST ->
                                if (!consumesSharesInPlace(nav.controller) && (message != null || attachment != null)) {
                                    nav.newStack(Route.NewShortNote(message = message, attachment = attachment))
                                }
                        }
                    } else {
                        val uri = intent.data?.toString()

                        if (!uri.isNullOrBlank()) {
                            // navigation functions
                            val newPage = uriToRoute(uri, accountViewModel.account)

                            if (newPage != null) {
                                scope.launch {
                                    val npub = runCatching { URI(uri.removePrefix("nostr:")).findParameterValue("account") }.getOrNull()
                                    if (npub != null && accountSessionManager.currentAccountNPub() != npub) {
                                        accountSessionManager.checkAndSwitchUserSync(npub) { newAccount ->
                                            uriToRoute(uri, newAccount)
                                        }
                                    } else {
                                        val currentRoute = getRouteWithArguments(newPage::class, nav.controller)
                                        if (!isSameRoute(currentRoute, newPage)) {
                                            nav.newStack(newPage)
                                        }
                                    }
                                }
                            } else if (uri.contains("ncryptsec")) {
                                // login functions
                                Nip19Parser.tryParseAndClean(uri)?.let {
                                    newAccount = it
                                }
                            } else {
                                scope.launch {
                                    delay(1000)
                                    accountViewModel.toastManager.toast(
                                        R.string.invalid_nip19_uri,
                                        R.string.invalid_nip19_uri_description,
                                        uri,
                                    )
                                }
                            }
                        }
                    }
                }
            activity.addOnNewIntentListener(consumer)
            onDispose { activity.removeOnNewIntentListener(consumer) }
        }

        if (newAccount != null) {
            AddAccountDialog(newAccount, accountSessionManager) { newAccount = null }
        }
    }
}

fun URI.findParameterValue(parameterName: String): String? =
    rawQuery
        ?.split('&')
        ?.map {
            val parts = it.split('=')
            val name = parts.firstOrNull() ?: ""
            val value = parts.drop(1).firstOrNull() ?: ""
            Pair(name, value)
        }?.firstOrNull { it.first == parameterName }
        ?.second
