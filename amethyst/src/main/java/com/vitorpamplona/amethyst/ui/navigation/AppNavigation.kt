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
import android.os.Parcelable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.crashreports.DisplayCrashMessages
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataScreen
import com.vitorpamplona.amethyst.ui.actions.mediaServers.AllMediaServersScreen
import com.vitorpamplona.amethyst.ui.broadcast.DisplayBroadcastProgress
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.navs.rememberNav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import com.vitorpamplona.amethyst.ui.navigation.routes.isBaseRoute
import com.vitorpamplona.amethyst.ui.navigation.routes.isSameRoute
import com.vitorpamplona.amethyst.ui.note.PayViaIntentScreen
import com.vitorpamplona.amethyst.ui.note.nip22Comments.ReplyCommentPostScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.default.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.display.BookmarkGroupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.ListOfBookmarkGroupsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.metadata.BookmarkGroupMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement.ArticleBookmarkListManagementScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.membershipManagement.PostBookmarkListManagementScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomByAuthorScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.NewGroupDMScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.EphemeralChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.metadata.NewEphemeralChatScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.PublicChatChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata.ChannelMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities.LiveActivityChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.MessagesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessGameScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chess.ChessLobbyScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.CommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.DiscoverScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.nip99Classifieds.NewProductScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.DraftListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.DvmContentDiscoveryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.followPacks.feed.FollowPackFeedScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.ShortNotePostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.VoiceReplyScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.lists.PeopleListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.display.packs.FollowPackScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.ListOfPeopleListsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.metadata.FollowPackMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.metadata.PeopleListMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.memberEdit.FollowListAndPackAndUserScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.publicMessages.NewPublicMessageScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.privacy.PrivacyOptionsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.ShowQRScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.redirect.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.AllRelayListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.RelayInformationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NIP47SetupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.UserSettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun AppNavigation(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
) {
    val nav = rememberNav()

    AccountSwitcherAndLeftDrawerLayout(accountViewModel, accountStateViewModel, nav) {
        NavHost(
            navController = nav.controller,
            startDestination = Route.Home,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable<Route.Home> { HomeScreen(accountViewModel, nav) }
            composable<Route.Message> { MessagesScreen(accountViewModel, nav) }
            composable<Route.Video> { VideoScreen(accountViewModel, nav) }
            composable<Route.Discover> { DiscoverScreen(accountViewModel, nav) }
            composable<Route.Notification> { NotificationScreen(accountViewModel, nav) }
            composable<Route.Chess> { ChessLobbyScreen(accountViewModel, nav) }

            composableFromEnd<Route.Lists> { ListOfPeopleListsScreen(accountViewModel, nav) }
            composableFromEndArgs<Route.MyPeopleListView> { PeopleListScreen(it.dTag, accountViewModel, nav) }
            composableFromEndArgs<Route.MyFollowPackView> { FollowPackScreen(it.dTag, accountViewModel, nav) }
            composableFromBottomArgs<Route.PeopleListManagement> { FollowListAndPackAndUserScreen(it.userToAdd, accountViewModel, nav) }

            composableFromBottomArgs<Route.PeopleListMetadataEdit> { PeopleListMetadataScreen(it.dTag, accountViewModel, nav) }
            composableFromBottomArgs<Route.FollowPackMetadataEdit> { FollowPackMetadataScreen(it.dTag, accountViewModel, nav) }

            composableFromEnd<Route.BookmarkGroups> { ListOfBookmarkGroupsScreen(accountViewModel, nav) }
            composableFromEndArgs<Route.BookmarkGroupView> { BookmarkGroupScreen(it.dTag, it.bookmarkType, accountViewModel, nav) }
            composableFromBottomArgs<Route.BookmarkGroupMetadataEdit> { BookmarkGroupMetadataScreen(it.dTag, accountViewModel, nav) }
            composableFromBottomArgs<Route.PostBookmarkManagement> { PostBookmarkListManagementScreen(it.postId, accountViewModel, nav) }
            composableFromBottomArgs<Route.ArticleBookmarkManagement> { ArticleBookmarkListManagementScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }

            composableFromBottomArgs<Route.QRDisplay> { ShowQRScreen(it.pubkey, accountViewModel, nav) }

            composableFromBottomArgs<Route.ManualZapSplitPayment> { PayViaIntentScreen(it.paymentId, accountViewModel, nav) }

            composableFromBottomArgs<Route.EditProfile> { NewUserMetadataScreen(nav, accountViewModel) }
            composable<Route.Search> { SearchScreen(accountViewModel, nav) }

            composableFromEnd<Route.SecurityFilters> { SecurityFiltersScreen(accountViewModel, nav) }
            composableFromEnd<Route.PrivacyOptions> { PrivacyOptionsScreen(Amethyst.instance.torPrefs.value, nav) }
            composableFromEnd<Route.Bookmarks> { BookmarkListScreen(accountViewModel, nav) }
            composableFromEnd<Route.Drafts> { DraftListScreen(accountViewModel, nav) }
            composableFromEnd<Route.Settings> { SettingsScreen(accountViewModel, nav) }
            composableFromEnd<Route.UserSettings> { UserSettingsScreen(accountViewModel, nav) }
            composableFromBottomArgs<Route.Nip47NWCSetup> { NIP47SetupScreen(accountViewModel, nav, it.nip47) }
            composableFromEndArgs<Route.EditRelays> { AllRelayListScreen(accountViewModel, nav) }
            composableFromEndArgs<Route.EditMediaServers> { AllMediaServersScreen(accountViewModel, nav) }

            composableFromEndArgs<Route.ContentDiscovery> { DvmContentDiscoveryScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Profile> { ProfileScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Note> { ThreadScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Hashtag> { HashtagScreen(it, accountViewModel, nav) }
            composableFromEndArgs<Route.Geohash> { GeoHashScreen(it, accountViewModel, nav) }
            composableFromEndArgs<Route.ChessGame> { ChessGameScreen(it.gameId, accountViewModel, nav) }
            composableFromEndArgs<Route.RelayInfo> { RelayInformationScreen(it.url, accountViewModel, nav) }
            composableFromEndArgs<Route.Community> { CommunityScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }
            composableFromEndArgs<Route.FollowPack> { FollowPackFeedScreen(Address(it.kind, it.pubKeyHex, it.dTag), accountViewModel, nav) }

            composableFromEndArgs<Route.Room> { ChatroomScreen(it.toKey(), it.message, it.replyId, it.draftId, it.expiresDays, accountViewModel, nav) }
            composableFromEndArgs<Route.RoomByAuthor> { ChatroomByAuthorScreen(it.id, null, accountViewModel, nav) }

            composableFromEndArgs<Route.PublicChatChannel> {
                PublicChatChannelScreen(
                    it.id,
                    it.draftId?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    it.replyTo?.let { hex -> accountViewModel.checkGetOrCreateNote(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromEndArgs<Route.LiveActivityChannel> {
                LiveActivityChannelScreen(
                    Address(it.kind, it.pubKeyHex, it.dTag),
                    draft = it.draftId?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    replyTo = it.replyTo?.let { hex -> accountViewModel.checkGetOrCreateNote(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromEndArgs<Route.EphemeralChat> {
                RelayUrlNormalizer.normalizeOrNull(it.relayUrl)?.let { relay ->
                    EphemeralChatScreen(
                        channelId = RoomId(it.id, relay),
                        draft = it.draftId?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                        replyTo = it.replyTo?.let { hex -> accountViewModel.checkGetOrCreateNote(hex) },
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }

            composableFromBottomArgs<Route.ChannelMetadataEdit> { ChannelMetadataScreen(it.id, accountViewModel, nav) }
            composableFromBottomArgs<Route.NewEphemeralChat> { NewEphemeralChatScreen(accountViewModel, nav) }
            composableFromBottomArgs<Route.NewGroupDM> { NewGroupDMScreen(it.message, it.attachment, accountViewModel, nav) }

            composableArgs<Route.EventRedirect> { LoadRedirectScreen(it.id, accountViewModel, nav) }

            composableFromBottomArgs<Route.GeoPost> {
                GeoHashPostScreen(
                    geohash = it.geohash,
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    reply = it.replyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromBottomArgs<Route.NewPublicMessage> {
                NewPublicMessageScreen(
                    to = it.toKey(),
                    reply = it.replyId?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draftId?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            composableFromBottomArgs<Route.HashtagPost> {
                HashtagPostScreen(
                    hashtag = it.hashtag,
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    reply = it.replyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromBottomArgs<Route.GenericCommentPost> {
                ReplyCommentPostScreen(
                    reply = it.replyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromBottomArgs<Route.NewProduct> {
                NewProductScreen(
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    accountViewModel,
                    nav,
                )
            }

            composableFromBottomArgs<Route.NewShortNote> {
                ShortNotePostScreen(
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    baseReplyTo = it.baseReplyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    fork = it.fork?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    version = it.version?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
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

    NavigateIfIntentRequested(nav, accountViewModel, accountStateViewModel)

    DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
    DisplayNotifyMessages(accountViewModel, nav)
    DisplayCrashMessages(accountViewModel, nav)
    DisplayBroadcastProgress(accountViewModel)
}

@Composable
private fun NavigateIfIntentRequested(
    nav: Nav,
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
) {
    accountViewModel.firstRoute?.let {
        accountViewModel.firstRoute = null
        val currentRoute = getRouteWithArguments(nav.controller)
        if (!isSameRoute(currentRoute, it)) {
            nav.newStack(it)
        }
    }

    val activity = LocalContext.current.getActivity()

    if (activity.intent.action == Intent.ACTION_SEND) {
        // avoids restarting the new Post screen when the intent is for the screen.
        // Microsoft's swift key sends Gifs as new actions
        if (isBaseRoute<Route.NewShortNote>(nav.controller)) return

        // saves the intent to avoid processing again
        var message by remember {
            mutableStateOf(
                activity.intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                    it.ifBlank { null }
                },
            )
        }

        var media by remember {
            mutableStateOf(
                (activity.intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri),
            )
        }

        nav.newStack(Route.NewShortNote(message = message, attachment = media.toString()))
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
                        if (npub != null && accountStateViewModel.currentAccountNPub() != npub) {
                            accountStateViewModel.checkAndSwitchUserSync(npub, nextRoute)
                        } else {
                            val currentRoute = getRouteWithArguments(nav.controller)
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
                    if (intent.action == Intent.ACTION_SEND) {
                        // avoids restarting the new Post screen when the intent is for the screen.
                        // Microsoft's swift key sends Gifs as new actions
                        if (!isBaseRoute<Route.NewShortNote>(nav.controller)) {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                                nav.newStack(Route.NewShortNote(message = it))
                            }

                            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                                nav.newStack(Route.NewShortNote(attachment = it.toString()))
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
                                    if (npub != null && accountStateViewModel.currentAccountNPub() != npub) {
                                        accountStateViewModel.checkAndSwitchUserSync(npub, newPage)
                                    } else {
                                        val currentRoute = getRouteWithArguments(nav.controller)
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
            AddAccountDialog(newAccount, accountStateViewModel) { newAccount = null }
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
