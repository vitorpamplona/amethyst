/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.navigation.bottombars

import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.isDebug
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.serialization.Serializable

/**
 * Stable identifiers for every drawer destination that the user can pin to the bottom bar.
 * Order in this enum has no semantic meaning — the user picks a subset and an order at runtime.
 */
@Serializable
enum class NavBarItem {
    HOME,
    MESSAGES,
    VIDEO,
    DISCOVER,
    NOTIFICATIONS,
    PROFILE,
    MY_LISTS,
    BOOKMARKS,
    WEB_BOOKMARKS,
    DRAFTS,
    INTEREST_SETS,
    EMOJI_PACKS,
    WALLET,
    COMMUNITIES,
    ARTICLES,
    PICTURES,
    SHORTS,
    PUBLIC_CHATS,
    FOLLOW_PACKS,
    LIVE_STREAMS,
    NESTS,
    MEETING_ROOMS,
    LONGS,
    POLLS,
    BADGES,
    PRODUCTS,
    EMOJI_SETS,
    SETTINGS,
}

data class NavBarItemDef(
    val id: NavBarItem,
    val labelRes: Int,
    val icon: MaterialSymbol,
    val resolveRoute: (AccountViewModel) -> Route,
)

val NavBarCatalog: Map<NavBarItem, NavBarItemDef> =
    linkedMapOf(
        NavBarItem.HOME to
            NavBarItemDef(
                id = NavBarItem.HOME,
                labelRes = R.string.route_home,
                icon = MaterialSymbols.Home,
                resolveRoute = { Route.Home },
            ),
        NavBarItem.MESSAGES to
            NavBarItemDef(
                id = NavBarItem.MESSAGES,
                labelRes = R.string.route_messages,
                icon = MaterialSymbols.Mail,
                resolveRoute = { Route.Message },
            ),
        NavBarItem.VIDEO to
            NavBarItemDef(
                id = NavBarItem.VIDEO,
                labelRes = R.string.route_video,
                icon = MaterialSymbols.Subscriptions,
                resolveRoute = { Route.Video },
            ),
        NavBarItem.DISCOVER to
            NavBarItemDef(
                id = NavBarItem.DISCOVER,
                labelRes = R.string.route_discover,
                icon = MaterialSymbols.Sensors,
                resolveRoute = { Route.Discover },
            ),
        NavBarItem.NOTIFICATIONS to
            NavBarItemDef(
                id = NavBarItem.NOTIFICATIONS,
                labelRes = R.string.route_notifications,
                icon = MaterialSymbols.Notifications,
                resolveRoute = { Route.Notification() },
            ),
        NavBarItem.PROFILE to
            NavBarItemDef(
                id = NavBarItem.PROFILE,
                labelRes = R.string.profile,
                icon = MaterialSymbols.AccountCircle,
                resolveRoute = { Route.Profile(it.userProfile().pubkeyHex) },
            ),
        NavBarItem.MY_LISTS to
            NavBarItemDef(
                id = NavBarItem.MY_LISTS,
                labelRes = R.string.my_lists,
                icon = MaterialSymbols.AutoMirrored.FormatListBulleted,
                resolveRoute = { Route.Lists },
            ),
        NavBarItem.BOOKMARKS to
            NavBarItemDef(
                id = NavBarItem.BOOKMARKS,
                labelRes = R.string.bookmarks,
                icon = MaterialSymbols.CollectionsBookmark,
                resolveRoute = { Route.BookmarkGroups },
            ),
        NavBarItem.WEB_BOOKMARKS to
            NavBarItemDef(
                id = NavBarItem.WEB_BOOKMARKS,
                labelRes = R.string.web_bookmarks,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.WebBookmarks },
            ),
        NavBarItem.DRAFTS to
            NavBarItemDef(
                id = NavBarItem.DRAFTS,
                labelRes = R.string.drafts,
                icon = MaterialSymbols.Drafts,
                resolveRoute = { Route.Drafts },
            ),
        NavBarItem.INTEREST_SETS to
            NavBarItemDef(
                id = NavBarItem.INTEREST_SETS,
                labelRes = R.string.interest_sets_title,
                icon = MaterialSymbols.Tag,
                resolveRoute = { Route.InterestSets },
            ),
        NavBarItem.EMOJI_PACKS to
            NavBarItemDef(
                id = NavBarItem.EMOJI_PACKS,
                labelRes = R.string.manage_emoji_packs,
                icon = MaterialSymbols.EmojiEmotions,
                resolveRoute = { Route.EmojiPacks },
            ),
        NavBarItem.WALLET to
            NavBarItemDef(
                id = NavBarItem.WALLET,
                labelRes = R.string.wallet,
                icon = MaterialSymbols.AccountBalanceWallet,
                resolveRoute = { Route.Wallet },
            ),
        NavBarItem.COMMUNITIES to
            NavBarItemDef(
                id = NavBarItem.COMMUNITIES,
                labelRes = R.string.communities,
                icon = MaterialSymbols.Groups,
                resolveRoute = { Route.Communities },
            ),
        NavBarItem.ARTICLES to
            NavBarItemDef(
                id = NavBarItem.ARTICLES,
                labelRes = R.string.discover_reads,
                icon = MaterialSymbols.AutoMirrored.Article,
                resolveRoute = { Route.Articles },
            ),
        NavBarItem.PICTURES to
            NavBarItemDef(
                id = NavBarItem.PICTURES,
                labelRes = R.string.pictures,
                icon = MaterialSymbols.Photo,
                resolveRoute = { Route.Pictures },
            ),
        NavBarItem.SHORTS to
            NavBarItemDef(
                id = NavBarItem.SHORTS,
                labelRes = R.string.shorts,
                icon = MaterialSymbols.PlayCircle,
                resolveRoute = { Route.Shorts },
            ),
        NavBarItem.PUBLIC_CHATS to
            NavBarItemDef(
                id = NavBarItem.PUBLIC_CHATS,
                labelRes = R.string.public_chats,
                icon = MaterialSymbols.AutoMirrored.Chat,
                resolveRoute = { Route.PublicChats },
            ),
        NavBarItem.FOLLOW_PACKS to
            NavBarItemDef(
                id = NavBarItem.FOLLOW_PACKS,
                labelRes = R.string.follow_packs,
                icon = MaterialSymbols.CollectionsBookmark,
                resolveRoute = { Route.FollowPacks },
            ),
        NavBarItem.LIVE_STREAMS to
            NavBarItemDef(
                id = NavBarItem.LIVE_STREAMS,
                labelRes = R.string.live_streams,
                icon = MaterialSymbols.Sensors,
                resolveRoute = { Route.LiveStreams },
            ),
        NavBarItem.NESTS to
            NavBarItemDef(
                id = NavBarItem.NESTS,
                labelRes = R.string.nests,
                icon = MaterialSymbols.Mic,
                resolveRoute = { Route.Nests },
            ),
        NavBarItem.MEETING_ROOMS to
            NavBarItemDef(
                id = NavBarItem.MEETING_ROOMS,
                labelRes = R.string.meeting_rooms,
                icon = MaterialSymbols.Groups,
                resolveRoute = { Route.MeetingRooms },
            ),
        NavBarItem.LONGS to
            NavBarItemDef(
                id = NavBarItem.LONGS,
                labelRes = R.string.longs,
                icon = MaterialSymbols.SmartDisplay,
                resolveRoute = { Route.Longs },
            ),
        NavBarItem.POLLS to
            NavBarItemDef(
                id = NavBarItem.POLLS,
                labelRes = R.string.polls,
                icon = MaterialSymbols.Poll,
                resolveRoute = { Route.Polls },
            ),
        NavBarItem.BADGES to
            NavBarItemDef(
                id = NavBarItem.BADGES,
                labelRes = R.string.badges,
                icon = MaterialSymbols.MilitaryTech,
                resolveRoute = { Route.Badges },
            ),
        NavBarItem.PRODUCTS to
            NavBarItemDef(
                id = NavBarItem.PRODUCTS,
                labelRes = R.string.discover_marketplace,
                icon = MaterialSymbols.Storefront,
                resolveRoute = { Route.Products },
            ),
        NavBarItem.EMOJI_SETS to
            NavBarItemDef(
                id = NavBarItem.EMOJI_SETS,
                labelRes = R.string.emoji_sets,
                icon = MaterialSymbols.EmojiEmotions,
                resolveRoute = { Route.BrowseEmojiSets },
            ),
        NavBarItem.SETTINGS to
            NavBarItemDef(
                id = NavBarItem.SETTINGS,
                labelRes = R.string.settings,
                icon = MaterialSymbols.Settings,
                resolveRoute = { Route.AllSettings },
            ),
    )

