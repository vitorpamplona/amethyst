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

import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import com.vitorpamplona.amethyst.ui.navigation.navs.ImeSettler
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.routes.getRouteWithArguments
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

/**
 * Leaving a screen while the soft keyboard is still animating strands `imePadding()` at keyboard
 * height for the whole app, because `WindowInsets.ime` is a single shared holder. The fix is that
 * [Nav] waits for the IME to be gone before it moves, so these assert the ordering rather than any
 * visual result: every transition must settle the keyboard *first*.
 *
 * Without the settle calls in [Nav] each of these records only "navigate" and fails.
 *
 * This is the prevention half. The system's own back gesture never reaches [Nav] — the first back
 * press with a keyboard up is consumed by the IME — so it can still cancel an animation and freeze
 * the inset. `SafeImeInsets` is the backstop for that; see `SafeImeInsetsTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavImeSettleTest {
    private fun controllerRecording(order: MutableList<String>): NavHostController =
        mockk<NavHostController>(relaxed = true) {
            every { navigate(any<Route>(), any<NavOptionsBuilder.() -> Unit>()) } answers
                { order.add("navigate") }
            every { navigate(any<Route>()) } answers { order.add("navigate") }
            every { navigateUp() } answers {
                order.add("navigate")
                true
            }
        }

    @Test
    fun popBackSettlesTheKeyboardBeforeNavigating() =
        runTest {
            val order = mutableListOf<String>()
            val nav = Nav(controllerRecording(order), this, ImeSettler { order.add("settle") })

            nav.popBack()
            advanceUntilIdle()

            assertEquals(listOf("settle", "navigate"), order)
        }

    @Test
    fun bottomBarSettlesTheKeyboardBeforeNavigating() =
        runTest {
            // The search tab focuses its field on arrival, so the keyboard is already up when the
            // user taps another tab — the exit that has no BackHandler and no top bar to guard it.
            val order = mutableListOf<String>()
            val controller = controllerRecording(order)

            // navBottomBar reads back where the navigate landed and re-takes the tab when that isn't
            // the route it asked for (see NavBottomBarTabRestoreTest). A relaxed mock lands nowhere,
            // so answer that it landed on the tab and keep this test about the keyboard.
            mockkStatic("com.vitorpamplona.amethyst.ui.navigation.routes.RoutesKt")
            every { getRouteWithArguments(any<KClass<Route>>(), controller) } returns Route.Home

            try {
                Nav(controller, this, ImeSettler { order.add("settle") }).navBottomBar(Route.Home)
                advanceUntilIdle()
            } finally {
                unmockkStatic("com.vitorpamplona.amethyst.ui.navigation.routes.RoutesKt")
            }

            assertEquals(listOf("settle", "navigate"), order)
        }

    @Test
    fun newStackSettlesTheKeyboardBeforeNavigating() =
        runTest {
            val order = mutableListOf<String>()
            val nav = Nav(controllerRecording(order), this, ImeSettler { order.add("settle") })

            nav.newStack(Route.Home)
            advanceUntilIdle()

            assertEquals(listOf("settle", "navigate"), order)
        }

    @Test
    fun aSlowKeyboardStillHoldsTheNavigationBack() =
        runTest {
            // The real settler suspends for the length of the IME close animation. Navigation must
            // wait for it, not fire alongside it — that overlap is the bug.
            val order = mutableListOf<String>()
            val nav =
                Nav(
                    controllerRecording(order),
                    this,
                    ImeSettler {
                        delay(250)
                        order.add("settle")
                    },
                )

            nav.popBack()
            assertEquals(emptyList<String>(), order)

            advanceUntilIdle()
            assertEquals(listOf("settle", "navigate"), order)
        }
}
