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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `NavHost` does not read the back stack when a predictive back gesture starts. `onBackStarted`
 * only *launches* a coroutine on the composition scope; the body runs a main-thread message later,
 * reads a `collectAsState` mirror of `ComposeNavigator.backStack`, and calls `prepareForTransition`
 * on its top two entries — which validates against the NavController's live `backQueue` and throws
 * `IllegalStateException: Cannot transition entry that is not in the back stack` when they
 * disagree.
 *
 * [Nav] queues its work on that same dispatcher, so a `launch`ed pop could land inside that gap:
 * tap the back arrow, see nothing happen, swipe back, and the tap's pop commits between the
 * gesture's start and the handler reading the stack. These pin the two halves of the fix — the
 * transition runs on the caller's own message when there is nothing to wait for, and the shell is
 * told when there *is* something to wait for so it can hold the gesture off.
 *
 * [NavImeSettleTest] covers the ordering (keyboard first, then navigate) that must survive both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavBackStackRaceTest {
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

    /** Nothing to settle: the settler returns without suspending. */
    private val settledKeyboard = ImeSettler { }

    @Test
    fun popBackMovesTheBackStackOnTheCallersOwnMessage() =
        runTest {
            val order = mutableListOf<String>()
            val nav = Nav(controllerRecording(order), this, settledKeyboard)

            nav.popBack()

            // No advanceUntilIdle: a pop that only lands on a later dispatch is a pop that can
            // land inside a back gesture that has already started.
            assertEquals(listOf("navigate"), order)
        }

    @Test
    fun theOtherTransitionsStayOnTheDispatcher() =
        runTest {
            // Only back runs inline. The rest keep the hop on purpose: NavigateIfIntentRequested
            // calls newStack straight from a composable body, and moving the back stack during
            // composition writes ComposeNavigator.isPop while the NavHost is reading it.
            val order = mutableListOf<String>()
            val nav = Nav(controllerRecording(order), this, settledKeyboard)

            nav.nav(Route.Home)
            nav.newStack(Route.Home)
            nav.navBottomBar(Route.Home)
            nav.popUpTo(Route.Home, Route.Home::class)
            assertEquals(emptyList<String>(), order)

            advanceUntilIdle()
            assertEquals(listOf("navigate", "navigate", "navigate", "navigate"), order)
        }

    @Test
    fun aSettledKeyboardNeverReportsAnOpenGestureWindow() =
        runTest {
            val nav = Nav(controllerRecording(mutableListOf()), this, settledKeyboard)

            nav.popBack()

            assertFalse(nav.isNavigating)
        }

    @Test
    fun aRetractingKeyboardHoldsTheGestureWindowOpenUntilTheTransitionLands() =
        runTest {
            // The keyboard is up: the transition parks in the settler and the back stack only
            // moves later. That is the one window a back gesture can still race, so the shell has
            // to be able to see it and hold back off.
            val order = mutableListOf<String>()
            val nav =
                Nav(
                    controllerRecording(order),
                    this,
                    ImeSettler { delay(IME_SETTLE_MS) },
                )

            nav.popBack()
            assertTrue(nav.isNavigating)
            assertEquals(emptyList<String>(), order)

            advanceUntilIdle()
            assertFalse(nav.isNavigating)
            assertEquals(listOf("navigate"), order)
        }

    companion object {
        private const val IME_SETTLE_MS = 250L
    }
}
