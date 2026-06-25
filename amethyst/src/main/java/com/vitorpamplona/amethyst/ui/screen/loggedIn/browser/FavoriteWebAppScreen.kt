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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.napplet.WebUrlNetworkRegistry
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.favoriteIds
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabChrome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost

/**
 * A pinned web client rendered as an **in-app tab**. The embedded `:napplet` browser surface is drawn
 * by the persistent [EmbeddedTabHost]/[com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer]
 * layer, which keeps the session warm across tab swaps. This screen owns only the chrome — it publishes
 * the controls as an [EmbeddedTabChrome] and the layer draws a top pull-down sheet over the (z-below)
 * surface. No title bar or editable address bar.
 *
 * Only bottom-row favorites stay warm; if this URL isn't a bottom-bar favorite, its session is evicted
 * (restarted) when the screen leaves. Requires API 30+ for the cross-process surface.
 */
@Composable
fun FavoriteWebAppScreen(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedFavoriteTab(url, accountViewModel, nav)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedFavoriteTab(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    // Matches FavoriteApp.WebUrl.id, so warm-keep membership lines up with the bottom-bar favorites.
    val id = "url:$url"

    var currentUrl by remember { mutableStateOf(url) }
    var canGoBack by remember { mutableStateOf(false) }

    val proxyAvailable = remember { Amethyst.instance.torManager.activePortOrNull.value != null }
    // Start from this site's remembered Tor choice (some sites' servers reject Tor exits, so the user
    // can opt one out and it must stick). Only meaningful when Tor is actually available.
    var torOn by remember { mutableStateOf(proxyAvailable && WebUrlNetworkRegistry.useTor(url)) }

    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    val isFavorite = remember(apps, currentUrl) { apps.any { it is FavoriteApp.WebUrl && it.url == currentUrl } }

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    val controller =
        remember(id) {
            EmbeddedTabFactory.acquireBrowser(context, url, backgroundColor)
        }

    // Keep the URL/back callback fresh (cheap, needs the latest closure).
    SideEffect {
        controller.onUrlChanged = { newUrl, back ->
            if (newUrl != "about:blank") currentUrl = newUrl
            canGoBack = back
        }
    }

    // Rebuilt only when a displayed value changes, so the tab layer isn't recomposed every frame.
    val chrome =
        remember(currentUrl, torOn, proxyAvailable, isFavorite) {
            EmbeddedTabChrome(
                title = hostLabel(currentUrl),
                isSandbox = false,
                onReload = { controller.reload() },
                onOpenFull = { FavoriteAppLauncher.launchUrl(context, url) },
                torOn = if (proxyAvailable) torOn else null,
                onToggleTor = {
                    torOn = !torOn
                    controller.setTor(torOn)
                    WebUrlNetworkRegistry.set(url, torOn)
                },
                isFavorite = isFavorite,
                onFavorite = {
                    val favId = "url:$currentUrl"
                    if (FavoriteAppsRegistry.isFavorite(favId)) {
                        FavoriteAppsRegistry.remove(favId)
                    } else {
                        FavoriteAppsRegistry.add(FavoriteApp.WebUrl(currentUrl, hostLabel(currentUrl), System.currentTimeMillis()))
                    }
                },
            )
        }
    // Publish the top-sheet controls to the tab layer (which draws them over the z-below surface). In a
    // SideEffect so it runs after [setActive]; the host short-circuits the identical remembered instance.
    SideEffect { EmbeddedTabHost.setActiveChrome(id, chrome) }

    val bottomBarFlow = accountViewModel.settings.uiSettingsFlow.bottomBarItems
    DisposableEffect(id) {
        val token = EmbeddedTabHost.setActive(id)
        onDispose {
            EmbeddedTabHost.clearActiveIfOwner(token)
            EmbeddedTabHost.clearActiveChrome(id)
            // Only bottom-row apps stay warm; anything else restarts when it leaves.
            if (id !in bottomBarFlow.value.favoriteIds()) EmbeddedTabHost.evict(id)
        }
    }

    BackHandler(enabled = canGoBack) { controller.back() }

    Scaffold(
        bottomBar = {
            AppBottomBar(Route.FavoriteWebApp(url), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        // Reserve the full content area; the warm surface, its top sheet, and the loading/error overlay
        // are all drawn over these bounds by the tab layer.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { EmbeddedTabHost.reportBounds(it.boundsInWindow()) },
        )
    }
}

/** The host of [url] for the tab title, falling back to the raw string. */
internal fun hostLabel(url: String): String = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
