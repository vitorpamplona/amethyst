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

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.BottomBarEntry
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.badges
import com.vitorpamplona.amethyst.commons.resources.bookmarks
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_apps
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_chats
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_feeds
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_main
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_other
import com.vitorpamplona.amethyst.commons.resources.bottom_bar_category_you
import com.vitorpamplona.amethyst.commons.resources.browser
import com.vitorpamplona.amethyst.commons.resources.communities
import com.vitorpamplona.amethyst.commons.resources.concord_home_title
import com.vitorpamplona.amethyst.commons.resources.discover_marketplace
import com.vitorpamplona.amethyst.commons.resources.discover_reads
import com.vitorpamplona.amethyst.commons.resources.drafts
import com.vitorpamplona.amethyst.commons.resources.emoji_sets
import com.vitorpamplona.amethyst.commons.resources.favorite_apps
import com.vitorpamplona.amethyst.commons.resources.favorite_dvms_title
import com.vitorpamplona.amethyst.commons.resources.follow_packs
import com.vitorpamplona.amethyst.commons.resources.git_repositories
import com.vitorpamplona.amethyst.commons.resources.highlights
import com.vitorpamplona.amethyst.commons.resources.interest_sets_title
import com.vitorpamplona.amethyst.commons.resources.live_streams
import com.vitorpamplona.amethyst.commons.resources.location_channels
import com.vitorpamplona.amethyst.commons.resources.longs
import com.vitorpamplona.amethyst.commons.resources.manage_emoji_packs
import com.vitorpamplona.amethyst.commons.resources.marmot_groups_title
import com.vitorpamplona.amethyst.commons.resources.my_blossom_data
import com.vitorpamplona.amethyst.commons.resources.my_fitness
import com.vitorpamplona.amethyst.commons.resources.my_lists
import com.vitorpamplona.amethyst.commons.resources.napplets
import com.vitorpamplona.amethyst.commons.resources.nests
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_title
import com.vitorpamplona.amethyst.commons.resources.nsites
import com.vitorpamplona.amethyst.commons.resources.pictures
import com.vitorpamplona.amethyst.commons.resources.polls
import com.vitorpamplona.amethyst.commons.resources.profile
import com.vitorpamplona.amethyst.commons.resources.public_chats
import com.vitorpamplona.amethyst.commons.resources.relay_groups_title
import com.vitorpamplona.amethyst.commons.resources.route_calendar_collections
import com.vitorpamplona.amethyst.commons.resources.route_calendars
import com.vitorpamplona.amethyst.commons.resources.route_discover
import com.vitorpamplona.amethyst.commons.resources.route_geocache_hunts
import com.vitorpamplona.amethyst.commons.resources.route_geocaches
import com.vitorpamplona.amethyst.commons.resources.route_home
import com.vitorpamplona.amethyst.commons.resources.route_media
import com.vitorpamplona.amethyst.commons.resources.route_messages
import com.vitorpamplona.amethyst.commons.resources.route_music_playlists
import com.vitorpamplona.amethyst.commons.resources.route_music_tracks
import com.vitorpamplona.amethyst.commons.resources.route_notifications
import com.vitorpamplona.amethyst.commons.resources.route_podcast_episodes
import com.vitorpamplona.amethyst.commons.resources.route_podcasts
import com.vitorpamplona.amethyst.commons.resources.scheduled_posts
import com.vitorpamplona.amethyst.commons.resources.settings
import com.vitorpamplona.amethyst.commons.resources.shorts
import com.vitorpamplona.amethyst.commons.resources.software_apps
import com.vitorpamplona.amethyst.commons.resources.wallet
import com.vitorpamplona.amethyst.commons.resources.web_bookmarks
import com.vitorpamplona.amethyst.commons.resources.workouts
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.GeocacheTab
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import org.jetbrains.compose.resources.StringResource

data class NavBarItemDef(
    val id: NavBarItem,
    val labelRes: StringResource,
    val icon: MaterialSymbol,
    val resolveRoute: (AccountViewModel) -> Route,
)

