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
package com.vitorpamplona.amethyst.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.reflect.KClass

/**
 * `saveState`/`restoreState` are keyed by DESTINATION, so one destination serves every route that
 * differs only in its arguments. `Route.Notification?scrollToEventId={scrollToEventId}` is both the
 * bottom-bar tab (`Route.Notification()`, no id) and the deep link a tapped push notification lands
 * on (`Route.Notification(scrollToEventId = <id>)`), so the restore in [Nav.navBottomBar] can hand
 * back the deep-link entry when the user taps the Notifications tab — as it can hand back another
 * pinned web app's entry, which is the same bug with a different route.
 *
 * The old code then looked the requested route up with `getBackStackEntry(route)`, which matches on
 * arguments too and *throws* when they differ:
 *
 *     java.lang.IllegalArgumentException: No destination with route
 *     …Route.Notification?scrollToEventId=null is on the NavController's back stack.
 *     The current destination is …Route.Notification?scrollToEventId={scrollToEventId}
 *
 * These assert the landing is now checked by reading back where we ended up, and that a mismatch
 * re-takes the tab fresh instead of leaving the user on someone else's arguments.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavBottomBarTabRestoreTest {
    private val navigations = mutableListOf<Route>()
    private val options = mutableListOf<NavOptionsBuilder.() -> Unit>()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var controller: NavHostController

    @Before
    fun setUp() {
        mockkStatic("com.vitorpamplona.amethyst.ui.navigation.routes.RoutesKt")

        savedStateHandle = mockk(relaxed = true)
        val entry = mockk<NavBackStackEntry>(relaxed = true) { every { this@mockk.savedStateHandle } returns this@NavBottomBarTabRestoreTest.savedStateHandle }

        controller =
            mockk(relaxed = true) {
                every { navigate(any<Route>(), any<NavOptionsBuilder.() -> Unit>()) } answers {
                    navigations.add(firstArg())
                    options.add(secondArg())
                }
                every { currentBackStackEntry } returns entry
            }
    }

    @After
    fun tearDown() {
        unmockkStatic("com.vitorpamplona.amethyst.ui.navigation.routes.RoutesKt")
    }

    /** Replays a captured `navigate {}` block so its flags can be asserted. */
    private fun optionsAt(index: Int) = NavOptionsBuilder().apply(options[index])

    private fun landsOn(route: Route?) {
        every { getRouteWithArguments(any<KClass<Route>>(), controller) } returns route
    }

    @Test
    fun aTabRestoredWithAnotherRoutesArgumentsIsTakenFresh() =
        runTest {
            // The user tapped a push notification (Route.Notification(scrollToEventId = <id>)), then
            // tapped the Notifications tab. The restore hands the deep-link entry back.
            landsOn(Route.Notification(scrollToEventId = "f".repeat(64)))

            Nav(controller, this).navBottomBar(Route.Notification())
            advanceUntilIdle()

            assertEquals(listOf(Route.Notification(), Route.Notification()), navigations)

            // The retake must not restore again — the saved entry is the one we are refusing — and
            // must not reuse the top, which is that same entry.
            assertTrue(optionsAt(0).restoreState)
            assertTrue(optionsAt(0).launchSingleTop)
            assertFalse(optionsAt(1).restoreState)
            assertFalse(optionsAt(1).launchSingleTop)
        }

    @Test
    fun aTabRestoredWithItsOwnArgumentsIsKept() =
        runTest {
            // Nothing else has been on this destination, so the restore returns the tab itself and
            // its saved scroll/ViewModel state survives.
            landsOn(Route.Notification())

            Nav(controller, this).navBottomBar(Route.Notification())
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Notification()), navigations)
        }

    @Test
    fun aTabThatIsNotOnTheStackAfterNavigatingIsTakenFresh() =
        runTest {
            // getRouteWithArguments answers null when the current entry is some other destination.
            landsOn(null)

            Nav(controller, this).navBottomBar(Route.Home)
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Home, Route.Home), navigations)
        }

    @Test
    fun theLandedEntryIsMarkedATabRoot() =
        runTest {
            landsOn(Route.Notification())

            Nav(controller, this).navBottomBar(Route.Notification())
            advanceUntilIdle()

            verify { savedStateHandle[BOTTOM_NAV_ROOT_KEY] = true }
        }

    @Test
    fun anEmptyBackStackDoesNotCrash() =
        runTest {
            // Nothing to mark: the stamp is best-effort, never a reason to throw out of navigation.
            every { controller.currentBackStackEntry } returns null
            landsOn(Route.Notification())

            Nav(controller, this).navBottomBar(Route.Notification())
            advanceUntilIdle()

            assertEquals(listOf<Route>(Route.Notification()), navigations)
        }
}
