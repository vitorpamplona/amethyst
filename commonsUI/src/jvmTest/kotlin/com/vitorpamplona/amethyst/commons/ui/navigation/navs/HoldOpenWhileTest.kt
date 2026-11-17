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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.navigation.NavArrangement
import com.vitorpamplona.amethyst.commons.model.navigation.NavBackStacks
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.TabsModel
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavigationHost
import com.vitorpamplona.amethyst.commons.ui.navigation.host.composableFromEnd
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A screen held open by [HoldOpenWhile] stays held while it is a background tab: the host keeps a
 * background tab's ViewModels but stops composing it, and the hold must last as long as they do.
 */
class HoldOpenWhileTest {
    class Composer : ViewModel() {
        var posting by mutableStateOf(false)
    }

    @Test
    fun aBackgroundTabStaysHeldWhileItsScreenIsPosting() {
        val stacks = NavBackStacks(Route.Home).apply { changeArrangement(NavArrangement.TABS) }
        val nav = Nav(stacks, CoroutineScope(Dispatchers.Unconfined))
        var composer: Composer? = null
        val destinations =
            NavDestinations().apply {
                composableFromEnd<Route.Home> { Box(Modifier.fillMaxSize()) }
                composableFromEnd<Route.Settings> {
                    val vm = viewModel { Composer() }
                    composer = vm
                    HoldOpenWhile(vm) { vm.posting }
                    Box(Modifier.fillMaxSize())
                }
            }
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        val scene =
            ImageComposeScene(200, 200, Density(1f)) {
                AmethystPreviewTheme(dark = true) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        NavigationHost(nav, destinations)
                    }
                }
            }
        try {
            var time = 0L

            fun frames() = repeat(30) { scene.render(time).close().also { time += 16_000_000L } }

            val tabs = stacks.model as TabsModel
            val home = stacks.top
            stacks.push(Route.Settings)
            frames()
            val held = stacks.top
            assertNotNull(composer).posting = true

            // Another tab in front: the composer leaves composition, its ViewModel stays.
            tabs.activate(home.id)
            frames()

            assertFalse(stacks.close(held.id), "a background tab closed while its screen was posting")
            assertTrue(held in stacks.stack)

            composer!!.posting = false
            assertTrue(stacks.close(held.id))
            // Closed for good: its ViewModel is cleared and takes the hold with it.
            frames()
            assertNull(held.holdOpen)
        } finally {
            scene.close()
            owner.viewModelStore.clear()
        }
    }
}
