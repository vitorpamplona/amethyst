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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.favorites

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.napplethost.NappletEmbedContract
import com.vitorpamplona.amethyst.napplethost.NappletHostContract
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.navigation.bottombars.favoriteIds
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabChrome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost

/**
 * A favorited nsite/napplet rendered as an **in-app tab**. The verified-blob sandbox surface (hosted in
 * the keyless `:napplet` process by `NappletHostService`) is drawn by the persistent [EmbeddedTabHost]
 * layer, which keeps the session warm across tab swaps — the surface stays attached and just moves over
 * the area this screen reserves. The **trusted chrome** (sandbox shield, app name, "what it can access")
 * is drawn here in the main process; the sandbox must never draw chrome the user is meant to trust. The
 * pop-out hands the app to the full-screen `NappletHostActivity`.
 *
 * Only bottom-row favorites stay warm; otherwise the session is evicted (restarted) when the screen
 * leaves. Requires API 30+ for the cross-process surface.
 */
@Composable
fun FavoriteNappletScreen(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedNappletTab(coordinate, accountViewModel, nav)
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmbeddedNappletTab(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    // Matches FavoriteApp.NostrApp.id, so warm-keep membership lines up with the bottom-bar favorites.
    val id = "nostr:$coordinate"

    // Mint the verified launch params (a fresh token per resolve); null until the event loads.
    val params = remember(coordinate) { FavoriteAppLauncher.embedParams(context, coordinate) }
    if (params == null) {
        UnavailableTab(coordinate, accountViewModel, nav)
        return
    }

    // Carry the app's theme background into the keyless sandbox so its WebView doesn't flash white
    // before the app paints. Idempotent across recompositions (the params bundle is remembered).
    params.putInt(NappletHostContract.EXTRA_BG_COLOR, MaterialTheme.colorScheme.background.toArgb())

    val title = params.getString(NappletHostContract.EXTRA_TITLE).orEmpty()
    val capLabels = params.getStringArrayList(NappletHostContract.EXTRA_CAP_LABELS).orEmpty()
    val websiteMode = params.getBoolean(NappletHostContract.EXTRA_WEBSITE_MODE, false)
    val useTor = params.getBoolean(NappletHostContract.EXTRA_USE_TOR, true)

    var canGoBack by remember { mutableStateOf(false) }
    var showAccess by remember { mutableStateOf(false) }

    val controller =
        remember(id) {
            EmbeddedTabHost.acquire(id) {
                EmbeddedNappletController(context.applicationContext, params).also { it.bind() }
            } as EmbeddedNappletController
        }

    // Keep the controller callbacks fresh (cheap, need the latest closures).
    SideEffect {
        controller.onStateChanged = { canGoBack = it }
        controller.onNotice = { notice ->
            noticeResId(notice)?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }

    // Stable per app (title/coordinate don't change), so the tab layer isn't recomposed every frame.
    val chrome =
        remember(title, coordinate) {
            EmbeddedTabChrome(
                title = title.ifBlank { coordinate },
                isSandbox = true,
                onReload = { controller.reload() },
                onOpenFull = { FavoriteAppLauncher.launch(context, FavoriteApp.NostrApp(coordinate, title, System.currentTimeMillis())) },
                onInfo = { showAccess = true },
            )
        }
    // Publish the top-sheet controls to the tab layer (drawn over the z-below surface). In a SideEffect
    // so it runs after [setActive]; the host short-circuits the identical remembered instance.
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

    // Pause the applet's JS while the app is backgrounded (parity with NappletHostActivity's onPause):
    // an "allow always" napplet can't act on the user's behalf when they aren't looking. (The tab layer
    // separately pauses it whenever it isn't the visible tab.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> controller.pause()
                    Lifecycle.Event.ON_START -> controller.resume()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = canGoBack) { controller.back() }

    if (showAccess) {
        AccessDialog(title, capLabels, websiteMode, useTor) { showAccess = false }
    }

    Scaffold(
        bottomBar = {
            AppBottomBar(Route.FavoriteNostrApp(coordinate), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        // Reserve the full content area; the warm surface + its top sheet are drawn over these bounds.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { EmbeddedTabHost.reportBounds(it.boundsInWindow()) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnavailableTab(
    coordinate: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.favorite_apps)) }) },
        bottomBar = {
            AppBottomBar(Route.FavoriteNostrApp(coordinate), nav, accountViewModel) { route -> nav.navBottomBar(route) }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.favorite_app_unavailable),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AccessDialog(
    title: String,
    capLabels: List<String>,
    websiteMode: Boolean,
    useTor: Boolean,
    onDismiss: () -> Unit,
) {
    val capsBody =
        if (capLabels.isEmpty()) {
            stringResource(R.string.favorite_app_access_static)
        } else {
            capLabels.joinToString("\n") { "•  $it" }
        }
    val networkBody =
        if (websiteMode) {
            "\n\n" + stringResource(if (useTor) R.string.favorite_app_network_tor else R.string.favorite_app_network_open)
        } else {
            ""
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (title.isBlank()) stringResource(R.string.favorite_app_access_title) else title) },
        text = { Text(capsBody + networkBody) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

private fun noticeResId(notice: String): Int? =
    when (notice) {
        NappletEmbedContract.NOTICE_PUBLISHED -> R.string.favorite_notice_published
        NappletEmbedContract.NOTICE_UPLOADED -> R.string.favorite_notice_uploaded
        NappletEmbedContract.NOTICE_PAID -> R.string.favorite_notice_paid
        else -> null
    }
