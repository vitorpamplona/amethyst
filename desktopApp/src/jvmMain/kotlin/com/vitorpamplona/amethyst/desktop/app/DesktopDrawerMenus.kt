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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.new_post
import com.vitorpamplona.amethyst.commons.resources.relay_setup
import com.vitorpamplona.amethyst.commons.resources.route_chess
import com.vitorpamplona.amethyst.commons.resources.screen_search_title
import com.vitorpamplona.amethyst.commons.resources.share_hls_video
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarItemDef
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSection
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSectionVisibility
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import org.jetbrains.compose.resources.StringResource

/** One entry of a menu-bar menu, with its label still a resource so the model stays out of composition. */
internal sealed interface MenuEntry {
    data class Action(
        val label: StringResource,
        val command: Command? = null,
        val onClick: () -> Unit,
    ) : MenuEntry

    data class Submenu(
        val label: StringResource,
        val entries: List<MenuEntry>,
    ) : MenuEntry

    data object Separator : MenuEntry
}

/** What a drawer menu item does when picked: [DesktopNavigator] in the app, a recorder in tests. */
internal interface DrawerMenuActions {
    /** A drawer destination, opened the way its drawer row opens it. */
    fun openItem(route: () -> Route)

    /** An item that has a keyboard shortcut runs that shortcut's command, so the two can't drift apart. */
    fun run(command: Command)

    /** A plain push: the drawer's fixed rows (relays, the composers). */
    fun open(route: Route)
}

/** The drawer destinations that carry a shortcut, which their menu items show and run. */
private val DrawerItemCommands: Map<NavBarItem, Command> =
    mapOf(
        NavBarItem.HOME to Command.HOME,
        NavBarItem.MESSAGES to Command.MESSAGES,
        NavBarItem.NOTIFICATIONS to Command.NOTIFICATIONS,
        NavBarItem.PROFILE to Command.PROFILE,
        NavBarItem.BOOKMARKS to Command.BOOKMARKS,
        NavBarItem.DRAFTS to Command.DRAFTS,
        // Shown without Cmd+, here: the app menu's Settings… carries it, as on every Mac app.
        NavBarItem.SETTINGS to Command.SETTINGS,
    )

/**
 * The drawer as menu-bar menus: one menu per [DrawerSection], in the drawer's order, holding the
 * rows the user left visible there (Settings › Side Menu) and that this front end has a screen for.
 * System also gets one submenu per settings category, the same list the Settings screen shows.
 *
 * A section with nothing left to show gets no menu, as it gets no heading in the drawer. Create and
 * System always have rows: New Post, and Relays and Settings.
 */
internal fun drawerMenus(
    sections: List<DrawerSection>,
    hidden: Set<NavBarItem>,
    settings: List<SettingsCategory>,
    canOpen: (Route) -> Boolean,
    resolveRoute: (NavBarItemDef) -> Route,
    showChess: Boolean,
    actions: DrawerMenuActions,
): List<MenuEntry.Submenu> =
    sections.mapNotNull { section ->
        val items =
            DrawerSectionVisibility.visibleItems(section, hidden).mapNotNull { id ->
                val def = NavBarCatalog[id] ?: return@mapNotNull null
                // A row whose screen this front end doesn't have (My Fitness on desktop) is left out.
                if (!canOpen(resolveRoute(def))) return@mapNotNull null
                val command = DrawerItemCommands[id]
                if (command != null) {
                    MenuEntry.Action(def.labelRes, command.takeIf { id != NavBarItem.SETTINGS }) { actions.run(command) }
                } else {
                    MenuEntry.Action(def.labelRes) { actions.openItem { resolveRoute(def) } }
                }
            }

        val entries =
            when (section.id) {
                DrawerSectionId.NAVIGATE -> items + MenuEntry.Separator + MenuEntry.Action(Res.string.screen_search_title, Command.SEARCH) { actions.run(Command.SEARCH) }
                DrawerSectionId.CREATE -> createEntries(canOpen, showChess, actions) + items
                DrawerSectionId.SYSTEM -> systemEntries(settings, actions) + MenuEntry.Separator + items
                else -> items
            }.trimSeparators()

        if (entries.none { it !is MenuEntry.Separator }) null else MenuEntry.Submenu(section.titleRes, entries)
    }

/** New Post, then the drawer's Create rows: HLS upload where this front end has it, chess on debug builds. */
private fun createEntries(
    canOpen: (Route) -> Boolean,
    showChess: Boolean,
    actions: DrawerMenuActions,
): List<MenuEntry> =
    buildList {
        add(MenuEntry.Action(Res.string.new_post, Command.NEW_POST) { actions.run(Command.NEW_POST) })
        if (canOpen(Route.NewHlsVideo)) add(MenuEntry.Action(Res.string.share_hls_video) { actions.open(Route.NewHlsVideo) })
        if (showChess) add(MenuEntry.Action(Res.string.route_chess) { actions.open(Route.Chess) })
    }

/** The drawer's Relays row, then each settings category as a submenu of its screens. */
private fun systemEntries(
    settings: List<SettingsCategory>,
    actions: DrawerMenuActions,
): List<MenuEntry> =
    buildList {
        add(MenuEntry.Action(Res.string.relay_setup) { actions.open(Route.EditRelays) })
        val categories = settings.filter { it.entries.isNotEmpty() }
        if (categories.isNotEmpty()) {
            add(MenuEntry.Separator)
            categories.forEach { category ->
                add(MenuEntry.Submenu(category.titleRes, category.entries.map { MenuEntry.Action(it.titleRes, onClick = it.onClick) }))
            }
        }
    }

/** Drops leading, trailing and doubled separators, which a section with hidden rows can leave behind. */
private fun List<MenuEntry>.trimSeparators(): List<MenuEntry> {
    val out = ArrayList<MenuEntry>(size)
    forEach { entry ->
        if (entry is MenuEntry.Separator && (out.isEmpty() || out.last() is MenuEntry.Separator)) return@forEach
        out.add(entry)
    }
    if (out.lastOrNull() is MenuEntry.Separator) out.removeAt(out.lastIndex)
    return out
}
