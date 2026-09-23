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

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A nav-bar tap must land on the tab itself, never on whatever the user had pushed on top of one.
 *
 * The phone bottom bar gets this for free — it hides itself off tab roots, so it can only ever be
 * tapped from one. The large-screen navigation rail stays on screen the whole time, which is where
 * the gap showed: tapping Home from a thread three screens deep came back to that thread instead of
 * the feed.
 *
 * Two rules keep that from happening, one per test below:
 *
 * 1. Pushes above a tab root are dropped, unsaved, on the way out — anything saved is eligible to be
 *    replayed by a later `restoreState`.
 * 2. Home is never restored onto. `popUpTo(Home) { inclusive = false; saveState = true }` files the
 *    popped entries under the popUpTo target's own destination id as well as the popped tab's — see
 *    the `if (!inclusive)` branch of `NavControllerImpl.executePopOperations` — so
 *    `navigate(Home) { restoreState = true }` handed Home back the stack the user had left behind in
 *    some *other* tab. Every other tab restores as before; that is what keeps its ViewModelStore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavBottomBarStackTest {
    /** A back-stack entry that is, or isn't, a tab root as far as [isBottomNavRoot] can tell. */
    private fun entry(isTabRoot: Boolean): NavBackStackEntry =
        mockk<NavBackStackEntry>(relaxed = true) {
            every { savedStateHandle.get<Boolean>(BOTTOM_NAV_ROOT_KEY) } returns isTabRoot
        }

    /**
     * Taps [route] on a controller whose back stack holds none of the nav-bar destinations, and
     * returns the options the resulting navigate was built with.
     */
    private fun TestScope.optionsForTapping(route: Route): NavOptions {
        val options = slot<NavOptionsBuilder.() -> Unit>()
        val controller =
            mockk<NavHostController>(relaxed = true) {
                every { currentBackStackEntry } returns entry(isTabRoot = true)
                every { navigate(route, capture(options)) } returns Unit
            }

        Nav(controller, this).navBottomBar(route)
        advanceUntilIdle()

        return navOptions(options.captured)
    }

    @Test
    fun dropsWhatTheUserPushedOnTopOfTheTabBeforeSwitchingTabs() =
        runTest {
            // Home > Note > Profile: two pushes sitting on a tab root.
            val controller =
                mockk<NavHostController>(relaxed = true) {
                    every { currentBackStackEntry } returnsMany
                        listOf(entry(isTabRoot = false), entry(isTabRoot = false), entry(isTabRoot = true))
                    every { popBackStack() } returns true
                }

            Nav(controller, this).navBottomBar(Route.Message)
            advanceUntilIdle()

            // Exactly the two pushes and no more: the tab root itself stays, so the navigate below
            // saves a tab root rather than a branch that could be replayed later.
            verify(exactly = 2) { controller.popBackStack() }
        }

    @Test
    fun neverRestoresASavedStackOntoHome() =
        runTest {
            assertFalse(optionsForTapping(Route.Home).shouldRestoreState())
        }

    @Test
    fun restoresTheSavedStackOfEveryOtherTab() =
        runTest {
            assertTrue(optionsForTapping(Route.Message).shouldRestoreState())
        }
}
