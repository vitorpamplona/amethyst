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

import android.os.Build
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
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
    SCHEDULED_POSTS,
    INTEREST_SETS,
    BLOSSOM_DATA,
    EMOJI_PACKS,
    WALLET,
    NOSTR_SIGNER,
    COMMUNITIES,
    ARTICLES,
    PICTURES,
    WORKOUTS,
    GIT_REPOSITORIES,
    SOFTWARE_APPS,
    NAPPLETS,
    NSITES,
    BROWSER,
    FAVORITE_APPS,
    CALENDARS,
    CALENDAR_COLLECTIONS,
    SHORTS,
    MUSIC_TRACKS,
    MUSIC_PLAYLISTS,
    PODCAST_EPISODES,
    PODCASTS,
    PUBLIC_CHATS,
    RELAY_GROUPS,
    AGENT_CONSOLE,
    BUZZ_DMS,
    CONCORD,
    GEOHASH_CHATS,
    FOLLOW_PACKS,
    LIVE_STREAMS,
    NESTS,
    LONGS,
    POLLS,
    BADGES,
    PRODUCTS,
    EMOJI_SETS,
    SETTINGS,
    FAVORITE_ALGO_FEEDS,
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
                resolveRoute = { Route.Discover() },
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
        NavBarItem.SCHEDULED_POSTS to
            NavBarItemDef(
                id = NavBarItem.SCHEDULED_POSTS,
                labelRes = R.string.scheduled_posts,
                icon = MaterialSymbols.Schedule,
                resolveRoute = { Route.ScheduledPosts },
            ),
        NavBarItem.INTEREST_SETS to
            NavBarItemDef(
                id = NavBarItem.INTEREST_SETS,
                labelRes = R.string.interest_sets_title,
                icon = MaterialSymbols.Tag,
                resolveRoute = { Route.InterestSets },
            ),
        NavBarItem.FAVORITE_ALGO_FEEDS to
            NavBarItemDef(
                id = NavBarItem.FAVORITE_ALGO_FEEDS,
                labelRes = R.string.favorite_dvms_title,
                icon = MaterialSymbols.AutoAwesome,
                resolveRoute = { Route.EditFavoriteAlgoFeeds },
            ),
        NavBarItem.BLOSSOM_DATA to
            NavBarItemDef(
                id = NavBarItem.BLOSSOM_DATA,
                labelRes = R.string.my_blossom_data,
                icon = MaterialSymbols.Storage,
                resolveRoute = { Route.ManageBlossomBlobs },
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
        NavBarItem.NOSTR_SIGNER to
            NavBarItemDef(
                id = NavBarItem.NOSTR_SIGNER,
                labelRes = R.string.nip46_signer_title,
                icon = MaterialSymbols.Key,
                resolveRoute = { Route.Nip46Signer() },
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
        NavBarItem.WORKOUTS to
            NavBarItemDef(
                id = NavBarItem.WORKOUTS,
                labelRes = R.string.workouts,
                icon = MaterialSymbols.DirectionsRun,
                resolveRoute = { Route.Workouts },
            ),
        NavBarItem.GIT_REPOSITORIES to
            NavBarItemDef(
                id = NavBarItem.GIT_REPOSITORIES,
                labelRes = R.string.git_repositories,
                icon = MaterialSymbols.Code,
                resolveRoute = { Route.GitRepositories },
            ),
        NavBarItem.SOFTWARE_APPS to
            NavBarItemDef(
                id = NavBarItem.SOFTWARE_APPS,
                labelRes = R.string.software_apps,
                icon = MaterialSymbols.Apps,
                resolveRoute = { Route.SoftwareApps },
            ),
        NavBarItem.NAPPLETS to
            NavBarItemDef(
                id = NavBarItem.NAPPLETS,
                labelRes = R.string.napplets,
                icon = MaterialSymbols.Apps,
                resolveRoute = { Route.Napplets },
            ),
        NavBarItem.NSITES to
            NavBarItemDef(
                id = NavBarItem.NSITES,
                labelRes = R.string.nsites,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.Nsites },
            ),
        NavBarItem.BROWSER to
            NavBarItemDef(
                id = NavBarItem.BROWSER,
                labelRes = R.string.browser,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.Browser },
            ),
        NavBarItem.FAVORITE_APPS to
            NavBarItemDef(
                id = NavBarItem.FAVORITE_APPS,
                labelRes = R.string.favorite_apps,
                icon = MaterialSymbols.Star,
                resolveRoute = { Route.FavoriteApps },
            ),
        NavBarItem.CALENDARS to
            NavBarItemDef(
                id = NavBarItem.CALENDARS,
                labelRes = R.string.route_calendars,
                icon = MaterialSymbols.CalendarMonth,
                resolveRoute = { Route.Calendars },
            ),
        NavBarItem.CALENDAR_COLLECTIONS to
            NavBarItemDef(
                id = NavBarItem.CALENDAR_COLLECTIONS,
                labelRes = R.string.route_calendar_collections,
                icon = MaterialSymbols.AutoMirrored.FormatListBulleted,
                resolveRoute = { Route.CalendarCollections },
            ),
        NavBarItem.SHORTS to
            NavBarItemDef(
                id = NavBarItem.SHORTS,
                labelRes = R.string.shorts,
                icon = MaterialSymbols.PlayCircle,
                resolveRoute = { Route.Shorts },
            ),
        NavBarItem.MUSIC_TRACKS to
            NavBarItemDef(
                id = NavBarItem.MUSIC_TRACKS,
                labelRes = R.string.route_music_tracks,
                icon = MaterialSymbols.MusicNote,
                resolveRoute = { Route.MusicTracks },
            ),
        NavBarItem.MUSIC_PLAYLISTS to
            NavBarItemDef(
                id = NavBarItem.MUSIC_PLAYLISTS,
                labelRes = R.string.route_music_playlists,
                icon = MaterialSymbols.AutoMirrored.PlaylistAdd,
                resolveRoute = { Route.MusicPlaylists },
            ),
        NavBarItem.PODCAST_EPISODES to
            NavBarItemDef(
                id = NavBarItem.PODCAST_EPISODES,
                labelRes = R.string.route_podcast_episodes,
                icon = MaterialSymbols.Headphones,
                resolveRoute = { Route.PodcastEpisodes },
            ),
        NavBarItem.PODCASTS to
            NavBarItemDef(
                id = NavBarItem.PODCASTS,
                labelRes = R.string.route_podcasts,
                icon = MaterialSymbols.Podcasts,
                resolveRoute = { Route.Podcasts },
            ),
        NavBarItem.PUBLIC_CHATS to
            NavBarItemDef(
                id = NavBarItem.PUBLIC_CHATS,
                labelRes = R.string.public_chats,
                icon = MaterialSymbols.AutoMirrored.Chat,
                resolveRoute = { Route.PublicChats },
            ),
        NavBarItem.RELAY_GROUPS to
            NavBarItemDef(
                id = NavBarItem.RELAY_GROUPS,
                labelRes = R.string.relay_groups_title,
                icon = MaterialSymbols.Forum,
                resolveRoute = { Route.RelayGroups },
            ),
        NavBarItem.AGENT_CONSOLE to
            NavBarItemDef(
                id = NavBarItem.AGENT_CONSOLE,
                labelRes = R.string.buzz_console_card_title,
                icon = MaterialSymbols.AutoAwesome,
                resolveRoute = { Route.AgentConsole },
            ),
        NavBarItem.BUZZ_DMS to
            NavBarItemDef(
                id = NavBarItem.BUZZ_DMS,
                labelRes = R.string.buzz_dm_nav_label,
                icon = MaterialSymbols.AutoMirrored.Send,
                resolveRoute = { Route.BuzzDmList },
            ),
        NavBarItem.CONCORD to
            NavBarItemDef(
                id = NavBarItem.CONCORD,
                labelRes = R.string.concord_home_title,
                icon = MaterialSymbols.Group,
                resolveRoute = { Route.Concords },
            ),
        NavBarItem.GEOHASH_CHATS to
            NavBarItemDef(
                id = NavBarItem.GEOHASH_CHATS,
                labelRes = R.string.location_channels,
                icon = MaterialSymbols.LocationOn,
                resolveRoute = { Route.GeohashChats },
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
        NavBarItem.WALLET,
        NavBarItem.BROWSER,
        NavBarItem.NOTIFICATIONS,
    )

