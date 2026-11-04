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
package com.vitorpamplona.amethyst.desktop.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.components.ScopedViewModelStore
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composable
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableArgs
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * The ViewModels of a navigation entry live as long as the arguments they were built for: handing
 * the screen on top new arguments in place (`newStack`) clears the previous arguments' ViewModels,
 * a pop clears them too, and a tab the user only left keeps them.
 */
class RouteScopedViewModelsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private val created = mutableListOf<String>()
    private val cleared = mutableListOf<String>()

    private inner class Tracked(
        val label: String,
    ) : ViewModel() {
        init {
            created += label
        }

        override fun onCleared() {
            cleared += label
        }
    }

    // The same key for every version, as a screen with an unkeyed ViewModel would ask.
    @Composable
    private fun Screen(label: String) {
        val viewModel = rememberViewModel(key = "screen") { Tracked(label) }
        Text(viewModel.label)
    }

    private val nav = Nav(NavBackStacks(), scope)

    private fun show(home: @Composable () -> Unit = { Screen("home") }) {
        val destinations =
            NavDestinations().apply {
                composable<Route.Home> { home() }
                composable<Route.Message> { Screen("messages") }
                composableArgs<Route.Note> { Screen("note:${it.id}") }
            }
        val root =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        // A started window, as in the app: a composition-scoped store clears on leaving only then.
        val window =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
            }
        compose.setContent {
            CompositionLocalProvider(LocalViewModelStoreOwner provides root, LocalLifecycleOwner provides window) {
                NavigationHost(nav, destinations)
            }
        }
        compose.waitForIdle()
    }

    private fun act(change: NavBackStacks.() -> Unit) {
        compose.runOnIdle { nav.stacks.change() }
        compose.waitForIdle()
    }

    @Test
    fun newArgumentsInPlaceClearThePreviousArgumentsViewModels() {
        show()
        act { push(Route.Note("a")) }
        val entry = nav.stacks.top

        act { newStack(Route.Note("b")) }

        assertSame(entry, nav.stacks.top, "the same entry takes the new arguments")
        compose.onNodeWithText("note:b").assertExists()
        assertEquals(listOf("home", "note:a", "note:b"), created)
        assertEquals(listOf("note:a"), cleared)
    }

    @Test
    fun aPoppedScreenClearsItsViewModels() {
        show()
        act { push(Route.Note("a")) }
        act { newStack(Route.Note("b")) }

        act { pop() }

        assertEquals(listOf("note:a", "note:b"), cleared)
    }

    @Test
    fun aTabTheUserLeftKeepsItsViewModels() {
        show()
        act { switchTab(Route.Message) }
        act { switchTab(Route.Home) }
        act { switchTab(Route.Message) }

        compose.onNodeWithText("messages").assertExists()
        assertEquals(listOf("home", "messages"), created)
        assertEquals(emptyList(), cleared)
    }

    @Test
    fun aScopedStoreSurvivesAScreenPushedOnTopAndClearsOnANewKey() {
        // The shape DVM content discovery uses for its per-request results.
        val request = mutableStateOf("r1")
        show { key(request.value) { ScopedViewModelStore { Screen("results:${request.value}") } } }

        // Home leaves the composition under the pushed note, and comes back with the same model.
        act { push(Route.Note("a")) }
        act { pop() }
        assertEquals(listOf("results:r1", "note:a"), created)
        assertEquals(listOf("note:a"), cleared)

        // A new key (a refresh with a new request) clears the previous key's model.
        compose.runOnIdle { request.value = "r2" }
        compose.waitForIdle()
        assertEquals(listOf("results:r1", "note:a", "results:r2"), created)
        assertEquals(listOf("note:a", "results:r1"), cleared)
    }
}
