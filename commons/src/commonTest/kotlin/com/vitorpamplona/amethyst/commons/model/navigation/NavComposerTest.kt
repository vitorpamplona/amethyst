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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Composers ([NavModel.opensOver]) open over the screen that asked, never as a tab, and can move out to a window. */
class NavComposerTest {
    private val note = Route.NewShortNote()
    private val profile = Route.Profile("abc")

    private fun isComposer(route: Route) = route is Route.NewShortNote || route == Route.ConcordCreate

    private fun stacks(arrangement: NavArrangement = NavArrangement.STACK) =
        NavBackStacks().apply {
            opensOver = ::isComposer
            changeArrangement(arrangement)
        }

    private val NavBackStacks.tabs get() = (model as TabsModel)

    private fun NavBackStacks.push(
        route: Route,
        origin: Long? = null,
    ) = open(NavRequest(route, origin = origin))

    @Test
    fun aComposerIsNotATab() {
        val stacks = stacks(NavArrangement.TABS)
        val home = stacks.top
        stacks.push(note, origin = home.id)

        assertEquals(listOf(Route.Home), stacks.tabs.strip().map { it.route })
        assertEquals(home, stacks.top)
        assertEquals(listOf(home.route, note), stacks.shown(wide = true).map { it.route })
        assertTrue(stacks.model.isOpenOver(stacks.shown(wide = true).last()))
    }

    @Test
    fun backClosesTheComposerAndLeavesTheTab() {
        val stacks = stacks(NavArrangement.TABS)
        val home = stacks.top
        stacks.push(note, origin = home.id)
        val composer = stacks.tabs.overlays.single()

        assertTrue(stacks.back(composer))
        assertTrue(stacks.tabs.overlays.isEmpty())
        assertEquals(listOf(home), stacks.tabs.strip())
        // Closed like a tab: it can come back.
        assertTrue(stacks.tabs.reopenClosed())
        assertEquals(listOf(note), stacks.tabs.overlays.map { it.route })
    }

    @Test
    fun backFromTheWindowClosesTheComposerFirst() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(profile)
        stacks.push(note)