val DefaultBottomBarItems: List<NavBarItem> =
    listOf(
        NavBarItem.HOME,
        NavBarItem.MESSAGES,
        NavBarItem.VIDEO,
        NavBarItem.DISCOVER,
        NavBarItem.NOTIFICATIONS,
    )

// Ordered membership lists for each drawer section. The drawer renders these by looking up
// each id in NavBarCatalog, so adding a new screen only requires editing the catalog + the
// matching section list below — not two separate files.
val DrawerNavigateItems: List<NavBarItem> =
    listOf(
        NavBarItem.HOME,
        NavBarItem.MESSAGES,
        NavBarItem.VIDEO,
        NavBarItem.DISCOVER,
        NavBarItem.NOTIFICATIONS,
    )

val DrawerYouItems: List<NavBarItem> =
    listOf(
        NavBarItem.PROFILE,
        NavBarItem.MY_LISTS,
        NavBarItem.BOOKMARKS,
        NavBarItem.WEB_BOOKMARKS,
        NavBarItem.DRAFTS,
        NavBarItem.INTEREST_SETS,
        NavBarItem.EMOJI_PACKS,
        NavBarItem.WALLET,
    )

val DrawerFeedsItems: List<NavBarItem> =
    listOfNotNull(
        NavBarItem.COMMUNITIES,
        NavBarItem.ARTICLES,
        NavBarItem.PICTURES,
        NavBarItem.SHORTS,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.FOLLOW_PACKS,
        NavBarItem.LIVE_STREAMS,
        if (isDebug) NavBarItem.NESTS else null,
        if (isDebug) NavBarItem.MEETING_ROOMS else null,
        NavBarItem.LONGS,
        NavBarItem.POLLS,
        NavBarItem.BADGES,
        NavBarItem.PRODUCTS,
        NavBarItem.EMOJI_SETS,
    )
