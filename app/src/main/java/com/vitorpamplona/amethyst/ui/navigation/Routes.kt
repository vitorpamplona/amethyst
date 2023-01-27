package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrNotificationDataSource
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.FiltersScreen
import com.vitorpamplona.amethyst.ui.screen.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.NostrChatroomListKnownFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NostrHomeFeedViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel


sealed class Route(
    val route: String,
    val icon: Int,
    val hasNewItems: @Composable (LocalCache, NotificationCache) -> Boolean = @Composable { _,_ -> false },
    val arguments: List<NamedNavArgument> = emptyList(),
    val buildScreen: (AccountViewModel, AccountStateViewModel, NavController) -> @Composable (NavBackStackEntry) -> Unit
) {
    object Home : Route("Home", R.drawable.ic_home,
        hasNewItems = { _, cache -> homeHasNewItems(cache) },
        buildScreen = { acc, accSt, nav -> { _ -> HomeScreen(acc, nav) } }
    )
    object Search : Route("Search", R.drawable.ic_search,
        buildScreen = { acc, accSt, nav -> { _ -> SearchScreen(acc, nav) }}
    )
    object Notification : Route("Notification", R.drawable.ic_notifications,
        hasNewItems = { _, cache -> notificationHasNewItems(cache) },
        buildScreen = { acc, accSt, nav -> { _ -> NotificationScreen(acc, nav) }}
    )

    object Message : Route("Message", R.drawable.ic_dm,
        hasNewItems = { _, cache -> messagesHasNewItems(cache) },
        buildScreen = { acc, accSt, nav -> { _ -> ChatroomListScreen(acc, nav) }}
    )

    object Filters : Route("Filters", R.drawable.ic_dm,
        buildScreen = { acc, accSt, nav -> { _ -> FiltersScreen(acc, nav) }}
    )

    object Profile : Route("User/{id}", R.drawable.ic_profile,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, accSt, nav -> { ProfileScreen(it.arguments?.getString("id"), acc, nav) }}
    )

    object Note : Route("Note/{id}", R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, accSt, nav -> { ThreadScreen(it.arguments?.getString("id"), acc, nav) }}
    )

    object Room : Route("Room/{id}", R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, accSt, nav -> { ChatroomScreen(it.arguments?.getString("id"), acc, nav) }}
    )

    object Channel : Route("Channel/{id}", R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, accSt, nav -> { ChannelScreen(it.arguments?.getString("id"), acc, accSt, nav) }}
    )
}

val Routes = listOf(
    // bottom
    Route.Home,
    Route.Message,
    Route.Search,
    Route.Notification,

    //drawer
    Route.Profile,
    Route.Note,
    Route.Room,
    Route.Channel,
    Route.Filters
)

//**
//*  Functions below only exist because we have not broken the datasource classes into backend and frontend.
//**
@Composable
public fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

@Composable
private fun homeHasNewItems(cache: NotificationCache): Boolean {
    val context = LocalContext.current.applicationContext
    val lastTimeFollows = cache.load("HomeFollows", context)

    val homeFeed = NostrHomeDataSource.feed().take(100)

    val hasNewInFollows = homeFeed.filter {
        it.event is RepostEvent || it.replyTo == null || it.replyTo?.size == 0
    }.filter {
        (it.event?.createdAt ?: 0) > lastTimeFollows
    }.isNotEmpty()

    return hasNewInFollows
}

@Composable
private fun notificationHasNewItems(cache: NotificationCache): Boolean {
    val context = LocalContext.current.applicationContext
    val lastTime = cache.load("Notification", context)
    return NostrNotificationDataSource.loadTop()
        .filter { it.event != null && it.event!!.createdAt > lastTime }
        .isNotEmpty()
}

@Composable
private fun messagesHasNewItems(cache: NotificationCache): Boolean {
    val context = LocalContext.current.applicationContext
    return NostrChatroomListDataSource.feed().take(100).filter {
        // only for known sources
        val me = NostrChatroomListDataSource.account.userProfile()
        it.channel != null || me.hasSentMessagesTo(it.author)
    }.filter {
        val lastTime = if (it.channel != null) {
            cache.load("Channel/${it.channel!!.idHex}", context)
        } else {
            cache.load("Room/${it.author?.pubkeyHex}", context)
        }

        NostrChatroomListDataSource.account.isAcceptable(it) && it.event != null && it.event!!.createdAt > lastTime
    }.isNotEmpty()
}