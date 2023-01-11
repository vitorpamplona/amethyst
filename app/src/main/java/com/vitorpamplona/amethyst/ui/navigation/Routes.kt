package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.MessageScreen
import com.vitorpamplona.amethyst.ui.screen.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel


sealed class Route(
    val route: String,
    val icon: Int,
    val buildScreen: (AccountViewModel) -> @Composable (NavBackStackEntry) -> Unit
) {
    object Home : Route("Home", R.drawable.ic_home, { acc -> { _ -> HomeScreen(acc) } })
    object Search : Route("Search", R.drawable.ic_search, { acc -> { _ -> SearchScreen(acc) }})
    object Notification : Route("Notification", R.drawable.ic_notifications, { acc -> { _ -> NotificationScreen(acc) }})
    object Message : Route("Message", R.drawable.ic_dm, { acc -> { _ -> MessageScreen(acc) }})
    object Profile : Route("Profile", R.drawable.ic_profile, { acc -> { _ -> ProfileScreen(acc) }})
    object Lists : Route("Lists", R.drawable.ic_lists, { acc -> { _ -> ProfileScreen(acc) }})
    object Topics : Route("Topics", R.drawable.ic_topics, { acc -> { _ -> ProfileScreen(acc) }})
    object Bookmarks : Route("Bookmarks", R.drawable.ic_bookmarks, { acc -> { _ -> ProfileScreen(acc) }})
    object Moments : Route("Moments", R.drawable.ic_moments, { acc -> { _ -> ProfileScreen(acc) }})
}

val Routes = listOf(
    // bottom
    Route.Home,
    Route.Message,
    Route.Search,
    Route.Notification,

    //drawer
    Route.Profile,
    Route.Lists,
    Route.Topics,
    Route.Bookmarks,
    Route.Moments
)

@Composable
public fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}