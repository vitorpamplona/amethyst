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
package com.vitorpamplona.amethyst.commons.model.navigation

import com.vitorpamplona.amethyst.commons.model.NavigationLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavBackStacksLayoutsTest {
    private fun NavBackStacks.routes() = stack.map { it.route }

    private fun tabs() = NavBackStacks().apply { changeMode(NavigationLayoutType.TABS) }

    private fun windows() = NavBackStacks().apply { changeMode(NavigationLayoutType.WINDOWS) }

    @Test
    fun eachScreenOpensAsATabNextToTheOneThatOpenedIt() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.switchTab(Route.Home)
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Home, Route.Note("n"), Route.Hashtag("nostr")), stacks.routes())
        assertEquals(Route.Note("n"), stacks.topRoute)
        assertEquals(Route.Home, stacks.openerOf(stacks.top)?.route)
    }

    @Test
    fun openingAnOpenScreenFocusesItInsteadOfDuplicating() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Home)
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Home, Route.Note("n")), stacks.routes())
        assertEquals(Route.Note("n"), stacks.topRoute)
    }

    @Test
    fun aFreshSearchOpensEvenWhenOneIsOpen() {
        val stacks = tabs()
        stacks.open(Route.Search(), forceNew = true)
        stacks.open(Route.Search(), forceNew = true)

        assertEquals(3, stacks.stack.size)
    }

    @Test
    fun goingBackClosesTheTabAndShowsItsOpener() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.switchTab(Route.Home)
        stacks.activate(stacks.stack.first { it.route == Route.Note("n") }.id)

        assertTrue(stacks.pop())

        assertEquals(listOf(Route.Home, Route.Hashtag("nostr")), stacks.routes())
        assertEquals(Route.Hashtag("nostr"), stacks.topRoute)
    }

    @Test
    fun closingATabHandsItsChildrenToItsOpener() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.close(stacks.stack.first { it.route == Route.Hashtag("nostr") }.id)

        assertEquals(Route.Home, stacks.openerOf(stacks.stack.first { it.route == Route.Note("n") })?.route)
    }

    @Test
    fun closingTheLastTabLeavesHome() {
        val stacks = tabs()
        assertTrue(stacks.pop())

        assertEquals(listOf<Route>(Route.Home), stacks.routes())
    }

    @Test
    fun theNextStepOfAFlowReplacesTheScreenInPlace() {
        val stacks = tabs()
        stacks.push(Route.Note("n"))
        val id = stacks.top.id
        stacks.popUpTo(Route.Note("m"), Route.Note::class)

        assertEquals(id, stacks.top.id)
        assertEquals(Route.Note("m"), stacks.topRoute)
        assertEquals(2, stacks.stack.size)
    }

    @Test
    fun aNarrowTabsWindowShowsTheChainOfOpeners() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))

        assertEquals(listOf(Route.Note("n")), stacks.shown(wide = true).map { it.route })
        assertEquals(listOf(Route.Home, Route.Hashtag("nostr"), Route.Note("n")), stacks.shown(wide = false).map { it.route })
    }

    @Test
    fun switchingBetweenStackAndTabsKeepsTheScreens() {
        val stacks = NavBackStacks()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))
        stacks.changeMode(NavigationLayoutType.TABS)

        assertEquals(Route.Note("n"), stacks.topRoute)
        assertEquals(Route.Hashtag("nostr"), stacks.openerOf(stacks.top)?.route)

        stacks.switchTab(Route.Home)
        stacks.push(Route.Profile("a"))
        stacks.changeMode(NavigationLayoutType.STACK)

        assertEquals(listOf(Route.Home, Route.Profile("a")), stacks.routes())
    }

    @Test
    fun inWindowsSectionsStayInTheMainWindowAndScreensGetWindows() {
        val stacks = windows()
        stacks.switchTab(Route.Message)
        stacks.push(Route.Note("n"))
        stacks.push(Route.Profile("a"))

        assertEquals(Route.Message, stacks.topRoute)
        assertEquals(listOf(Route.Note("n"), Route.Profile("a")), stacks.windows().map { it.route })
        assertEquals(stacks.windows().last().id, stacks.windowFocus?.id)
    }

    @Test
    fun aScreenOpenInAWindowIsRaisedNotOpenedAgain() {
        val stacks = windows()
        stacks.push(Route.Note("n"))
        val first = assertNotNull(stacks.windowFocus)
        stacks.push(Route.Profile("a"))
        stacks.push(Route.Note("n"))

        assertEquals(2, stacks.windows().size)
        assertEquals(first.id, stacks.windowFocus?.id)
        assertTrue(stacks.windowFocus!!.serial > first.serial)
    }

    @Test
    fun theMainWindowNeverPopsAndClosingAWindowLeavesIt() {
        val stacks = windows()
        stacks.push(Route.Note("n"))

        assertFalse(stacks.pop())
        stacks.close(stacks.windows().single().id)

        assertEquals(listOf<Route>(Route.Home), stacks.routes())
        assertEquals(Route.Home, stacks.topRoute)
    }

    @Test
    fun theLayoutSurvivesSavingAndRestoring() {
        val stacks = tabs()
        stacks.push(Route.Hashtag("nostr"))
        stacks.push(Route.Note("n"))

        val restored = assertNotNull(NavBackStacks.decode(stacks.encode()))

        assertEquals(NavigationLayoutType.TABS, restored.mode)
        assertEquals(Route.Note("n"), restored.topRoute)
        assertEquals(Route.Hashtag("nostr"), restored.openerOf(restored.top)?.route)
        assertNull(restored.windowFocus)
    }
}
