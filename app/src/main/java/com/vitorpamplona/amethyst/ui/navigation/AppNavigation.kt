package com.vitorpamplona.amethyst.ui.navigation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
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
import androidx.core.util.Consumer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListNewFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverChatFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverCommunityFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrDiscoverLiveFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeRepliesFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrVideoFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.BookmarkListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomScreenByAuthor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.CommunityScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DiscoverScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.GeoHashScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HashtagScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HiddenUsersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.LoadRedirectScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SettingsScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.VideoScreen
import com.vitorpamplona.amethyst.ui.uriToRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    homeFeedViewModel: NostrHomeFeedViewModel,
    repliesFeedViewModel: NostrHomeRepliesFeedViewModel,
    knownFeedViewModel: NostrChatroomListKnownFeedViewModel,
    newFeedViewModel: NostrChatroomListNewFeedViewModel,
    videoFeedViewModel: NostrVideoFeedViewModel,
    discoveryLiveFeedViewModel: NostrDiscoverLiveFeedViewModel,
    discoveryCommunityFeedViewModel: NostrDiscoverCommunityFeedViewModel,
    discoveryChatFeedViewModel: NostrDiscoverChatFeedViewModel,
    notifFeedViewModel: NotificationViewModel,
    userReactionsStatsModel: UserReactionsViewModel,

    navController: NavHostController,
    accountViewModel: AccountViewModel,
    sharedPreferencesViewModel: SharedPreferencesViewModel
) {
    val scope = rememberCoroutineScope()
    val nav = remember {
        { route: String ->
            scope.launch {
                if (getRouteWithArguments(navController) != route) {
                    navController.navigate(route)
                }
            }
            Unit
        }
    }

    NavHost(
        navController,
        startDestination = Route.Home.route,
        enterTransition = { fadeIn(animationSpec = tween(200)) },
        exitTransition = { fadeOut(animationSpec = tween(200)) }
    ) {
        Route.Home.let { route ->
            composable(route.route, route.arguments, content = { it ->
                val nip47 = it.arguments?.getString("nip47")

                HomeScreen(
                    homeFeedViewModel = homeFeedViewModel,
                    repliesFeedViewModel = repliesFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    nip47 = nip47
                )

                if (nip47 != null) {
                    LaunchedEffect(key1 = Unit) {
                        launch {
                            delay(1000)
                            it.arguments?.remove("nip47")
                        }
                    }
                }
            })
        }

        composable(
            Route.Message.route,
            content = {
                ChatroomListScreen(
                    knownFeedViewModel,
                    newFeedViewModel,
                    accountViewModel,
                    nav
                )
            }
        )

        Route.Video.let { route ->
            composable(route.route, route.arguments, content = {
                VideoScreen(
                    videoFeedView = videoFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Discover.let { route ->
            composable(route.route, route.arguments, content = {
                DiscoverScreen(
                    discoveryLiveFeedViewModel = discoveryLiveFeedViewModel,
                    discoveryCommunityFeedViewModel = discoveryCommunityFeedViewModel,
                    discoveryChatFeedViewModel = discoveryChatFeedViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Search.let { route ->
            composable(route.route, route.arguments, content = {
                SearchScreen(
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Notification.let { route ->
            composable(route.route, route.arguments, content = {
                NotificationScreen(
                    notifFeedViewModel = notifFeedViewModel,
                    userReactionsStatsModel = userReactionsStatsModel,
                    sharedPreferencesViewModel = sharedPreferencesViewModel,
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        composable(Route.BlockedUsers.route, content = { HiddenUsersScreen(accountViewModel, nav) })
        composable(Route.Bookmarks.route, content = { BookmarkListScreen(accountViewModel, nav) })

        Route.Profile.let { route ->
            composable(route.route, route.arguments, content = {
                ProfileScreen(
                    userId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Note.let { route ->
            composable(route.route, route.arguments, content = {
                ThreadScreen(
                    noteId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Hashtag.let { route ->
            composable(route.route, route.arguments, content = {
                HashtagScreen(
                    tag = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Geohash.let { route ->
            composable(route.route, route.arguments, content = {
                GeoHashScreen(
                    tag = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Community.let { route ->
            composable(route.route, route.arguments, content = {
                CommunityScreen(
                    aTagHex = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Room.let { route ->
            composable(route.route, route.arguments, content = {
                ChatroomScreen(
                    roomId = it.arguments?.getString("id"),
                    draftMessage = it.arguments?.getString("message"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.RoomByAuthor.let { route ->
            composable(route.route, route.arguments, content = {
                ChatroomScreenByAuthor(
                    authorPubKeyHex = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Channel.let { route ->
            composable(route.route, route.arguments, content = {
                ChannelScreen(
                    channelId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            })
        }

        Route.Event.let { route ->
            composable(route.route, route.arguments, content = {
                LoadRedirectScreen(
                    eventId = it.arguments?.getString("id"),
                    accountViewModel = accountViewModel,
                    navController = navController
                )
            })
        }

        Route.Settings.let { route ->
            composable(route.route, route.arguments, content = {
                SettingsScreen(
                    sharedPreferencesViewModel
                )
            })
        }
    }

    val activity = LocalContext.current.getActivity()
    var actionableNextPage by remember {
        mutableStateOf(uriToRoute(activity.intent?.data?.toString()?.ifBlank { null }))
    }
    actionableNextPage?.let {
        LaunchedEffect(it) {
            navController.navigate(it) {
                popUpTo(Route.Home.route)
                launchSingleTop = true
            }
        }
        actionableNextPage = null
    }

    DisposableEffect(activity) {
        val consumer = Consumer<Intent> { intent ->
            val uri = intent?.data?.toString()
            val newPage = uriToRoute(uri)

            newPage?.let { route ->
                val currentRoute = getRouteWithArguments(navController)
                if (!isSameRoute(currentRoute, route)) {
                    navController.navigate(route) {
                        popUpTo(Route.Home.route)
                        launchSingleTop = true
                    }
                }
            }
        }
        activity.addOnNewIntentListener(consumer)
        onDispose {
            activity.removeOnNewIntentListener(consumer)
        }
    }
}

fun Context.getActivity(): MainActivity {
    if (this is MainActivity) return this
    return if (this is ContextWrapper) baseContext.getActivity() else getActivity()
}

private fun isSameRoute(currentRoute: String?, newRoute: String): Boolean {
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
