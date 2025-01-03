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

import android.content.Context
import android.content.ContextWrapper
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
import androidx.core.util.Consumer
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.actions.relays.AllRelayListView
import com.vitorpamplona.amethyst.ui.components.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.components.DisplayNotifyMessages
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NewPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarks.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ChatroomScreenByAuthor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.CommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.DiscoverScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.drafts.DraftListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.dvms.DvmContentDiscoveryScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash.GeoHashScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.hashtag.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.ListsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.NIP47SetupScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URI
import java.net.URLDecoder

fun NavBackStackEntry.id(): String? = arguments?.getString("id")

fun NavBackStackEntry.message(): String? =
    arguments?.getString("message")?.let {
        URLDecoder.decode(it, "utf-8")
    }

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
            startDestination = Route.Home.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
        ) {
            composable(Route.Home.route) { HomeScreen(accountViewModel, nav) }
            composable(Route.Message.route) { ChatroomListScreen(accountViewModel, nav) }
            composable(Route.Video.route) { VideoScreen(accountViewModel, nav) }
            composable(Route.Discover.route) { DiscoverScreen(accountViewModel, nav) }
            composable(Route.Notification.route) { NotificationScreen(sharedPreferencesViewModel, accountViewModel, nav) }

            composable(Route.Search.route) { SearchScreen(accountViewModel, nav) }

            composable(
                Route.BlockedUsers.route,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) { SecurityFiltersScreen(accountViewModel, nav) }

            composable(
                Route.Bookmarks.route,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) { BookmarkListScreen(accountViewModel, nav) }

            composable(
                Route.Lists.route,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) { ListsScreen(accountViewModel, nav) }

            composable(
                Route.Drafts.route,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) { DraftListScreen(accountViewModel, nav) }

            composable(
                Route.ContentDiscovery.route,
                Route.ContentDiscovery.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                DvmContentDiscoveryScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Profile.route,
                Route.Profile.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                ProfileScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Note.route,
                Route.Note.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                ThreadScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Hashtag.route,
                Route.Hashtag.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                HashtagScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Geohash.route,
                Route.Geohash.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                GeoHashScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Community.route,
                Route.Community.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                CommunityScreen(it.id(), accountViewModel, nav)
            }

            composable(
                Route.Room.route,
                Route.Room.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                ChatroomScreen(
                    roomId = it.id(),
                    draftMessage = it.message(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            composable(
                Route.RoomByAuthor.route,
                Route.RoomByAuthor.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                ChatroomScreenByAuthor(it.id(), null, accountViewModel, nav)
            }

            composable(
                Route.Channel.route,
                Route.Channel.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                ChannelScreen(
                    channelId = it.id(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            composable(
                Route.Event.route,
                Route.Event.arguments,
            ) {
                LoadRedirectScreen(
                    eventId = it.id(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            composable(
                Route.Settings.route,
                Route.Settings.arguments,
                enterTransition = { slideInHorizontallyFromEnd },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutHorizontallyToEnd },
            ) {
                SettingsScreen(
                    sharedPreferencesViewModel,
                    accountViewModel,
                    nav,
                )
            }

            composable(
                Route.NIP47Setup.route,
                Route.NIP47Setup.arguments,
                enterTransition = { slideInVerticallyFromBottom },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutVerticallyToBottom },
            ) {
                val nip47 = it.arguments?.getString("nip47")

                NIP47SetupScreen(accountViewModel, nav, nip47)
            }

            composable(
                Route.EditRelays.route,
                content = {
                    val relayToAdd = it.arguments?.getString("toAdd")

                    AllRelayListView(
                        relayToAdd = relayToAdd,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                },
            )

            composable(
                Route.NewPost.route,
                Route.NewPost.arguments,
                enterTransition = { slideInVerticallyFromBottom },
                exitTransition = { scaleOut },
                popEnterTransition = { scaleIn },
                popExitTransition = { slideOutVerticallyToBottom },
            ) {
                val draftMessage = it.arguments?.getString("message")?.ifBlank { null }
                val attachment =
                    it.arguments?.getString("attachment")?.ifBlank { null }?.let {
                        Uri.parse(it)
                    }
                val baseReplyTo = it.arguments?.getString("baseReplyTo")
                val quote = it.arguments?.getString("quote")
                val fork = it.arguments?.getString("fork")
                val version = it.arguments?.getString("version")
                val draft = it.arguments?.getString("draft")
                val enableMessageInterface = it.arguments?.getBoolean("enableMessageInterface") ?: false
                val enableGeolocation = it.arguments?.getBoolean("enableGeolocation") ?: false

                NewPostScreen(
                    message = draftMessage,
                    attachment = attachment,
                    baseReplyTo = baseReplyTo?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    quote = quote?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    fork = fork?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    version = version?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    draft = draft?.let { hex -> accountViewModel.getNoteIfExists(hex) },
                    enableMessageInterface = enableMessageInterface,
                    enableGeolocation = enableGeolocation,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }

    NavigateIfIntentRequested(nav, accountViewModel, accountStateViewModel)

    DisplayErrorMessages(accountViewModel)
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
        if (isBaseRoute(nav.controller, Route.NewPost.base)) return

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

        nav.newStack(buildNewPostRoute(draftMessage = message, attachment = media))

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
                    Nip19Bech32.tryParseAndClean(intentNextPage)?.let {
                        newAccount = it
                    }

                    actionableNextPage = null
                } else {
                    accountViewModel.toast(
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
                        if (!isBaseRoute(nav.controller, Route.NewPost.base)) {
                            intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                                nav.newStack(buildNewPostRoute(draftMessage = it))
                            }

                            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                                nav.newStack(buildNewPostRoute(attachment = it))
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
                                Nip19Bech32.tryParseAndClean(uri)?.let {
                                    newAccount = it
                                }
                            } else {
                                scope.launch {
                                    delay(1000)
                                    accountViewModel.toast(
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

fun Context.getActivity(): MainActivity {
    if (this is MainActivity) return this
    return if (this is ContextWrapper) baseContext.getActivity() else getActivity()
}

private fun isSameRoute(
    currentRoute: String?,
    newRoute: String,
): Boolean {
    if (currentRoute == null) return false

    if (currentRoute == newRoute) {
        return true
    }

    if (newRoute.startsWith("Event/") && currentRoute.contains("/")) {
        if (newRoute.split("/")[1] == currentRoute.split("/")[1]) {
            return true
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
