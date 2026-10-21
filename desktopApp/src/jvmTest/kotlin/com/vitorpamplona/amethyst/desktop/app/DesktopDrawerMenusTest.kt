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

import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.DrawerSectionId
import com.vitorpamplona.amethyst.commons.model.navigation.NavBarItem
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.account_settings
import com.vitorpamplona.amethyst.commons.resources.app_settings
import com.vitorpamplona.amethyst.commons.resources.relay_setup
import com.vitorpamplona.amethyst.commons.resources.route_chess
import com.vitorpamplona.amethyst.commons.resources.ui_preferences
import com.vitorpamplona.amethyst.commons.resources.zaps
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.NavBarCatalog
import com.vitorpamplona.amethyst.commons.ui.navigation.drawer.DrawerSections
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsCategory
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsEntry
import com.vitorpamplona.amethyst.commons.ui.settings.SettingsIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopDrawerMenusTest {
    private val opened = mutableListOf<Route>()
    private val ran = mutableListOf<Command>()

    private val actions =
        object : DrawerMenuActions {
            override fun openItem(route: () -> Route) {
                opened += route()
            }

            override fun run(command: Command) {
                ran += command
            }

            override fun open(route: Route) {
                opened += route
            }
        }

    private val settings =
        listOf(
            SettingsCategory(
                titleRes = Res.string.account_settings,
                entries = listOf(SettingsEntry(Res.string.zaps, SettingsIcon.Symbol(MaterialSymbols.Bolt)) { opened += Route.UpdateReactionType }),
            ),
            SettingsCategory(
                titleRes = Res.string.app_settings,
                entries = listOf(SettingsEntry(Res.string.ui_preferences, SettingsIcon.Symbol(MaterialSymbols.Settings)) { opened += Route.Settings }),
            ),
        )

    // Each item resolves to a route named after it, so canOpen can single one out.
    private fun menus(
        hidden: Set<NavBarItem> = emptySet(),
        canOpen: (Route) -> Boolean = { true },
        showChess: Boolean = false,
    ) = drawerMenus(
        sections = DrawerSections,
        hidden = hidden,
        settings = settings,
        canOpen = canOpen,
        resolveRoute = { def -> Route.Profile(def.id.name) },
        showChess = showChess,
        actions = actions,
    )

    private fun List<MenuEntry.Submenu>.of(id: DrawerSectionId) = firstOrNull { it.label == DrawerSections.first { s -> s.id == id }.titleRes }

    private fun MenuEntry.Submenu.actions() = entries.filterIsInstance<MenuEntry.Action>()

    @Test
    fun everyDrawerSectionIsAMenuInTheDrawersOrder() {
        assertEquals(DrawerSections.map { it.titleRes }, menus().map { it.label })
    }

    @Test
    fun aSectionListsItsVisibleRowsInOrder() {
        val you = menus().of(DrawerSectionId.YOU)!!
        val expected = DrawerSections.first { it.id == DrawerSectionId.YOU }.items.map { NavBarCatalog.getValue(it).labelRes }
        assertEquals(expected, you.actions().map { it.label })
    }

    @Test
    fun hiddenRowsAndRowsWithoutAScreenAreLeftOut() {
        val you = menus(hidden = setOf(NavBarItem.WALLET), canOpen = { it != Route.Profile(NavBarItem.DRAFTS.name) }).of(DrawerSectionId.YOU)!!
        val labels = you.actions().map { it.label }
        assertFalse(NavBarCatalog.getValue(NavBarItem.WALLET).labelRes in labels)
        assertFalse(NavBarCatalog.getValue(NavBarItem.DRAFTS).labelRes in labels)
        assertTrue(NavBarCatalog.getValue(NavBarItem.PROFILE).labelRes in labels)
    }

    @Test
    fun aSectionWithEveryRowHiddenHasNoMenu() {
        val feeds = DrawerSections.first { it.id == DrawerSectionId.FEEDS }
        assertNull(menus(hidden = feeds.items.toSet()).of(DrawerSectionId.FEEDS))
    }

    @Test
    fun shortcutRowsRunTheirCommandAndOthersOpenTheirRoute() {
        val navigate = menus().of(DrawerSectionId.NAVIGATE)!!
        val home = navigate.actions().first { it.label == NavBarCatalog.getValue(NavBarItem.HOME).labelRes }
        assertEquals(Command.HOME, home.command)
        home.onClick()
        assertEquals(listOf(Command.HOME), ran)

        val discover = navigate.actions().first { it.label == NavBarCatalog.getValue(NavBarItem.DISCOVER).labelRes }
        assertNull(discover.command)
        discover.onClick()
        assertEquals(listOf<Route>(Route.Profile(NavBarItem.DISCOVER.name)), opened)

        // Search isn't a drawer row, but it is where the window's search shortcut lives.
        assertEquals(Command.SEARCH, navigate.actions().last().command)
    }

    @Test
    fun createLeadsWithNewPostAndAddsChessOnDebugBuilds() {
        val create = menus(canOpen = { it != Route.NewHlsVideo }).of(DrawerSectionId.CREATE)!!
        assertEquals(listOf(Command.NEW_POST), create.actions().map { it.command })

        val debug = menus(canOpen = { it != Route.NewHlsVideo }, showChess = true).of(DrawerSectionId.CREATE)!!
        assertEquals(Res.string.route_chess, debug.actions().last().label)
    }

    @Test
    fun systemHoldsRelaysTheSettingsCategoriesAsSubmenusAndSettings() {
        val system = menus().of(DrawerSectionId.SYSTEM)!!
        assertEquals(Res.string.relay_setup, (system.entries.first() as MenuEntry.Action).label)

        val submenus = system.entries.filterIsInstance<MenuEntry.Submenu>()
        assertEquals(listOf(Res.string.account_settings, Res.string.app_settings), submenus.map { it.label })
        assertEquals(Res.string.zaps, (submenus.first().entries.single() as MenuEntry.Action).label)

        // Settings itself runs the shortcut's command but leaves Cmd+, to the app menu.
        val all = system.actions().last()
        assertEquals(NavBarCatalog.getValue(NavBarItem.SETTINGS).labelRes, all.label)
        assertNull(all.command)
        all.onClick()
        assertEquals(listOf(Command.SETTINGS), ran)
    }

    @Test
    fun noMenuStartsOrEndsWithASeparatorOrDoublesOne() {
        menus(hidden = NavBarItem.entries.toSet()).forEach { menu ->
            assertFalse(menu.entries.first() is MenuEntry.Separator, "${menu.label} starts with a separator")
            assertFalse(menu.entries.last() is MenuEntry.Separator, "${menu.label} ends with a separator")
            menu.entries.zipWithNext().forEach { (a, b) -> assertFalse(a is MenuEntry.Separator && b is MenuEntry.Separator) }
        }
    }
}
