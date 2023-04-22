package com.vitorpamplona.amethyst.ui.navigation

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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.dal.AdditiveFeedFilter
import com.vitorpamplona.amethyst.ui.dal.ChatroomListKnownFeedFilter
import com.vitorpamplona.amethyst.ui.dal.HomeNewThreadFeedFilter
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter

sealed class Route(
    val route: String,
    val icon: Int,
    val hasNewItems: (Account, NotificationCache, Set<com.vitorpamplona.amethyst.model.Note>) -> Boolean = { _, _, _ -> false },
    val arguments: List<NamedNavArgument> = emptyList()
) {
    val base: String
        get() = route.substringBefore("?")

    object Home : Route(
        route = "Home?scrollToTop={scrollToTop}&nip47={nip47}",
        icon = R.drawable.ic_home,
        arguments = listOf(
            navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false },
            navArgument("nip47") { type = NavType.StringType; nullable = true; defaultValue = null }
        ),
        hasNewItems = { accountViewModel, cache, newNotes -> HomeLatestItem.hasNewItems(accountViewModel, cache, newNotes) }
    )

    object Search : Route(
        route = "Search?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_globe,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false })
    )

    object Notification : Route(
        route = "Notification?scrollToTop={scrollToTop}",
        icon = R.drawable.ic_notifications,
        arguments = listOf(navArgument("scrollToTop") { type = NavType.BoolType; defaultValue = false }),
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
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )

    object Note : Route(
        route = "Note/{id}",
        icon = R.drawable.ic_moments,
        arguments = listOf(navArgument("id") { type = NavType.StringType })
    )

    object Hashtag : Route(
        route = "Hashtag/{id}",
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

    object Event : Route(
        route = "Event/{id}",
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

open class LatestItem {
    var newestItemPerAccount: Map<String, Note?> = mapOf()

    fun updateNewestItem(newNotes: Set<Note>, account: Account, filter: AdditiveFeedFilter<Note>): Note? {
        val newestItem = newestItemPerAccount[account.userProfile().pubkeyHex]

        if (newestItem == null) {
            newestItemPerAccount = newestItemPerAccount + Pair(
                account.userProfile().pubkeyHex,
                filter.feed().firstOrNull { it.createdAt() != null }
            )
        } else {
            newestItemPerAccount = newestItemPerAccount + Pair(
                account.userProfile().pubkeyHex,
                filter.sort(filter.applyFilter(newNotes) + newestItem).first()
            )
        }

        return newestItemPerAccount[account.userProfile().pubkeyHex]
    }
}

object HomeLatestItem : LatestItem() {
    fun hasNewItems(
        account: Account,
        cache: NotificationCache,
        newNotes: Set<Note>
    ): Boolean {
        val lastTime = cache.load("HomeFollows")
        HomeNewThreadFeedFilter.account = account

        val newestItem = updateNewestItem(newNotes, account, HomeNewThreadFeedFilter)

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
        NotificationFeedFilter.account = account

        val newestItem = updateNewestItem(newNotes, account, NotificationFeedFilter)

        return (newestItem?.createdAt() ?: 0) > lastTime
    }
}

object MessagesLatestItem {
    fun hasNewItems(
        account: Account,
        cache: NotificationCache,
        newNotes: Set<Note>
    ): Boolean {
        ChatroomListKnownFeedFilter.account = account

        val note = ChatroomListKnownFeedFilter.loadTop().firstOrNull {
            it.createdAt() != null && it.channel() == null && it.author != account.userProfile()
        } ?: return false

        val lastTime = cache.load("Room/${note.author?.pubkeyHex}")

        return (note.createdAt() ?: 0) > lastTime
    }
}
