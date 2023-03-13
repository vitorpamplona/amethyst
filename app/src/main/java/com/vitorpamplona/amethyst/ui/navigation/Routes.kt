package com.vitorpamplona.amethyst.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
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

sealed class Route(
    val route: String,
    val icon: Int,
    val hasNewItems: (Account, NotificationCache, Context) -> Boolean = { _, _, _ -> false },
    val arguments: List<NamedNavArgument> = emptyList()
) {
    val base: String
        get() = route.substringBefore("?")

    object Home : Route(
        route = "Home?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_home,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false }),
        hasNewItems = { accountViewModel, cache, context -> homeHasNewItems(accountViewModel, cache, context) }
    )

    object Search : Route(
        route = "Search?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_globe,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false })
    )

    object Notification : Route(
        route = "Notification",
        icon = R.drawable.ic_notifications,
        hasNewItems = { accountViewModel, cache, context ->
            notificationHasNewItems(accountViewModel, cache, context)
        }
    )

    object Message : Route(
        route = "Message",
        icon = R.drawable.ic_dm,
        hasNewItems = { accountViewModel, cache, context ->
            messagesHasNewItems(accountViewModel, cache, context)
        }
    )

    object Filters : Route(
        route = "Filters",
        icon = R.drawable.ic_security
    )

    object Profile : Route(
        route = "User/{id}",
        icon = R.drawable.ic_profile,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )

    object Note : Route(
        route = "Note/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )

    object Room : Route(
        route = "Room/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )

    object Channel : Route(
        route = "Channel/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )
}

// **
// *  Functions below only exist because we have not broken the datasource classes into backend and frontend.
// **
@Composable
fun currentRoute(navController: NavHostController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

private fun homeHasNewItems(account: Account, cache: NotificationCache, context: Context): Boolean {
    val lastTime = cache.load("HomeFollows", context)

    HomeNewThreadFeedFilter.account = account

    return (
        HomeNewThreadFeedFilter.feed().firstOrNull { it.createdAt() != null }?.createdAt()
            ?: 0
        ) > lastTime
}

private fun notificationHasNewItems(
    account: Account,
    cache: NotificationCache,
    context: Context
): Boolean {
    val lastTime = cache.load("Notification", context)

    NotificationFeedFilter.account = account

    return (
        NotificationFeedFilter.feed().firstOrNull { it.createdAt() != null }?.createdAt()
            ?: 0
        ) > lastTime
}

private fun messagesHasNewItems(
    account: Account,
    cache: NotificationCache,
    context: Context
): Boolean {
    ChatroomListKnownFeedFilter.account = account

    val note = ChatroomListKnownFeedFilter.feed().firstOrNull {
        it.createdAt() != null && it.channel() == null && it.author != account.userProfile()
    } ?: return false

    val lastTime = cache.load("Room/${note.author?.pubkeyHex}", context)

    return (note.createdAt() ?: 0) > lastTime
}
