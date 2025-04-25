/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.relayClient.notifyCommand.compose.DisplayNotifyMessages
import com.vitorpamplona.amethyst.ui.actions.NewUserMetadataScreen
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.components.toasts.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NewPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarks.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomByAuthorScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.send.NewGroupDMScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata.ChannelMetadataScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.MessagesScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.CommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.DiscoverScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.DraftListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.DvmContentDiscoveryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.redirect.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.AllRelayListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NIP47SetupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI

@Composable
fun AppNavigation(
    accountViewModel: AccountViewModel,
    accountStateViewModel: AccountStateViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel,
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
            composable<Route.Notification> { NotificationScreen(sharedPreferencesViewModel, accountViewModel, nav) }

            composable<Route.EditProfile> { NewUserMetadataScreen(nav, accountViewModel) }
            composable<Route.Search> { SearchScreen(accountViewModel, nav) }

            composableFromEnd<Route.SecurityFilters> { SecurityFiltersScreen(accountViewModel, nav) }
            composableFromEnd<Route.Bookmarks> { BookmarkListScreen(accountViewModel, nav) }
            composableFromEnd<Route.Drafts> { DraftListScreen(accountViewModel, nav) }
            composableFromEnd<Route.Settings> { SettingsScreen(sharedPreferencesViewModel, accountViewModel, nav) }
            composableFromBottomArgs<Route.Nip47NWCSetup> { NIP47SetupScreen(accountViewModel, nav, it.nip47) }
            composableFromEndArgs<Route.EditRelays> { AllRelayListScreen(it.toAdd, accountViewModel, nav) }

            composableFromEndArgs<Route.ContentDiscovery> { DvmContentDiscoveryScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Profile> { ProfileScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Note> { ThreadScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Hashtag> { HashtagScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Geohash> { GeoHashScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Community> { CommunityScreen(it.id, accountViewModel, nav) }
            composableFromEndArgs<Route.Room> { ChatroomScreen(it.id.toString(), it.message, it.replyId, it.draftId, accountViewModel, nav) }
            composableFromEndArgs<Route.RoomByAuthor> { ChatroomByAuthorScreen(it.id, null, accountViewModel, nav) }
            composableFromEndArgs<Route.Channel> { ChannelScreen(it.id, accountViewModel, nav) }

            composableFromBottomArgs<Route.ChannelMetadataEdit> { ChannelMetadataScreen(it.id, accountViewModel, nav) }
            composableFromBottomArgs<Route.NewGroupDM> { NewGroupDMScreen(it.message, it.attachment, accountViewModel, nav) }

            composableArgs<Route.EventRedirect> { LoadRedirectScreen(it.id, accountViewModel, nav) }

            composableFromBottomArgs<Route.NewPost> {
                NewPostScreen(
                    message = it.message,
                    attachment = it.attachment?.ifBlank { null }?.toUri(),
                    baseReplyTo = it.baseReplyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    quote = it.quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    fork = it.fork?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    version = it.version?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = it.draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    enableGeolocation = it.enableGeolocation,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }

    NavigateIfIntentRequested(nav, accountViewModel, accountStateViewModel)

    DisplayErrorMessages(accountViewModel.toastManager, accountViewModel, nav)
    DisplayNotifyMessages(accountViewModel, nav)
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
        if (isBaseRoute<Route.NewPost>(nav.controller)) return

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

        nav.newStack(Route.NewPost(message = message, attachment = media.toString()))

        media = null
        message = null
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
                mutableStateOf(uriToRoute(intentNextPage))
            }

            LaunchedEffect(intentNextPage) {
                if (actionableNextPage != null) {
                    actionableNextPage?.let { nextRoute ->
                        val npub = runCatching { URI(intentNextPage.removePrefix("nostr:")).findParameterValue("account") }.getOrNull()
                        if (npub != null && accountStateViewModel.currentAccount() != npub) {
                            accountStateViewModel.switchUserSync(npub, nextRoute)
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
                        if (!isBaseRoute<Route.NewPost>(nav.controller)) {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                                nav.newStack(Route.NewPost(message = it))
                            }

                            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                                nav.newStack(Route.NewPost(attachment = it.toString()))
                            }
                        }
                    } else {
                        val uri = intent.data?.toString()

                        if (!uri.isNullOrBlank()) {
                            // navigation functions
                            val newPage = uriToRoute(uri)

                            if (newPage != null) {
                                scope.launch {
                                    val npub = runCatching { URI(uri.removePrefix("nostr:")).findParameterValue("account") }.getOrNull()
                                    if (npub != null && accountStateViewModel.currentAccount() != npub) {
                                        accountStateViewModel.switchUserSync(npub, newPage)
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

private fun isSameRoute(
    currentRoute: Route?,
    newRoute: Route,
): Boolean {
    if (currentRoute == null) return false

    if (currentRoute == newRoute) {
        return true
    }

    if (newRoute is Route.EventRedirect) {
        return when (currentRoute) {
            is Route.Note -> newRoute.id == currentRoute.id
            is Route.Channel -> newRoute.id == currentRoute.id
            else -> false
        }
    }

    return false
}

val slideInVerticallyFromBottom = slideInVertically(animationSpec = tween(), initialOffsetY = { it })
val slideOutVerticallyToBottom = slideOutVertically(animationSpec = tween(), targetOffsetY = { it })

val slideInHorizontallyFromEnd = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
val slideOutHorizontallyToEnd = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })

val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.9f)
val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.9f)

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
