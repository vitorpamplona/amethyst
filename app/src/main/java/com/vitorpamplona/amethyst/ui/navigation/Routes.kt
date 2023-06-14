package com.vitorpamplona.amethyst.ui.navigation

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
sealed class Route(
    val route: String,
    val icon: Int,
    val hasNewItems: (Account, NotificationCache, Set<com.vitorpamplona.amethyst.model.Note>) -> Boolean = { _, _, _ -> false },
    val arguments: ImmutableList<NamedNavArgument> = persistentListOf()
) {
    val base: String
        get() = route.substringBefore("?")

    object Home : Route(
        route = "Home?scrollToTop={scrollToTop}&nip47={nip47}",
        icon = R.drawable.ic_home,
        arguments = listOf(
            navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false },
            navArgument("nip47") { type = NavType.StringType; nullable = true; defaultValue = null }
        ).toImmutableList(),
        hasNewItems = { accountViewModel, cache, newNotes -> HomeLatestItem.hasNewItems(accountViewModel, cache, newNotes) }
    )

    object Search : Route(
        route = "Search?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_globe,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false }).toImmutableList()
    )

    object Video : Route(
        route = "Video?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_video,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false }).toImmutableList()
    )

    object Notification : Route(
        route = "Notification?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_notifications,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false }).toImmutableList(),
        hasNewItems = { accountViewModel, cache, newNotes -> NotificationLatestItem.hasNewItems(accountViewModel, cache, newNotes) }
    )

    object Message : Route(
        route = "Message",
        icon = R.drawable.ic_dm,
        hasNewItems = { accountViewModel, cache, newNotes -> MessagesLatestItem.hasNewItems(accountViewModel, cache, newNotes) }
    )

    object BlockedUsers : Route(
        route = "BlockedUsers",
        icon = R.drawable.ic_security
    )

    object Bookmarks : Route(
        route = "Bookmarks",
        icon = R.drawable.ic_bookmarks
    )

    object Profile : Route(
        route = "User/{id}",
        icon = R.drawable.ic_profile,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
    )

    object Note : Route(
        route = "Note/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
    )

    object Hashtag : Route(
        route = "Hashtag/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
    )

    object Room : Route(
        route = "Room/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
    )

    object Channel : Route(
        route = "Channel/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
    )

    object Event : Route(
        route = "Event/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList()
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

open class LatestItem {
    var newestItemPerAccount: Map<String, Note?> = mapOf()

    fun updateNewestItem(newNotes: Set<Note>, account: Account, filter: AdditiveFeedFilter<Note>): Note? {
        val newestItem = newestItemPerAccount[account.userProfile().pubkeyHex]

        if (newestItem == null) {
            newestItemPerAccount = newestItemPerAccount + Pair(
                account.userProfile().pubkeyHex,
                filterMore(filter.feed()).firstOrNull { it.createdAt() != null }
            )
        } else {
            newestItemPerAccount = newestItemPerAccount + Pair(
                account.userProfile().pubkeyHex,
                filter.sort(filterMore(filter.applyFilter(newNotes)) + newestItem).first()
            )
        }

        return newestItemPerAccount[account.userProfile().pubkeyHex]
    }

    open fun filterMore(newItems: Set<Note>): Set<Note> {
        return newItems
    }

    open fun filterMore(newItems: List<Note>): List<Note> {
        return newItems
    }
}

object HomeLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        cache: NotificationCache,
        newNotes: Set<Note>
    ): Boolean {
        val lastTime = cache.load("HomeFollows")

        val newestItem = updateNewestItem(newNotes, account, HomeNewThreadFeedFilter(account))

        return (newestItem?.createdAt() ?: 0) > lastTime
    }
}

object NotificationLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        cache: NotificationCache,
        newNotes: Set<Note>
    ): Boolean {
        val lastTime = cache.load("Notification")

        val newestItem = updateNewestItem(newNotes, account, NotificationFeedFilter(account))

        return (newestItem?.createdAt() ?: 0) > lastTime
    }
}

object MessagesLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        cache: NotificationCache,
        newNotes: Set<Note>
    ): Boolean {
        val newestItem = updateNewestItem(newNotes, account, ChatroomListKnownFeedFilter(account))

        val roomUserHex = (newestItem?.event as? PrivateDmEvent)?.talkingWith(account.userProfile().pubkeyHex)

        val lastTime = cache.load("Room/$roomUserHex")

        return (newestItem?.createdAt() ?: 0) > lastTime
    }

    override fun filterMore(newItems: Set<Note>): Set<Note> {
        return newItems.filter { it.event is PrivateDmEvent }.toSet()
    }

    override fun filterMore(newItems: List<Note>): List<Note> {
        return newItems.filter { it.event is PrivateDmEvent }
    }
}

fun getRouteWithArguments(navController: NavHostController): String? {
    val currentEntry = navController.currentBackStackEntry ?: return null
    return getRouteWithArguments(currentEntry.destination, currentEntry.arguments)
}

private fun getRouteWithArguments(
    destination: NavDestination,
    arguments: Bundle?
): String? {
    var route = destination.route ?: return null
    arguments?.let { bundle ->
        destination.arguments.keys.forEach { key ->
            val value = destination.arguments[key]?.type?.get(bundle, key)?.toString()
            if (value == null) {
                val keyStart = route.indexOf("{$key}")
                // if it is a parameter, removes the complete segment `var={key}` and adjust connectors `#`, `&` or `&`
                if (keyStart > 0 && route[keyStart - 1] == '=') {
                    val end = keyStart + "{$key}".length
                    var start = keyStart
                    for (i in keyStart downTo 0) {
                        if (route[i] == '#' || route[i] == '?' || route[i] == '&') {
                            start = i + 1
                            break
                        }
                    }
                    if (end < route.length && route[end] == '&') {
                        route = route.removeRange(start, end + 1)
                    } else if (end < route.length && route[end] == '#') {
                        route = route.removeRange(start - 1, end)
                    } else if (end == route.length) {
                        route = route.removeRange(start - 1, end)
                    } else {
                        route = route.removeRange(start, end)
                    }
                } else {
                    route = route.replaceFirst("{$key}", "")
                }
            } else {
                route = route.replaceFirst("{$key}", value)
            }
        }
    }
    return route
}
