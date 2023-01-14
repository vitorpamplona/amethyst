package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.ChatroomListScreen
import com.vitorpamplona.amethyst.ui.screen.ChatroomScreen
import com.vitorpamplona.amethyst.ui.screen.HomeScreen
import com.vitorpamplona.amethyst.ui.screen.NotificationScreen
import com.vitorpamplona.amethyst.ui.screen.ProfileScreen
import com.vitorpamplona.amethyst.ui.screen.SearchScreen
import com.vitorpamplona.amethyst.ui.screen.ThreadScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel


sealed class Route(
    val route: String,
    val icon: Int,
    val arguments: List<NamedNavArgument> = emptyList(),
    val buildScreen: (AccountViewModel, NavController) -> @Composable (NavBackStackEntry) -> Unit
) {
    object Home : Route("Home", R.drawable.ic_home, buildScreen = { acc, nav -> { _ -> HomeScreen(acc, nav) } })
    object Search : Route("Search", R.drawable.ic_search, buildScreen = { acc, nav -> { _ -> SearchScreen(acc, nav) }})
    object Notification : Route("Notification", R.drawable.ic_notifications,buildScreen = { acc, nav -> { _ -> NotificationScreen(acc, nav) }})
    object Message : Route("Message", R.drawable.ic_dm, buildScreen = { acc, nav -> { _ -> ChatroomListScreen(acc, nav) }})
    object Profile : Route("Profile", R.drawable.ic_profile, buildScreen = { acc, nav -> { _ -> ProfileScreen(acc) }})
    object Lists : Route("Lists", R.drawable.ic_lists, buildScreen = { acc, nav -> { _ -> ProfileScreen(acc) }})
    object Topics : Route("Topics", R.drawable.ic_topics, buildScreen = { acc, nav -> { _ -> ProfileScreen(acc) }})
    object Bookmarks : Route("Bookmarks", R.drawable.ic_bookmarks, buildScreen = { acc, nav -> { _ -> ProfileScreen(acc) }})
    object Moments : Route("Moments", R.drawable.ic_moments, buildScreen = { acc, nav -> { _ -> ProfileScreen(acc) }})

    object Note : Route("Note/{id}", R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, nav -> { ThreadScreen(it.arguments?.getString("id"), acc, nav) }}
    )

    object Room : Route("Room/{id}", R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType } ),
        buildScreen = { acc, nav -> { ChatroomScreen(it.arguments?.getString("id"), acc, nav) }}
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
    Route.Lists,
    Route.Topics,
    Route.Bookmarks,
    Route.Moments,

    //inner
    Route.Note,
    Route.Room
)

@Composable
public fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}