val NavBarCatalog: Map<NavBarItem, NavBarItemDef> =
    linkedMapOf(
        NavBarItem.HOME to
            NavBarItemDef(
                id = NavBarItem.HOME,
                labelRes = Res.string.route_home,
                icon = MaterialSymbols.Home,
                resolveRoute = { Route.Home },
            ),
        NavBarItem.MESSAGES to
            NavBarItemDef(
                id = NavBarItem.MESSAGES,
                labelRes = Res.string.route_messages,
                icon = MaterialSymbols.Mail,
                resolveRoute = { Route.Message },
            ),
        // The combined media feed (VideoFeedFilter): pictures, NIP-94 files and every NIP-71
        // video kind. Deliberately *not* labelled "Shorts" — NavBarItem.SHORTS below is the
        // vertical-video-only feed, and the two used to share that label.
        NavBarItem.VIDEO to
            NavBarItemDef(
                id = NavBarItem.VIDEO,
                labelRes = Res.string.route_media,
                icon = MaterialSymbols.Subscriptions,
                resolveRoute = { Route.Video() },
            ),
        NavBarItem.DISCOVER to
            NavBarItemDef(
                id = NavBarItem.DISCOVER,
                labelRes = Res.string.route_discover,
                icon = MaterialSymbols.Sensors,
                resolveRoute = { Route.Discover() },
            ),
        NavBarItem.NOTIFICATIONS to
            NavBarItemDef(
                id = NavBarItem.NOTIFICATIONS,
                labelRes = Res.string.route_notifications,
                icon = MaterialSymbols.Notifications,
                resolveRoute = { Route.Notification() },
            ),
        NavBarItem.PROFILE to
            NavBarItemDef(
                id = NavBarItem.PROFILE,
                labelRes = Res.string.profile,
                icon = MaterialSymbols.AccountCircle,
                resolveRoute = { Route.Profile(it.userProfile().pubkeyHex) },
            ),
        NavBarItem.MY_LISTS to
            NavBarItemDef(
                id = NavBarItem.MY_LISTS,
                labelRes = Res.string.my_lists,
                icon = MaterialSymbols.AutoMirrored.FormatListBulleted,
                resolveRoute = { Route.Lists },
            ),
        NavBarItem.BOOKMARKS to
            NavBarItemDef(
                id = NavBarItem.BOOKMARKS,
                labelRes = Res.string.bookmarks,
                icon = MaterialSymbols.CollectionsBookmark,
                resolveRoute = { Route.BookmarkGroups },
            ),
        NavBarItem.WEB_BOOKMARKS to
            NavBarItemDef(
                id = NavBarItem.WEB_BOOKMARKS,
                labelRes = Res.string.web_bookmarks,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.WebBookmarks },
            ),
        NavBarItem.DRAFTS to
            NavBarItemDef(
                id = NavBarItem.DRAFTS,
                labelRes = Res.string.drafts,
                icon = MaterialSymbols.Drafts,
                resolveRoute = { Route.Drafts },
            ),
        NavBarItem.SCHEDULED_POSTS to
            NavBarItemDef(
                id = NavBarItem.SCHEDULED_POSTS,
                labelRes = Res.string.scheduled_posts,
                icon = MaterialSymbols.Schedule,
                resolveRoute = { Route.ScheduledPosts },
            ),
        NavBarItem.INTEREST_SETS to
            NavBarItemDef(
                id = NavBarItem.INTEREST_SETS,
                labelRes = Res.string.interest_sets_title,
                icon = MaterialSymbols.Tag,
                resolveRoute = { Route.InterestSets },
            ),
        NavBarItem.FAVORITE_ALGO_FEEDS to
            NavBarItemDef(
                id = NavBarItem.FAVORITE_ALGO_FEEDS,
                labelRes = Res.string.favorite_dvms_title,
                icon = MaterialSymbols.AutoAwesome,
                resolveRoute = { Route.EditFavoriteAlgoFeeds },
            ),
        NavBarItem.BLOSSOM_DATA to
            NavBarItemDef(
                id = NavBarItem.BLOSSOM_DATA,
                labelRes = Res.string.my_blossom_data,
                icon = MaterialSymbols.Storage,
                resolveRoute = { Route.ManageBlossomBlobs },
            ),
        NavBarItem.EMOJI_PACKS to
            NavBarItemDef(
                id = NavBarItem.EMOJI_PACKS,
                labelRes = Res.string.manage_emoji_packs,
                icon = MaterialSymbols.EmojiEmotions,
                resolveRoute = { Route.EmojiPacks },
            ),
        NavBarItem.WALLET to
            NavBarItemDef(
                id = NavBarItem.WALLET,
                labelRes = Res.string.wallet,
                icon = MaterialSymbols.AccountBalanceWallet,
                resolveRoute = { Route.Wallet },
            ),
        NavBarItem.NOSTR_SIGNER to
            NavBarItemDef(
                id = NavBarItem.NOSTR_SIGNER,
                labelRes = Res.string.nip46_signer_title,
                icon = MaterialSymbols.Key,
                resolveRoute = { Route.Nip46Signer() },
            ),
        NavBarItem.COMMUNITIES to
            NavBarItemDef(
                id = NavBarItem.COMMUNITIES,
                labelRes = Res.string.communities,
                icon = MaterialSymbols.Groups,
                resolveRoute = { Route.Communities },
            ),
        NavBarItem.ARTICLES to
            NavBarItemDef(
                id = NavBarItem.ARTICLES,
                labelRes = Res.string.discover_reads,
                icon = MaterialSymbols.AutoMirrored.Article,
                resolveRoute = { Route.Articles },
            ),
        NavBarItem.PICTURES to
            NavBarItemDef(
                id = NavBarItem.PICTURES,
                labelRes = Res.string.pictures,
                icon = MaterialSymbols.Photo,
                resolveRoute = { Route.Pictures() },
            ),
        NavBarItem.WORKOUTS to
            NavBarItemDef(
                id = NavBarItem.WORKOUTS,
                labelRes = Res.string.workouts,
                icon = MaterialSymbols.DirectionsRun,
                resolveRoute = { Route.Workouts },
            ),
        NavBarItem.MY_FITNESS to
            NavBarItemDef(
                id = NavBarItem.MY_FITNESS,
                labelRes = Res.string.my_fitness,
                icon = MaterialSymbols.AutoMirrored.ShowChart,
                resolveRoute = { Route.MyFitness },
            ),
        NavBarItem.GIT_REPOSITORIES to
            NavBarItemDef(
                id = NavBarItem.GIT_REPOSITORIES,
                labelRes = Res.string.git_repositories,
                icon = MaterialSymbols.Code,
                resolveRoute = { Route.GitRepositories },
            ),
        NavBarItem.HIGHLIGHTS to
            NavBarItemDef(
                id = NavBarItem.HIGHLIGHTS,
                labelRes = Res.string.highlights,
                icon = MaterialSymbols.FormatQuote,
                resolveRoute = { Route.Highlights },
            ),
        NavBarItem.SOFTWARE_APPS to
            NavBarItemDef(
                id = NavBarItem.SOFTWARE_APPS,
                labelRes = Res.string.software_apps,
                icon = MaterialSymbols.Apps,
                resolveRoute = { Route.SoftwareApps },
            ),
        NavBarItem.NAPPLETS to
            NavBarItemDef(
                id = NavBarItem.NAPPLETS,
                labelRes = Res.string.napplets,
                icon = MaterialSymbols.Apps,
                resolveRoute = { Route.Napplets },
            ),
        NavBarItem.NSITES to
            NavBarItemDef(
                id = NavBarItem.NSITES,
                labelRes = Res.string.nsites,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.Nsites },
            ),
        NavBarItem.BROWSER to
            NavBarItemDef(
                id = NavBarItem.BROWSER,
                labelRes = Res.string.browser,
                icon = MaterialSymbols.Language,
                resolveRoute = { Route.Browser },
            ),
        NavBarItem.FAVORITE_APPS to
            NavBarItemDef(
                id = NavBarItem.FAVORITE_APPS,
                labelRes = Res.string.favorite_apps,
                icon = MaterialSymbols.Star,
                resolveRoute = { Route.FavoriteApps },
            ),
        NavBarItem.CALENDARS to
            NavBarItemDef(
                id = NavBarItem.CALENDARS,
                labelRes = Res.string.route_calendars,
                icon = MaterialSymbols.CalendarMonth,
                resolveRoute = { Route.Calendars },
            ),
        NavBarItem.CALENDAR_COLLECTIONS to
            NavBarItemDef(
                id = NavBarItem.CALENDAR_COLLECTIONS,
                labelRes = Res.string.route_calendar_collections,
                icon = MaterialSymbols.AutoMirrored.FormatListBulleted,
                resolveRoute = { Route.CalendarCollections },
            ),
        // Vertical/short video only (ShortsFeedFilter: kinds 22 + 34236). The broader
        // picture-and-video feed is NavBarItem.VIDEO ("Media") above.
        NavBarItem.SHORTS to
            NavBarItemDef(
                id = NavBarItem.SHORTS,
                labelRes = Res.string.shorts,
                icon = MaterialSymbols.PlayCircle,
                resolveRoute = { Route.Shorts() },
            ),
        NavBarItem.MUSIC_TRACKS to
            NavBarItemDef(
                id = NavBarItem.MUSIC_TRACKS,
                labelRes = Res.string.route_music_tracks,
                icon = MaterialSymbols.MusicNote,
                resolveRoute = { Route.MusicTracks },
            ),
        NavBarItem.MUSIC_PLAYLISTS to
            NavBarItemDef(
                id = NavBarItem.MUSIC_PLAYLISTS,
                labelRes = Res.string.route_music_playlists,
                icon = MaterialSymbols.AutoMirrored.PlaylistAdd,
                resolveRoute = { Route.MusicPlaylists },
            ),
        NavBarItem.PODCAST_EPISODES to
            NavBarItemDef(
                id = NavBarItem.PODCAST_EPISODES,
                labelRes = Res.string.route_podcast_episodes,
                icon = MaterialSymbols.Headphones,
                resolveRoute = { Route.PodcastEpisodes },
            ),
        NavBarItem.PODCASTS to
            NavBarItemDef(
                id = NavBarItem.PODCASTS,
                labelRes = Res.string.route_podcasts,
                icon = MaterialSymbols.Podcasts,
                resolveRoute = { Route.Podcasts },
            ),
        NavBarItem.PUBLIC_CHATS to
            NavBarItemDef(
                id = NavBarItem.PUBLIC_CHATS,
                labelRes = Res.string.public_chats,
                icon = MaterialSymbols.AutoMirrored.Chat,
                resolveRoute = { Route.PublicChats },
            ),
        NavBarItem.RELAY_GROUPS to
            NavBarItemDef(
                id = NavBarItem.RELAY_GROUPS,
                labelRes = Res.string.relay_groups_title,
                icon = MaterialSymbols.Forum,
                resolveRoute = { Route.RelayGroups },
            ),
        NavBarItem.CONCORD to
            NavBarItemDef(
                id = NavBarItem.CONCORD,
                labelRes = Res.string.concord_home_title,
                icon = MaterialSymbols.Group,
                resolveRoute = { Route.Concords },
            ),
        NavBarItem.MARMOT_GROUPS to
            NavBarItemDef(
                id = NavBarItem.MARMOT_GROUPS,
                // Duplicates the already-translated `marmot_groups_title` in
                // commonsUI's composeResources, which the screen's own title uses.
                // Unavoidable here: `labelRes` is an Android @StringRes and every
                // other catalog entry is one, so a Compose resource cannot be
                // referenced without changing the type for all ~60 of them. Crowdin
                // manages both resource sets, so this one gets translated too.
                labelRes = Res.string.marmot_groups_title,
                // Lock, not Group: the rooms list already labels a Marmot room
                // with this symbol, so it is the signifier users have learned
                // for these, and it keeps the row distinct from Concord's.
                icon = MaterialSymbols.Lock,
                resolveRoute = { Route.MarmotGroupList },
            ),
        NavBarItem.GEOHASH_CHATS to
            NavBarItemDef(
                id = NavBarItem.GEOHASH_CHATS,
                labelRes = Res.string.location_channels,
                icon = MaterialSymbols.LocationOn,
                resolveRoute = { Route.GeohashChats },
            ),
        NavBarItem.FOLLOW_PACKS to
            NavBarItemDef(
                id = NavBarItem.FOLLOW_PACKS,
                labelRes = Res.string.follow_packs,
                icon = MaterialSymbols.CollectionsBookmark,
                resolveRoute = { Route.FollowPacks },
            ),
        NavBarItem.LIVE_STREAMS to
            NavBarItemDef(
                id = NavBarItem.LIVE_STREAMS,
                labelRes = Res.string.live_streams,
                icon = MaterialSymbols.Sensors,
                resolveRoute = { Route.LiveStreams },
            ),
        NavBarItem.NESTS to
            NavBarItemDef(
                id = NavBarItem.NESTS,
                labelRes = Res.string.nests,
                icon = MaterialSymbols.Mic,
                resolveRoute = { Route.Nests },
            ),
        NavBarItem.LONGS to
            NavBarItemDef(
                id = NavBarItem.LONGS,
                labelRes = Res.string.longs,
                icon = MaterialSymbols.SmartDisplay,
                resolveRoute = { Route.Longs },
            ),
        NavBarItem.POLLS to
            NavBarItemDef(
                id = NavBarItem.POLLS,
                labelRes = Res.string.polls,
                icon = MaterialSymbols.Poll,
                resolveRoute = { Route.Polls },
            ),
        NavBarItem.GEOCACHES to
            NavBarItemDef(
                id = NavBarItem.GEOCACHES,
                labelRes = Res.string.route_geocaches,
                icon = MaterialSymbols.Explore,
                resolveRoute = { Route.Geocaches() },
            ),
        NavBarItem.GEOCACHE_HUNTS to
            NavBarItemDef(
                id = NavBarItem.GEOCACHE_HUNTS,
                labelRes = Res.string.route_geocache_hunts,
                icon = MaterialSymbols.Hiking,
                resolveRoute = { Route.Geocaches(GeocacheTab.HUNTS) },
            ),
        NavBarItem.BADGES to
            NavBarItemDef(
                id = NavBarItem.BADGES,
                labelRes = Res.string.badges,
                icon = MaterialSymbols.MilitaryTech,
                resolveRoute = { Route.Badges },
            ),
        NavBarItem.PRODUCTS to
            NavBarItemDef(
                id = NavBarItem.PRODUCTS,
                labelRes = Res.string.discover_marketplace,
                icon = MaterialSymbols.Storefront,
                resolveRoute = { Route.Products },
            ),
        NavBarItem.EMOJI_SETS to
            NavBarItemDef(
                id = NavBarItem.EMOJI_SETS,
                labelRes = Res.string.emoji_sets,
                icon = MaterialSymbols.EmojiEmotions,
                resolveRoute = { Route.BrowseEmojiSets },
            ),
        NavBarItem.SETTINGS to
            NavBarItemDef(
                id = NavBarItem.SETTINGS,
                labelRes = Res.string.settings,
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

/**
 * A titled, collapsible group of selectable destinations in the bottom-bar settings picker. The
 * catalog's [linkedMapOf] insertion order is hand-maintained and reads as scattered in the flat
 * picker; these curated categories give the "Available" list a deliberate, grouped order instead.
 * Every [NavBarItem] in [NavBarCatalog] appears in exactly one category (see [BottomBarCategories]).
 */
data class NavBarCategory(
    val titleRes: StringResource,
    val icon: MaterialSymbol,
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
            Res.string.bottom_bar_category_main,
            MaterialSymbols.Home,
            listOf(
                NavBarItem.HOME,
                NavBarItem.MESSAGES,
                NavBarItem.VIDEO,
                NavBarItem.DISCOVER,
                NavBarItem.NOTIFICATIONS,
            ),
        ),
        NavBarCategory(
            Res.string.bottom_bar_category_chats,
            MaterialSymbols.Group,
            listOf(
                NavBarItem.PUBLIC_CHATS,
                NavBarItem.RELAY_GROUPS,
                NavBarItem.CONCORD,
                NavBarItem.MARMOT_GROUPS,
                NavBarItem.GEOHASH_CHATS,
            ),
        ),
        NavBarCategory(
            Res.string.bottom_bar_category_you,
            MaterialSymbols.AccountCircle,
            listOf(
                NavBarItem.PROFILE,
                NavBarItem.MY_FITNESS,
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
            Res.string.bottom_bar_category_feeds,
            MaterialSymbols.Subscriptions,
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
                NavBarItem.GEOCACHES,
                NavBarItem.GEOCACHE_HUNTS,
                NavBarItem.WORKOUTS,
                NavBarItem.GIT_REPOSITORIES,
                NavBarItem.HIGHLIGHTS,
                NavBarItem.COMMUNITIES,
                NavBarItem.FOLLOW_PACKS,
                NavBarItem.CALENDARS,
                NavBarItem.CALENDAR_COLLECTIONS,
                NavBarItem.BADGES,
                NavBarItem.EMOJI_SETS,
            ),
        ),
        NavBarCategory(
            Res.string.bottom_bar_category_apps,
            MaterialSymbols.Apps,
            listOf(
                NavBarItem.BROWSER,
                NavBarItem.FAVORITE_APPS,
                NavBarItem.SOFTWARE_APPS,
                NavBarItem.NAPPLETS,
                NavBarItem.NSITES,
            ),
        ),
        NavBarCategory(
            Res.string.bottom_bar_category_other,
            MaterialSymbols.Settings,
            listOf(
                NavBarItem.SETTINGS,
            ),
        ),
    )
