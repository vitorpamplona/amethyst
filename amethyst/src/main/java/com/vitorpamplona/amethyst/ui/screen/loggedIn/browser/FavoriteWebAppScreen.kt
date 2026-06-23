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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.favoriteIds
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.TorToggleButton

/**
 * A pinned web client rendered as an **in-app tab**. The embedded `:napplet` browser surface is drawn
 * by the persistent [EmbeddedTabHost]/[com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer]
 * layer, which keeps the session warm across tab swaps. This screen owns only the chrome — the shared
 * [EmbeddedTabTopBar] with the Tor onion as its leading affordance, plus reload + pop-out. Deliberately
 * no editable address bar.
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
    var torOn by remember { mutableStateOf(proxyAvailable) }

    val controller =
        remember(id) {
            EmbeddedTabHost.acquire(id) {
                val proxyPort = Amethyst.instance.torManager.activePortOrNull.value ?: -1
                EmbeddedBrowserController(context.applicationContext, proxyPort, proxyPort > 0).also { it.bind(url) }
            } as EmbeddedBrowserController
        }

    // Keep the URL/back callback fresh without re-binding the warm session.
    SideEffect {
        controller.onUrlChanged = { newUrl, back ->
            if (newUrl != "about:blank") currentUrl = newUrl
            canGoBack = back
        }
    }

    val bottomBarFlow = accountViewModel.settings.uiSettingsFlow.bottomBarItems
    DisposableEffect(id) {
        EmbeddedTabHost.setActive(id)
        onDispose {
            EmbeddedTabHost.clearActiveIfMatches(id)
            // Only bottom-row apps stay warm; anything else restarts when it leaves.
            if (id !in bottomBarFlow.value.favoriteIds()) EmbeddedTabHost.evict(id)
        }
    }

    BackHandler(enabled = canGoBack) { controller.back() }

    Scaffold(
        topBar = {
            EmbeddedTabTopBar(
                title = hostLabel(currentUrl),
                leading = {
                    if (proxyAvailable) {
                        TorToggleButton(torOn) {
                            torOn = !torOn
                            controller.setTor(torOn)
                        }
                    }
                },
                onReload = { controller.reload() },
                onPopOut = { FavoriteAppLauncher.launchUrl(context, url) },
            )
        },
        bottomBar = {
            AppBottomBar(Route.FavoriteWebApp(url), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        // Reserve the content area; the warm surface is positioned over these bounds by the tab layer.
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
