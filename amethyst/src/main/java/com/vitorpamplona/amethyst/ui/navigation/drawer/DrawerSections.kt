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
package com.vitorpamplona.amethyst.ui.navigation.drawer

import android.os.Build
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.ui.navigation.bottombars.NavBarItem

/**
 * The drawer's layout: which destinations it lists, under which heading, in which order.
 *
 * One list drives two screens — [ListContent] renders the visible rows of each section, and the Side
 * Menu settings screen renders the same sections as its show/hide catalog. Adding a destination to a
 * section's list therefore surfaces it in the drawer *and* in its configuration screen without
 * touching either, and DrawerSectionsTest fails the build if a newly added [NavBarCatalog] id isn't
 * filed into exactly one section.
 *
 * Section order and within-section order are fixed and not user-editable: the drawer is a menu, and a
 * menu whose headings move around is harder to learn, not easier. The only per-account choice is
 * which rows are visible — see [DrawerItemVisibility].
 */
@Immutable
data class DrawerSection(
    val id: DrawerSectionId,
    val titleRes: Int,
    val icon: MaterialSymbol,
    val items: List<NavBarItem>,
)

/**
 * Identifies a section for the handful of rendering rules that are specific to one. Matching on this
 * rather than on a section's object identity keeps those rules working if the list is ever mapped or
 * copied — a `DrawerSections.map { it.copy(...) }` would silently defeat an `===` check, with no
 * compile error and nothing to fail a test.
 */
enum class DrawerSectionId {
    YOU,
    NAVIGATE,
    FEEDS,

    /** Composer entry points. Carries no catalog destinations, so nothing in it is configurable. */
    CREATE,

    /** Also renders the relay-status row, which isn't a catalog destination (it shows a live counter). */
    SYSTEM,
}

private val DrawerNavigateItems: List<NavBarItem> =
    listOf(
        NavBarItem.HOME,
        NavBarItem.MESSAGES,
        NavBarItem.VIDEO,
        NavBarItem.BROWSER,
        NavBarItem.DISCOVER,
        NavBarItem.NOTIFICATIONS,
    )

private val DrawerYouItems: List<NavBarItem> =
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
    )

private val DrawerFeedsItems: List<NavBarItem> =
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
        NavBarItem.HIGHLIGHTS,
        NavBarItem.LIVE_STREAMS,
        NavBarItem.NESTS,
        NavBarItem.COMMUNITIES,
        NavBarItem.PUBLIC_CHATS,
        NavBarItem.RELAY_GROUPS,
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

val DrawerSections: List<DrawerSection> =
    listOf(
        DrawerSection(DrawerSectionId.YOU, R.string.drawer_section_you, MaterialSymbols.AccountCircle, DrawerYouItems),
        DrawerSection(DrawerSectionId.NAVIGATE, R.string.drawer_section_navigate, MaterialSymbols.Home, DrawerNavigateItems),
        DrawerSection(DrawerSectionId.FEEDS, R.string.drawer_section_feeds, MaterialSymbols.Subscriptions, DrawerFeedsItems),
        DrawerSection(DrawerSectionId.CREATE, R.string.drawer_section_create, MaterialSymbols.Edit, emptyList()),
        DrawerSection(DrawerSectionId.SYSTEM, R.string.drawer_section_system, MaterialSymbols.Settings, listOf(NavBarItem.SETTINGS)),
    )

fun drawerSection(id: DrawerSectionId): DrawerSection = DrawerSections.first { it.id == id }

/**
 * Catalog ids deliberately absent from every [DrawerSections] list, with the reason. Only Favorite
 * Apps qualifies: [DrawerFeedsItems] gates it on API 30+ (its inline tabs need SurfaceControlViewHost),
 * so on older devices the row simply doesn't exist. DrawerSectionsTest allows exactly these to be
 * missing, and fails on anything else — that's what keeps a newly added destination from silently
 * skipping both the drawer and its settings screen.
 */
val SdkGatedDrawerItems: Set<NavBarItem> = setOf(NavBarItem.FAVORITE_APPS)
