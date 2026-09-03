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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * [Nav] queues its work on that same dispatcher, so a pop of its own can land inside that gap. The
 * stretch a person can actually hit is the keyboard settle — tap the back arrow with a text field
 * focused, see nothing happen, swipe back — so [Nav] publishes it as `isNavigating` and the shell
 * hands the gesture to a no-op handler for its duration.
 *
 * The obvious way to shrink the gap further — starting the transition inline on the caller instead
 * of on the nav dispatcher — is the thing these also rule out: it drags the NavController onto
 * whatever thread asked, and `popBack` is routinely called from `Dispatchers.IO`.
 *
 * [NavImeSettleTest] covers the ordering (keyboard first, then navigate) that must survive all of
 * this.
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
    fun everyTransitionRunsOnTheNavDispatcherNotTheCallers() =
        runTest {
            // Screens pop from inside AccountViewModel.launchSigner — viewModelScope.launch(IO) —
            // e.g. NewProductScreen's onPost. A NavBackStackEntry's LifecycleRegistry is
            // main-thread enforced, so a transition that ran inline on the caller would trade the
            // crash this class is about for "Method setCurrentState must be called on the main
            // thread".
            // Thread identity, not name: coroutines' debug mode rewrites the name with the
            // running coroutine's, so two dispatches on one thread read as different names.
            val navThreads = mutableListOf<Thread>()
            val controller =
                mockk<NavHostController>(relaxed = true) {
                    every { navigateUp() } answers {
                        navThreads.add(Thread.currentThread())
                        true
                    }
                }
            val nav = Nav(controller, this, settledKeyboard)
            lateinit var callerThread: Thread

            withContext(Dispatchers.IO) {
                callerThread = Thread.currentThread()
                nav.popBack()
            }
            advanceUntilIdle()

            // advanceUntilIdle drives the nav scope's dispatcher on this thread, so this is the
            // thread the transition was supposed to land on.
            assertEquals(listOf(Thread.currentThread()), navThreads)
            assertNotEquals(callerThread, Thread.currentThread())
        }

    @Test
    fun aSettledKeyboardNeverReportsAnOpenGestureWindow() =
        runTest {
            val nav = Nav(controllerRecording(mutableListOf()), this, settledKeyboard)

            nav.popBack()
            runCurrent()

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
            runCurrent()
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