        assertTrue(stacks.back())
        assertTrue(stacks.tabs.overlays.isEmpty())
        assertEquals(profile, stacks.top.route)
    }

    @Test
    fun theSameComposerTwiceIsOneComposer() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        stacks.push(note)

        assertEquals(1, stacks.tabs.overlays.size)
    }

    @Test
    fun aLinkInAComposerOpensItsTabBehindIt() {
        val stacks = stacks(NavArrangement.TABS)
        val home = stacks.top
        stacks.push(note, origin = home.id)
        val composer = stacks.tabs.overlays.single()

        stacks.push(profile, origin = composer.id)

        assertEquals(listOf(Route.Home, profile), stacks.tabs.strip().map { it.route })
        assertEquals(home, stacks.top)
        // A copy: a SnapshotStateList compares by identity, and Native asserts actual == expected.
        assertEquals(listOf(composer), stacks.tabs.overlays.toList())
        // Opened beside the tab the composer was opened from.
        assertEquals(home, stacks.tabs.openerOf(stacks.tabs.strip().last()))
    }

    @Test
    fun theNextStepOfAComposersFlowIsATab() {
        val stacks = stacks(NavArrangement.TABS)
        val home = stacks.top
        stacks.push(Route.ConcordCreate, origin = home.id)
        val composer = stacks.tabs.overlays.single()

        stacks.open(NavRequest(Route.Settings, NavRequest.Kind.Replace(Route.ConcordCreate::class), origin = composer.id))

        assertTrue(stacks.tabs.overlays.isEmpty())
        assertEquals(Route.Settings, stacks.top.route)
        assertEquals(home, stacks.tabs.openerOf(stacks.top))
        // The flow went on: the form is not something to reopen.
        assertFalse(stacks.tabs.canReopen)
    }

    @Test
    fun aPlaceAskedForFromAComposerClosesIt() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        val composer = stacks.tabs.overlays.single()

        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Section, origin = composer.id))

        assertTrue(stacks.tabs.overlays.isEmpty())
        assertEquals(Route.Message, stacks.top.route)
    }

    @Test
    fun aComposerPoppedOutOfTheTabsIsAWindow() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        val composer = stacks.tabs.overlays.single()

        assertTrue(stacks.popOut(composer.id))

        assertTrue(stacks.tabs.overlays.isEmpty())
        assertEquals(listOf(composer), stacks.windows())
        assertEquals(listOf(Route.Home), stacks.tabs.strip().map { it.route })
        assertTrue(stacks.close(composer.id))
        assertTrue(stacks.windows().isEmpty())
    }

    @Test
    fun theStackPushesAComposerAndPopsItOutToAWindow() {
        val stacks = stacks()
        stacks.push(profile)
        stacks.push(note)
        val composer = stacks.top
        assertTrue(stacks.model.isOpenOver(composer))

        // A screen that is not a composer stays in the stack.
        assertFalse(stacks.popOut(stacks.stack.first { it.route == profile }.id))
        assertTrue(stacks.popOut(composer.id))

        assertEquals(profile, stacks.top.route)
        assertEquals(listOf(composer), stacks.windows())
        assertFalse(stacks.model.isMainScreen(composer))
        assertFalse(stacks.model.canGoBack(composer, wide = true))
        assertFalse(stacks.model.isOpenOver(composer))
        // Its back button closes the window, and leaves the stack alone.
        assertTrue(stacks.back(composer))
        assertTrue(stacks.windows().isEmpty())
        assertEquals(profile, stacks.top.route)
    }

    @Test
    fun aMovedOutComposersFlowStaysInItsWindow() {
        val stacks = stacks()
        stacks.push(Route.ConcordCreate)
        val composer = stacks.top
        stacks.popOut(composer.id)

        stacks.open(NavRequest(Route.Settings, NavRequest.Kind.Replace(Route.ConcordCreate::class), origin = composer.id))

        assertEquals(listOf(Route.Settings), stacks.windows().map { it.route })
        assertEquals(Route.Home, stacks.top.route)
    }

    @Test
    fun switchingLayoutsKeepsTheComposerOpenOverTheScreenBelowIt() {
        val stacks = stacks()
        stacks.push(profile)
        stacks.push(note)

        stacks.changeArrangement(NavArrangement.TABS)

        assertEquals(listOf(Route.Home, profile), stacks.tabs.strip().map { it.route })
        assertEquals(profile, stacks.top.route)
        assertEquals(listOf(note), stacks.tabs.overlays.map { it.route })
        assertEquals(profile, stacks.tabs.openerOf(stacks.tabs.overlays.single())?.route)

        stacks.changeArrangement(NavArrangement.STACK)
        assertEquals(listOf(Route.Home, profile, note), stacks.stack.map { it.route })
    }

    @Test
    fun composersSurviveARestart() {
        val tabs = stacks(NavArrangement.TABS)
        tabs.push(note)
        val restoredTabs = assertNotNull(NavBackStacks.decode(tabs.encode()))
        assertEquals(listOf(note), (restoredTabs.model as TabsModel).overlays.map { it.route })

        val stack = stacks()
        stack.push(note)
        stack.popOut(stack.top.id)
        val restoredStack = assertNotNull(NavBackStacks.decode(stack.encode()))
        assertEquals(listOf(note), restoredStack.windows().map { it.route })
        assertEquals(Route.Home, restoredStack.top.route)
    }

    @Test
    fun switchingLayoutsKeepsAPoppedOutComposerInItsWindow() {
        val stacks = stacks()
        stacks.push(profile)
        stacks.push(note)
        val composer = stacks.top
        stacks.popOut(composer.id)

        stacks.changeArrangement(NavArrangement.TABS)
        assertEquals(listOf(composer), stacks.windows())
        // Kept by the main host, so its window goes on with the same draft.
        assertTrue(composer in stacks.retained())

        stacks.changeArrangement(NavArrangement.STACK)
        assertEquals(listOf(composer), stacks.windows())
        assertTrue(composer in stacks.retained())
    }

    @Test
    fun aTabOpenedFromElsewhereCoversTheComposerUntilItsTabIsBack() {
        val stacks = stacks(NavArrangement.TABS)
        val home = stacks.top
        stacks.push(note, origin = home.id)
        val composer = stacks.tabs.overlays.single()

        // A notification or a deep link: no origin.
        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Restart))
        assertEquals(Route.Message, stacks.top.route)
        assertFalse(composer in stacks.shown(wide = true))
        // Still open, with its draft.
        assertTrue(composer in stacks.retained())

        // Back closes that tab, not the composer of another one, and the composer is back in view.
        assertTrue(stacks.back())
        assertEquals(home, stacks.top)
        assertEquals(listOf(home, composer), stacks.shown(wide = true))
    }

    @Test
    fun reopeningAClosedComposerShowsTheOneAlreadyOpen() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        stacks.back()
        stacks.push(note)

        assertTrue(stacks.tabs.reopenClosed())
        assertEquals(1, stacks.tabs.overlays.size)
    }

    @Test
    fun aPoppedOutComposerMovedBackIsNotATab() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        val composer = stacks.tabs.overlays.single()
        stacks.popOut(composer.id)

        stacks.tabs.setDetached(composer.id, false)

        assertEquals(listOf(Route.Home), stacks.tabs.strip().map { it.route })
        assertEquals(listOf(composer), stacks.tabs.overlays.toList())
        assertTrue(stacks.windows().isEmpty())
        assertEquals(listOf(stacks.top, composer), stacks.shown(wide = true))
    }

    @Test
    fun aComposerAskedForFromAMovedOutTabOpensOverTheMainWindow() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(profile)
        val window = stacks.top
        stacks.tabs.setDetached(window.id, true)

        stacks.push(note, origin = window.id)

        val composer = stacks.tabs.overlays.single()
        assertEquals(listOf(stacks.top, composer), stacks.shown(wide = true))
    }

    @Test
    fun aComposerWaitingOnItsPostStaysOpenOverTheTabs() {
        val stacks = stacks(NavArrangement.TABS)
        stacks.push(note)
        val composer = stacks.tabs.overlays.single()
        var posting = true
        composer.holdOpen = { posting }

        assertFalse(stacks.back())
        assertFalse(stacks.close(composer.id))
        // A place asked for from it opens, and the composer stays behind it with its post.
        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Section, origin = composer.id))
        assertEquals(listOf(composer), stacks.tabs.overlays.toList())

        posting = false
        assertTrue(stacks.close(composer.id))
        assertTrue(stacks.tabs.overlays.isEmpty())
    }

    @Test
    fun aPoppedOutComposerWaitingOnItsPostKeepsItsWindow() {
        val stacks = stacks()
        stacks.push(note)
        val composer = stacks.top
        stacks.popOut(composer.id)
        composer.holdOpen = { true }

        assertFalse(stacks.close(composer.id))
        assertFalse(stacks.back(composer))
        assertEquals(listOf(composer), stacks.windows())
    }

    @Test
    fun aPlaceAskedForByAComposerReplacesItInTheStack() {
        val stacks = stacks()
        stacks.push(profile)
        stacks.push(note)
        val composer = stacks.top

        // The chat the composer created: back from it returns to the profile, not to the form.
        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Restart, origin = composer.id))
        assertEquals(listOf(Route.Home, profile, Route.Message), stacks.stack.map { it.route })
    }

    @Test
    fun aPlaceAskedForByAPoppedOutComposerClosesItsWindow() {
        val stacks = stacks()
        stacks.push(note)
        val composer = stacks.top
        stacks.popOut(composer.id)

        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Restart, origin = composer.id))
        assertTrue(stacks.windows().isEmpty())
        assertEquals(Route.Message, stacks.top.route)
    }

    @Test
    fun aComposerWaitingOnItsPostStaysUnderThePlaceItAskedFor() {
        val stacks = stacks()
        stacks.push(note)
        val composer = stacks.top
        composer.holdOpen = { true }

        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Restart, origin = composer.id))
        assertEquals(listOf(Route.Home, note, Route.Message), stacks.stack.map { it.route })
    }

    @Test
    fun aRestartWithNoOriginLeavesTheComposerInTheStack() {
        val stacks = stacks()
        stacks.push(note)

        stacks.open(NavRequest(Route.Message, NavRequest.Kind.Restart))
        assertEquals(listOf(Route.Home, note, Route.Message), stacks.stack.map { it.route })
    }

    @Test
    fun withoutDestinationsNothingIsAComposer() {
        val stacks = NavBackStacks().apply { changeArrangement(NavArrangement.TABS) }
        stacks.push(note)

        assertEquals(note, stacks.top.route)
        assertTrue((stacks.model as TabsModel).overlays.isEmpty())
        assertNull(stacks.windows().firstOrNull())
    }
}
