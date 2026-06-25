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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.embed

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.napplet.NappletNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.favoriteIds
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

// Up to PRELOAD_ATTEMPTS sweeps, PRELOAD_RETRY_MS apart, give a slow napplet event or a still-connecting
// Tor proxy time to settle before we give up and leave that tab to load on first visit (~45 s total).
private const val PRELOAD_ATTEMPTS = 15
private const val PRELOAD_RETRY_MS = 3_000L

/**
 * Warms every bottom-bar favorite at startup so the first tap is instant — the surfaces are built,
 * attached (off-screen), and their content downloaded before the user ever opens them. Idle work that
 * shares the exact warm sessions the favorite screens use (same ids via [EmbeddedTabFactory]).
 *
 * Mount once next to [EmbeddedTabLayer]. Draws nothing; it just drives acquisition. Napplet favorites
 * whose events haven't synced yet, and Tor-routed sites whose proxy isn't up yet, are retried for a
 * bounded window (the latter so a clearnet preload can never race ahead of Tor).
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun EmbeddedTabPreloader(accountViewModel: AccountViewModel) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    val bottomBarItems by accountViewModel.settings.uiSettingsFlow.bottomBarItems
        .collectAsStateWithLifecycle()
    val favoriteIds = bottomBarItems.favoriteIds()

    // Give preloaded surfaces a realistic viewport before any tab is visited, so they download as a
    // full-size page rather than at the 1dp off-screen fallback. The first real visit corrects it.
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration) {
        with(density) {
            EmbeddedTabHost.seedBoundsIfUnset(Rect(0f, 0f, configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx()))
        }
    }

    LaunchedEffect(favoriteIds, backgroundColor) {
        if (favoriteIds.isEmpty()) return@LaunchedEffect
        // Hydrate the per-site Tor/open-web choices BEFORE the first preload: a cold start otherwise reads
        // the bare Tor default and would route a site the user pinned to the open web through Tor (or stall
        // it waiting for Tor), which is exactly what breaks Tor-incompatible servers.
        WebAppNetworkRegistry.init(context)
        NappletNetworkRegistry.init(context)
        WebAppNetworkRegistry.awaitReady()
        NappletNetworkRegistry.awaitReady()
        var attempt = 0
        while (isActive) {
            val byId = FavoriteAppsRegistry.favorites.value.associateBy { it.id }
            var stillPending = false
            for (id in favoriteIds) {
                val app = byId[id] ?: continue
                if (!EmbeddedTabFactory.preload(context, app, backgroundColor)) stillPending = true
                // Each preload may build + attach a WebView on this (main) thread; yield between favorites
                // so the startup sweep doesn't monopolize the frame and jank the first paint.
                yield()
            }
            if (!stillPending || ++attempt >= PRELOAD_ATTEMPTS) break
            delay(PRELOAD_RETRY_MS)
        }
    }
}