/** The default bottom bar as unified entries (all built-in; favorites are added by the user). */
val DefaultBottomBarEntries: List<BottomBarEntry> = DefaultBottomBarItems.map { BottomBarEntry.BuiltIn(it) }

// Ordered membership lists for each drawer section. The drawer renders these by looking up
// each id in NavBarCatalog, so adding a new screen only requires editing the catalog + the
// matching section list below — not two separate files.
val DrawerNavigateItems: List<NavBarItem> =
    listOf(
        NavBarItem.HOME,
        NavBarItem.MESSAGES,
        NavBarItem.VIDEO,
        NavBarItem.BROWSER,
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
        NavBarItem.SCHEDULED_POSTS,
        NavBarItem.INTEREST_SETS,
        NavBarItem.BLOSSOM_DATA,
        NavBarItem.EMOJI_PACKS,
        NavBarItem.WALLET,
        NavBarItem.NOSTR_SIGNER,
    )

/**
 * A titled, collapsible group of selectable destinations in the bottom-bar settings picker. The
 * catalog's [linkedMapOf] insertion order is hand-maintained and reads as scattered in the flat
 * picker; these curated categories give the "Available" list a deliberate, grouped order instead.
 * Every [NavBarItem] in [NavBarCatalog] appears in exactly one category (see [BottomBarCategories]).
 */
