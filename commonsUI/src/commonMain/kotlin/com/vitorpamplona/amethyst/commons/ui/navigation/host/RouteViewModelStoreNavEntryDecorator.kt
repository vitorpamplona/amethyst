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
package com.vitorpamplona.amethyst.commons.ui.navigation.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigation3.runtime.NavEntryDecorator
import com.vitorpamplona.amethyst.commons.model.navigation.NavStackEntry

/**
 * Navigation 3's ViewModel-store decorator, scoped to each version of an entry's arguments.
 *
 * The stock decorator keys a store by the entry's `contentKey`, which never changes, so when
 * `NavBackStacks.newStack` hands the screen on top new arguments in place (Thread A to Thread B,
 * Profile A to Profile B, a deep link into the Notifications tab), every ViewModel of the previous
 * arguments stayed alive, still collecting, until the entry was popped, and a tab root is never
 * popped. Here the store is keyed by the entry and its [NavStackEntry.routeVersion]: a new version
 * gets an empty store and a freshly built screen, and the previous version's store is cleared.
 *
 * Like the stock decorator, the stores live at the host, not inside the screen: a tab the user left
 * leaves the composition but stays on the back stack, and its ViewModels must survive that. A store
 * is cleared only when its entry is popped or re-argued.
 */
@Composable
internal fun rememberRouteViewModelStoreNavEntryDecorator(): NavEntryDecorator<NavStackEntry> {
    val provider = rememberViewModelStoreProvider()
    return remember(provider) { routeViewModelStoreNavEntryDecorator(provider) }
}

private fun routeViewModelStoreNavEntryDecorator(provider: ViewModelStoreProvider): NavEntryDecorator<NavStackEntry> {
    // contentKey -> the store key of the version on screen, so a pop clears that version's store.
    // Touched only from composition and its effects, on the main thread.
    val currentStoreKeys = HashMap<Any, String>()

    return NavEntryDecorator(
        onPop = { contentKey -> currentStoreKeys.remove(contentKey)?.let(provider::clearKey) },
        decorate = { entry ->
            val navStackEntry = entry.metadata[NavDestinations.NAV_STACK_ENTRY] as NavStackEntry
            val version = navStackEntry.routeVersion
            val storeKey = "${entry.contentKey}#v$version"

            DisposableEffect(storeKey) {
                currentStoreKeys[entry.contentKey] = storeKey
                onDispose {
                    // Leaving the composition for a reason other than a new version (a tab left, a
                    // pop, a configuration change) keeps the store: a pop clears it through onPop.
                    if (navStackEntry.routeVersion != version) provider.clearKey(storeKey)
                }
            }

            // Keyed so the screen is rebuilt for the new arguments too: nothing it remembered from
            // the previous ones (a ViewModel included) survives into the new version.
            key(version) {
                val owner = rememberViewModelStoreOwner(storeKey, provider)
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
            }
        },
    )
}
