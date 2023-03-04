package com.vitorpamplona.amethyst.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChannelScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.FiltersScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.ThreadScreen


sealed class Route(
    val route: String,
    val icon: Int,
    val hasNewItems: (Account, NotificationCache, Context) -> Boolean = { _,_,_ -> false },
    val arguments: List<NamedNavArgument> = emptyList(),
    val buildScreen: (AccountViewModel, AccountStateViewModel, NavController) -> @Composable (NavBackStackEntry) -> Unit
) {
    object Home : Route("Home", R.drawable.ic_home,
        hasNewItems = { acc, cache, ctx -> homeHasNewItems(acc, cache, ctx) },
        buildScreen = { acc, accSt, nav -> { _ -> HomeScreen(acc, nav) } }
    )
    object Search : Route("Search", R.drawable.ic_globe,
        buildScreen = { acc, accSt, nav -> { _ -> SearchScreen(acc, nav) }}
    )
    object Notification : Route("Notification", R.drawable.ic_notifications,
        hasNewItems = { acc, cache, ctx -> notificationHasNewItems(acc, cache, ctx) },
        buildScreen = { acc, accSt, nav -> { _ -> NotificationScreen(acc, nav) }}
    )

    object Message : Route("Message", R.drawable.ic_dm,
        hasNewItems = { acc, cache, ctx -> messagesHasNewItems(acc, cache, ctx) },
        buildScreen = { acc, accSt, nav -> { _ -> ChatroomListScreen(acc, nav) }}
    )

    object Filters : Route("Filters", R.drawable.ic_security,
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

private fun homeHasNewItems(account: Account, cache: NotificationCache, context: Context): Boolean {
    val lastTime = cache.load("HomeFollows", context)

    HomeNewThreadFeedFilter.account = account

    return (HomeNewThreadFeedFilter.feed().firstOrNull { it.createdAt() != null }?.createdAt() ?: 0) > lastTime
}

private fun notificationHasNewItems(account: Account, cache: NotificationCache, context: Context): Boolean {
    val lastTime = cache.load("Notification", context)

    NotificationFeedFilter.account = account

    return (NotificationFeedFilter.feed().firstOrNull { it.createdAt() != null }?.createdAt() ?: 0) > lastTime
}

private fun messagesHasNewItems(account: Account, cache: NotificationCache, context: Context): Boolean {
    ChatroomListKnownFeedFilter.account = account

    val note = ChatroomListKnownFeedFilter.feed().firstOrNull {
        it.createdAt() != null && it.channel() == null && it.author != account.userProfile()
    } ?: return false

    val lastTime = cache.load("Room/${note.author?.pubkeyHex}", context)

    return (note.createdAt() ?: 0) > lastTime
}