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

import android.os.Bundle
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size23dp
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size25dp
import com.vitorpamplona.quartz.events.ChatroomKeyable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Immutable
sealed class Route(
    val route: String,
    val base: String = route.substringBefore("?"),
    val icon: Int,
    val notifSize: Modifier = Modifier.size(Size23dp),
    val iconSize: Modifier = Modifier.size(Size20dp),
    val contentDescriptor: Int = R.string.route,
    val hasNewItems: (Account, Set<com.vitorpamplona.amethyst.model.Note>) -> Boolean = { _, _ ->
        false
    },
    val arguments: ImmutableList<NamedNavArgument> = persistentListOf(),
) {
    object Home :
        Route(
            route = "Home?nip47={nip47}",
            icon = R.drawable.ic_home,
            notifSize = Modifier.size(Size25dp),
            iconSize = Modifier.size(Size24dp),
            arguments =
                listOf(
                    navArgument("nip47") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
                    .toImmutableList(),
            contentDescriptor = R.string.route_home,
            hasNewItems = { accountViewModel, newNotes ->
                HomeLatestItem.hasNewItems(accountViewModel, newNotes)
            },
        )

    object Global :
        Route(
            route = "Global",
            icon = R.drawable.ic_globe,
            contentDescriptor = R.string.route_global,
        )

    object Search :
        Route(
            route = "Search",
            icon = R.drawable.ic_search,
            contentDescriptor = R.string.route_search,
        )

    object Video :
        Route(
            route = "Video",
            icon = R.drawable.ic_video,
            contentDescriptor = R.string.route_video,
        )

    object Discover :
        Route(
            route = "Discover",
            icon = R.drawable.ic_sensors,
            // hasNewItems = { accountViewModel, newNotes ->
            //    DiscoverLatestItem.hasNewItems(accountViewModel, newNotes)
            // },
            contentDescriptor = R.string.route_discover,
        )

    object Notification :
        Route(
            route = "Notification",
            icon = R.drawable.ic_notifications,
            hasNewItems = { accountViewModel, newNotes ->
                NotificationLatestItem.hasNewItems(accountViewModel, newNotes)
            },
            contentDescriptor = R.string.route_notifications,
        )

    object Message :
        Route(
            route = "Message",
            icon = R.drawable.ic_dm,
            hasNewItems = { accountViewModel, newNotes ->
                MessagesLatestItem.hasNewItems(accountViewModel, newNotes)
            },
            contentDescriptor = R.string.route_messages,
        )

    object BlockedUsers :
        Route(
            route = "BlockedUsers",
            icon = R.drawable.ic_security,
            contentDescriptor = R.string.route_security_filters,
        )

    object Bookmarks :
        Route(
            route = "Bookmarks",
            icon = R.drawable.ic_bookmarks,
            contentDescriptor = R.string.route_home,
        )

    object Drafts :
        Route(
            route = "Drafts",
            icon = R.drawable.ic_topics,
            contentDescriptor = R.string.drafts,
        )

    object Profile :
        Route(
            route = "User/{id}",
            icon = R.drawable.ic_profile,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Note :
        Route(
            route = "Note/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Hashtag :
        Route(
            route = "Hashtag/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Geohash :
        Route(
            route = "Geohash/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Community :
        Route(
            route = "Community/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Room :
        Route(
            route = "Room/{id}?message={message}",
            icon = R.drawable.ic_moments,
            arguments =
                listOf(
                    navArgument("id") { type = NavType.StringType },
                    navArgument("message") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                )
                    .toImmutableList(),
        )

    object RoomByAuthor :
        Route(
            route = "RoomByAuthor/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Channel :
        Route(
            route = "Channel/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Event :
        Route(
            route = "Event/{id}",
            icon = R.drawable.ic_moments,
            arguments = listOf(navArgument("id") { type = NavType.StringType }).toImmutableList(),
        )

    object Settings :
        Route(
            route = "Settings",
            icon = R.drawable.ic_settings,
        )

    companion object {
        val InvertedLayouts =
            setOf(
                Channel.route,
                Room.route,
                RoomByAuthor.route,
            )
    }
}

open class LatestItem {
    var newestItemPerAccount: Map<String, Note?> = mapOf()

    fun getNewestItem(account: Account): Note? {
        return newestItemPerAccount[account.userProfile().pubkeyHex]
    }

    fun clearNewestItem(account: Account) {
        val userHex = account.userProfile().pubkeyHex
        if (newestItemPerAccount.contains(userHex)) {
            newestItemPerAccount = newestItemPerAccount - userHex
        }
    }

    fun updateNewestItem(
        newNotes: Set<Note>,
        account: Account,
        filter: AdditiveFeedFilter<Note>,
    ): Note? {
        val newestItem = newestItemPerAccount[account.userProfile().pubkeyHex]

        // Block list got updated
        if (newestItem == null || !account.isAcceptable(newestItem)) {
            newestItemPerAccount =
                newestItemPerAccount +
                Pair(
                    account.userProfile().pubkeyHex,
                    filterMore(filter.feed(), account).firstOrNull { it.createdAt() != null },
                )
        } else {
            newestItemPerAccount =
                newestItemPerAccount +
                Pair(
                    account.userProfile().pubkeyHex,
                    filter.sort(filterMore(filter.applyFilter(newNotes), account) + newestItem).first(),
                )
        }

        return newestItemPerAccount[account.userProfile().pubkeyHex]
    }

    open fun filterMore(
        newItems: Set<Note>,
        account: Account,
    ): Set<Note> {
        return newItems
    }

    open fun filterMore(
        newItems: List<Note>,
        account: Account,
    ): List<Note> {
        return newItems
    }
}

object HomeLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        newNotes: Set<Note>,
    ): Boolean {
        checkNotInMainThread()

        val lastTime = account.loadLastRead("HomeFollows")

        val newestItem = updateNewestItem(newNotes, account, HomeNewThreadFeedFilter(account))

        return (newestItem?.createdAt() ?: 0) > lastTime
    }
}

object NotificationLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        newNotes: Set<Note>,
    ): Boolean {
        checkNotInMainThread()

        val lastTime = account.loadLastRead("Notification")

        val newestItem = updateNewestItem(newNotes, account, NotificationFeedFilter(account))

        return (newestItem?.createdAt() ?: 0) > lastTime
    }
}

object MessagesLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        newNotes: Set<Note>,
    ): Boolean {
        checkNotInMainThread()

        // Checks if the current newest item is still unread.
        // If so, there is no need to check anything else
        if (isNew(getNewestItem(account), account)) {
            return true
        }

        clearNewestItem(account)

        // gets the newest of the unread items
        val newestItem = updateNewestItem(newNotes, account, ChatroomListKnownFeedFilter(account))

        return isNew(newestItem, account)
    }

    fun isNew(
        it: Note?,
        account: Account,
    ): Boolean {
        if (it == null) return false

        val currentUser = account.userProfile().pubkeyHex
        val room = (it.event as? ChatroomKeyable)?.chatroomKey(currentUser)
        return if (room != null) {
            val lastRead = account.loadLastRead("Room/${room.hashCode()}")
            (it.createdAt() ?: 0) > lastRead
        } else {
            false
        }
    }

    override fun filterMore(
        newItems: Set<Note>,
        account: Account,
    ): Set<Note> {
        return newItems.filter { isNew(it, account) }.toSet()
    }

    override fun filterMore(
        newItems: List<Note>,
        account: Account,
    ): List<Note> {
        return newItems.filter { isNew(it, account) }
    }
}

fun getRouteWithArguments(navController: NavHostController): String? {
    val currentEntry = navController.currentBackStackEntry ?: return null
    return getRouteWithArguments(currentEntry.destination, currentEntry.arguments)
}

fun getRouteWithArguments(navState: State<NavBackStackEntry?>): String? {
    return navState.value?.let { getRouteWithArguments(it.destination, it.arguments) }
}

private fun getRouteWithArguments(
    destination: NavDestination,
    arguments: Bundle?,
): String? {
    var route = destination.route ?: return null
    arguments?.let { bundle ->
        destination.arguments.forEach {
            val key = it.key
            val value = it.value.type[bundle, key]?.toString()
            if (value == null) {
                val keyStart = route.indexOf("{$key}")
                // if it is a parameter, removes the complete segment `var={key}` and adjust connectors `#`,
                // `&` or `&`
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