data class NavBarCategory(
    val titleRes: Int,
    val items: List<NavBarItem>,
)

/**
 * The ordered, grouped catalog for the settings picker. Kept in sync with [NavBarCatalog]: every
 * catalog id must appear here exactly once (asserted by BottomBarCategoriesTest), so a newly added
 * screen surfaces in the picker under a deliberate heading rather than vanishing.
 */
val BottomBarCategories: List<NavBarCategory> =
    listOf(
        NavBarCategory(
            R.string.bottom_bar_category_main,
            listOf(
                NavBarItem.HOME,
                NavBarItem.MESSAGES,
                NavBarItem.VIDEO,
                NavBarItem.DISCOVER,
                NavBarItem.NOTIFICATIONS,
            ),
        ),
        NavBarCategory(
            R.string.bottom_bar_category_chats,
            listOf(
                NavBarItem.PUBLIC_CHATS,
                NavBarItem.RELAY_GROUPS,
                NavBarItem.AGENT_CONSOLE,
                NavBarItem.BUZZ_DMS,
                NavBarItem.CONCORD,
                NavBarItem.GEOHASH_CHATS,
            ),
        ),
        NavBarCategory(
            R.string.bottom_bar_category_you,
            listOf(
                NavBarItem.PROFILE,
                NavBarItem.MY_LISTS,
                NavBarItem.BOOKMARKS,
                NavBarItem.WEB_BOOKMARKS,
                NavBarItem.DRAFTS,
                NavBarItem.SCHEDULED_POSTS,
                NavBarItem.INTEREST_SETS,
                NavBarItem.FAVORITE_ALGO_FEEDS,
                NavBarItem.BLOSSOM_DATA,
                NavBarItem.EMOJI_PACKS,
                NavBarItem.WALLET,
                NavBarItem.NOSTR_SIGNER,
            ),
        ),
        NavBarCategory(
            R.string.bottom_bar_category_feeds,
            listOf(
                NavBarItem.ARTICLES,
                NavBarItem.LONGS,
                NavBarItem.PICTURES,
                NavBarItem.SHORTS,
                NavBarItem.LIVE_STREAMS,
                NavBarItem.NESTS,
                NavBarItem.PODCASTS,
                NavBarItem.PODCAST_EPISODES,
                NavBarItem.MUSIC_TRACKS,
                NavBarItem.MUSIC_PLAYLISTS,
                NavBarItem.POLLS,
                NavBarItem.PRODUCTS,
                NavBarItem.WORKOUTS,
                NavBarItem.GIT_REPOSITORIES,
                NavBarItem.COMMUNITIES,
                NavBarItem.FOLLOW_PACKS,
                NavBarItem.CALENDARS,
                NavBarItem.CALENDAR_COLLECTIONS,
                NavBarItem.BADGES,
                NavBarItem.EMOJI_SETS,
            ),
        ),
        NavBarCategory(
            R.string.bottom_bar_category_apps,
            listOf(
                NavBarItem.BROWSER,
                NavBarItem.FAVORITE_APPS,
                NavBarItem.SOFTWARE_APPS,
                NavBarItem.NAPPLETS,
                NavBarItem.NSITES,
            ),
        ),
        NavBarCategory(
            R.string.bottom_bar_category_other,
            listOf(
                NavBarItem.SETTINGS,
            ),
        ),
    )

val DrawerFeedsItems: List<NavBarItem> =
    listOfNotNull(
        NavBarItem.ARTICLES,
        NavBarItem.PICTURES,
        NavBarItem.SHORTS,
        NavBarItem.LONGS,
        NavBarItem.PODCAST_EPISODES,
        NavBarItem.PODCASTS,
        NavBarItem.MUSIC_TRACKS,
        NavBarItem.MUSIC_PLAYLISTS,
        NavBarItem.POLLS,
        NavBarItem.PRODUCTS,
        NavBarItem.WORKOUTS,
        NavBarItem.GIT_REPOSITORIES,
        NavBarItem.LIVE_STREAMS,
        NavBarItem.NESTS,
        NavBarItem.COMMUNITIES,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.RELAY_GROUPS,
        NavBarItem.AGENT_CONSOLE,
        NavBarItem.BUZZ_DMS,
        NavBarItem.CONCORD,
        NavBarItem.GEOHASH_CHATS,
        NavBarItem.CALENDARS,
        NavBarItem.CALENDAR_COLLECTIONS,
        NavBarItem.SOFTWARE_APPS,
        // Favorites can be pinned as inline tabs that render on a cross-process surface
        // (SurfaceControlViewHost), which needs API 30+. Gate the whole grid on R+ for that reason.
        NavBarItem.FAVORITE_APPS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.R },
        NavBarItem.NAPPLETS,
        NavBarItem.NSITES,
        NavBarItem.FOLLOW_PACKS,
        NavBarItem.BADGES,
        NavBarItem.EMOJI_SETS,
    )
