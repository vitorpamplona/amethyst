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
import androidx.navigation.NavOptionsBuilder
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A nav-bar tap must land on the tab itself, never on whatever the user had pushed on top of one.
 *
 * The phone bottom bar gets this for free — it hides itself off tab roots, so it can only ever be
 * tapped from one. The large-screen navigation rail stays on screen the whole time, which is where
 * the gap showed: tapping Home from a thread three screens deep came back to that thread instead of
 * the feed.
 *
 * Two things had to be true for that, and each test below pins one of them down:
 *
 * 1. Pushes above a tab root are dropped, unsaved, on the way out. Anything saved is eligible to be
 *    replayed by a later `restoreState`.
 * 2. A tab that is already on the back stack — always the case for Home, which is the graph's start
 *    destination — is reached by popping back to it, not by navigating to it.
 *
 * (2) is what actually broke. `popUpTo(Home) { inclusive = false; saveState = true }` files the
 * popped entries under the popUpTo target's own destination id as well as the popped tab's — see
 * the `if (!inclusive)` branch of `NavControllerImpl.executePopOperations` — so a later
 * `navigate(Home) { restoreState = true }` handed Home back the stack the user had left behind in
 * some *other* tab.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavBottomBarStackTest {
    /** A back-stack entry that is or isn't a tab root, as far as [isBottomNavRoot] can tell. */
    private fun entry(isTabRoot: Boolean): NavBackStackEntry =
        mockk<NavBackStackEntry>(relaxed = true) {
            every { savedStateHandle.get<Boolean>(BOTTOM_NAV_ROOT_KEY) } returns isTabRoot
        }

    @Test
    fun dropsWhatTheUserPushedOnTopOfTheTabBeforeSwitchingTabs() =
        runTest {
            // Home > Note > Profile, with Home stamped as the tab root the two pushes sit on.
            val controller =
                mockk<NavHostController>(relaxed = true) {
                    every { currentBackStackEntry } returnsMany
                        listOf(entry(isTabRoot = false), entry(isTabRoot = false), entry(isTabRoot = true))
                    every { popBackStack() } returns true
                    // Messages is not on the stack until the navigate below puts it there.
                    every { getBackStackEntry(Route.Message) } throws
                        IllegalArgumentException("No destination is on the NavController's back stack") andThen
                        entry(isTabRoot = true)
                }

            Nav(controller, this).navBottomBar(Route.Message)
            advanceUntilIdle()

            // Exactly the two pushes, and no more: the tab root itself stays, keeping its
            // ViewModelStore for the tab's own restore.
            verify(exactly = 2) { controller.popBackStack() }
            verify(exactly = 1) { controller.navigate(Route.Message, any<NavOptionsBuilder.() -> Unit>()) }
        }

    @Test
    fun popsBackToATabThatIsAlreadyOnTheStackInsteadOfNavigatingToIt() =
        runTest {
            val home = entry(isTabRoot = true)
            val controller =
                mockk<NavHostController>(relaxed = true) {
                    every { currentBackStackEntry } returns home
                    every { getBackStackEntry(Route.Home) } returns home
                }

            Nav(controller, this).navBottomBar(Route.Home)
            advanceUntilIdle()

            // Popping back to Home keeps the feed's own state and, unlike navigating, cannot pick up
            // a saved stack that popUpTo(Home) filed under Home's destination id.
            verify(exactly = 1) { controller.popBackStack(Route.Home, inclusive = false, saveState = true) }
            verify(exactly = 0) { controller.navigate(any<Route>(), any<NavOptionsBuilder.() -> Unit>()) }
        }
}
