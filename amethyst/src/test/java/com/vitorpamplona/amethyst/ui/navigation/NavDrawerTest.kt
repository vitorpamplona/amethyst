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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A screen opened from the navigation drawer is a top-level section: it keeps the bottom bar
 * (issue #4141, where Pictures and every other drawer destination lost it). It still sits on top of
 * the screen the drawer was opened over, so it keeps its back arrow too; the bar is told apart
 * from an in-app push by the [DRAWER_ROOT_KEY] stamp these tests pin down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavDrawerTest {
    private fun entry(
        isTabRoot: Boolean = false,
        isDrawerRoot: Boolean = false,
    ): NavBackStackEntry =
        mockk<NavBackStackEntry>(relaxed = true) {
            every { savedStateHandle.get<Boolean>(BOTTOM_NAV_ROOT_KEY) } returns isTabRoot
            every { savedStateHandle.get<Boolean>(DRAWER_ROOT_KEY) } returns isDrawerRoot
        }

    /** A controller whose current entry becomes [pushed] once [route] is navigated to. */
    private fun controllerPushing(
        route: Route,
        pushed: NavBackStackEntry,
    ): NavHostController {
        var current = entry(isTabRoot = true)
        return mockk<NavHostController>(relaxed = true) {
            every { currentBackStackEntry } answers { current }
            every { navigate(route) } answers { current = pushed }
        }
    }

    @Test
    fun stampsTheEntryItOpens() =
        runTest {
            val pushed = entry()
            val controller = controllerPushing(Route.Pictures(), pushed)

            Nav(controller, this).navDrawer(Route.Pictures())
            advanceUntilIdle()

            verify(exactly = 1) { controller.navigate(Route.Pictures()) }
            verify(exactly = 1) { pushed.savedStateHandle[DRAWER_ROOT_KEY] = true }
        }

    @Test
    fun stampsTheEntryOfAResolvedRoute() =
        runTest {
            val pushed = entry()
            val controller = controllerPushing(Route.Articles, pushed)

            Nav(controller, this).navDrawer { Route.Articles }
            advanceUntilIdle()

            verify(exactly = 1) { pushed.savedStateHandle[DRAWER_ROOT_KEY] = true }
        }

    @Test
    fun plainPushesAreNotStamped() =
        runTest {
            val pushed = entry()
            val controller = controllerPushing(Route.Pictures(), pushed)

            Nav(controller, this).nav(Route.Pictures())
            advanceUntilIdle()

            verify(exactly = 0) { pushed.savedStateHandle[DRAWER_ROOT_KEY] = any<Boolean>() }
        }

    @Test
    fun aTabTappedFromADrawerScreenDropsItFirst() =
        runTest {
            // Home > Pictures (from the drawer): the bar now shows on Pictures, so it can be tapped
            // from there. The drawer entry is not a tab root and must not be saved into the tab.
            val controller =
                mockk<NavHostController>(relaxed = true) {
                    every { currentBackStackEntry } returnsMany
                        listOf(entry(isDrawerRoot = true), entry(isTabRoot = true))
                    every { popBackStack() } returns true
                }

            Nav(controller, this).navBottomBar(Route.Message)
            advanceUntilIdle()

            verify(exactly = 1) { controller.popBackStack() }
        }
}
