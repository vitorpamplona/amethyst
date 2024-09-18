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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import com.vitorpamplona.amethyst.ui.components.DisplayErrorMessages
import com.vitorpamplona.amethyst.ui.components.DisplayNotifyMessages
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountSwitcherAndLeftDrawerLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadRedirectScreen
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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.search.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SecurityFiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.video.VideoScreen
import com.vitorpamplona.amethyst.ui.screen.loggedOff.AddAccountDialog
import com.vitorpamplona.amethyst.ui.uriToRoute
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            composable(
                Route.Home.route,
                Route.Home.arguments,
            ) {
                val nip47 = it.arguments?.getString("nip47")

                HomeScreen(accountViewModel, nav, nip47)

                if (nip47 != null) {
                    LaunchedEffect(key1 = Unit) {
                        launch {
                            delay(1000)
                            it.arguments?.remove("nip47")
                        }
                    }
                }
            }

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
    val activity = LocalContext.current.getActivity()
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
                actionableNextPage?.let {
                    val currentRoute = getRouteWithArguments(nav.controller)
                    if (!isSameRoute(currentRoute, it)) {
                        nav.newStack(it)
                    }
                    actionableNextPage = null
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
                val uri = intent.data.toString()
                if (uri.isNotBlank()) {
                    // navigation functions
                    val newPage = uriToRoute(uri)

                    if (newPage != null) {
                        val currentRoute = getRouteWithArguments(nav.controller)
                        if (!isSameRoute(currentRoute, newPage)) {
                            nav.newStack(newPage)
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
        activity.addOnNewIntentListener(consumer)
        onDispose { activity.removeOnNewIntentListener(consumer) }
    }

    if (newAccount != null) {
        AddAccountDialog(newAccount, accountStateViewModel) { newAccount = null }
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

val slideInHorizontallyFromEnd = slideInHorizontally(animationSpec = tween(), initialOffsetX = { it })
val slideOutHorizontallyToEnd = slideOutHorizontally(animationSpec = tween(), targetOffsetX = { it })

val scaleIn = scaleIn(animationSpec = tween(), initialScale = 0.9f)
val scaleOut = scaleOut(animationSpec = tween(), targetScale = 0.9f)
