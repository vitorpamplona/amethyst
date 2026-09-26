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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserSitePermission
import com.vitorpamplona.amethyst.commons.browser.OmniboxInput
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.favoriteIds
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_unsupported
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.favorites.FavoriteAppLauncher
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.favorites.WebShortcuts
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.napplet.WebSitePermissionRegistry
import com.vitorpamplona.amethyst.napplethost.BrowserWebTools
import com.vitorpamplona.amethyst.ui.navigation.bottombars.AppBottomBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabChrome
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabFactory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * A **Web app** (a Nostr web client, reached by [url]) rendered as an **in-app tab** — for *any* URL,
 * favorited or not; favoriting is just the star toggle in the chrome. The embedded `:napplet` browser surface is drawn
 * by the persistent [EmbeddedTabHost]/[com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabLayer]
 * layer, which keeps the session warm across tab swaps. This screen owns only the chrome — it publishes
 * the controls as an [EmbeddedTabChrome] and the layer draws a top pull-down sheet over the (z-below)
 * surface. No title bar or editable address bar.
 *
 * Only bottom-row favorites stay warm; if this URL isn't a bottom-bar favorite, its session is evicted
 * (restarted) when the screen leaves. Requires API 30+ for the cross-process surface.
 */
@Composable
fun WebAppScreen(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        EmbeddedWebAppTab(url, accountViewModel, nav)
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringRes(Res.string.browser_unsupported),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedWebAppTab(
    url: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    // Matches FavoriteApp.WebApp.id, so warm-keep membership lines up with the bottom-bar favorites.
    val id = "url:$url"

    var currentUrl by remember { mutableStateOf(url) }
    // The page's own <title>; null until the current document reports one (the sheet shows the host).
    var pageTitle by remember { mutableStateOf<String?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var desktopSite by remember { mutableStateOf(false) }
    var textZoom by remember { mutableIntStateOf(BrowserChrome.DEFAULT_TEXT_ZOOM) }
    var showPageInfo by remember { mutableStateOf(false) }

    val proxyAvailable = remember { Amethyst.instance.torManager.activePortOrNull.value != null }
    // Start from this site's remembered Tor choice (some sites' servers reject Tor exits, so the user
    // can opt one out and it must stick). Only meaningful when Tor is actually available.
    var torOn by remember { mutableStateOf(proxyAvailable && WebAppNetworkRegistry.useTor(url)) }

    val apps by FavoriteAppsRegistry.favorites.collectAsStateWithLifecycle()
    val isFavorite = remember(apps, currentUrl) { apps.any { it is FavoriteApp.WebApp && it.url == currentUrl } }

    val backgroundColor = MaterialTheme.colorScheme.background.toArgb()

    // Keyed on the theme epoch too: when the app theme flips, the warm session is torn down and this
    // re-acquires a freshly-themed one (the embed WebView's theme is fixed at construction).
    val controller =
        remember(id, EmbeddedTabHost.rebuildEpoch) {
            EmbeddedTabFactory.acquireWebApp(context, url, backgroundColor)
        }
    val isLoading by controller.isLoading

    // Keep the URL/back callback fresh (cheap, needs the latest closure).
    SideEffect {
        controller.onUrlChanged = { newUrl, title, back, forward ->
            if (newUrl != "about:blank") {
                currentUrl = newUrl
                pageTitle = title
            }
            canGoBack = back
            canGoForward = forward
        }
    }

    fun toggleFavorite() {
        val favId = "url:$currentUrl"
        if (FavoriteAppsRegistry.isFavorite(favId)) {
            FavoriteAppsRegistry.remove(favId)
        } else {
            FavoriteAppsRegistry.add(FavoriteApp.WebApp(currentUrl, pageTitle ?: hostLabel(currentUrl), System.currentTimeMillis()))
        }
    }

    // NIP-07 grants for a plain web client are keyed per visited origin as `browser:<origin>` (see
    // NappletBrokerService.BROWSER_IDENTITY_AUTHOR); site settings jump straight to that detail screen.
    fun openSiteSettings() {
        browserOrigin(currentUrl)?.let { origin -> nav.nav(Route.ConnectedAppDetail("browser:$origin")) }
    }

    // Rebuilt only when a displayed value changes, so the tab layer isn't recomposed every frame.
    val chrome =
        remember(currentUrl, pageTitle, canGoBack, canGoForward, isLoading, torOn, proxyAvailable, isFavorite, desktopSite, textZoom, controller) {
            EmbeddedTabChrome(
                title = pageTitle ?: hostLabel(currentUrl),
                state =
                    BrowserChrome.State(
                        surface = BrowserChrome.Surface.WEB,
                        presentation = BrowserChrome.Presentation.EMBEDDED,
                        url = currentUrl,
                        startUrl = url,
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isLoading = isLoading,
                        torOn = if (proxyAvailable) torOn else null,
                        hasSiteSettings = browserOrigin(currentUrl) != null,
                    ),
                isFavorite = isFavorite,
                desktopSite = desktopSite,
                textZoom = textZoom,
                onAction = { action ->
                    when (action) {
                        BrowserChrome.Action.BACK -> controller.back()
                        BrowserChrome.Action.FORWARD -> controller.forward()
                        BrowserChrome.Action.RELOAD -> controller.reload()
                        BrowserChrome.Action.STOP -> controller.stop()
                        BrowserChrome.Action.FAVORITE -> toggleFavorite()
                        BrowserChrome.Action.SHARE -> BrowserWebTools.share(context, pageTitle, null, currentUrl)
                        BrowserChrome.Action.BACK_TO_APP -> controller.backToScope(url)
                        BrowserChrome.Action.COPY_LINK -> BrowserWebTools.copyToClipboard(context, currentUrl)
                        BrowserChrome.Action.DESKTOP_SITE -> {
                            desktopSite = !desktopSite
                            controller.setDesktopSite(desktopSite)
                        }
                        BrowserChrome.Action.ADD_TO_HOME_SCREEN -> WebShortcuts.requestPin(context, currentUrl, pageTitle ?: hostLabel(currentUrl))
                        BrowserChrome.Action.OPEN_IN_BROWSER_APP -> BrowserWebTools.openInOtherBrowser(context, currentUrl)
                        // The page the user is looking at, not the one the tab was pinned with.
                        BrowserChrome.Action.OPEN_FULL_SCREEN -> FavoriteAppLauncher.launchUrl(context, currentUrl)
                        BrowserChrome.Action.TOR -> {
                            torOn = !torOn
                            controller.setTor(torOn)
                            WebAppNetworkRegistry.set(currentUrl, torOn)
                        }
                        BrowserChrome.Action.SITE_SETTINGS -> openSiteSettings()
                        else -> Unit
                    }
                },
                onNavigate = { text ->
                    val resolved = OmniboxInput.resolve(text)
                    if (resolved != null) {
                        // .onion only resolves over Tor.
                        if (resolved.forceTor && proxyAvailable && !torOn) {
                            torOn = true
                            controller.setTor(true)
                        }
                        controller.navigate(resolved.url)
                    }
                },
                onTextZoom = { percent ->
                    textZoom = percent
                    controller.setTextZoom(percent)
                },
                onOriginTap = {
                    controller.requestPageInfo()
                    showPageInfo = true
                },
            )
        }
    // Publish the top-sheet controls to the tab layer (which draws them over the z-below surface). In a
    // SideEffect so it runs after [setActive]; the host short-circuits the identical remembered instance.
    SideEffect { EmbeddedTabHost.setActiveChrome(id, chrome) }

    val bottomBarFlow = accountViewModel.account.settings.syncedSettings.navigation.bottomBarItems
    DisposableEffect(id) {
        val token = EmbeddedTabHost.setActive(id)
        onDispose {
            EmbeddedTabHost.clearActiveIfOwner(token)
            EmbeddedTabHost.clearActiveChrome(id)
            // Only bottom-row apps stay warm; anything else restarts when it leaves.
            if (id !in bottomBarFlow.value.favoriteIds()) EmbeddedTabHost.evict(id)
        }
    }

    val isFullscreen by controller.isFullscreen
    BackHandler(enabled = canGoBack && !isFullscreen) { controller.back() }
    // A fullscreen video inside the tab: back leaves fullscreen first, as in Chrome.
    BackHandler(enabled = isFullscreen) { controller.exitFullscreen() }

    EmbeddedPageUi(controller, currentUrl, showPageInfo, onPageInfoDismiss = { showPageInfo = false }, onSiteSettings = ::openSiteSettings)

    Scaffold(
        bottomBar = {
            AppBottomBar(Route.WebApp(url), nav, accountViewModel) { route -> nav.navBottomBar(route) }
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

/**
 * Everything an embedded page asks the user for, drawn by the main process because the provider has no
 * window: JS dialogs, camera / microphone / location prompts (remembered per origin in
 * [WebSitePermissionRegistry], then Android's own runtime permission), and page info.
 */
@RequiresApi(Build.VERSION_CODES.R)
@Composable
private fun EmbeddedPageUi(
    controller: EmbeddedWebAppController,
    currentUrl: String,
    showPageInfo: Boolean,
    onPageInfoDismiss: () -> Unit,
    onSiteSettings: () -> Unit,
) {
    val context = LocalContext.current

    val dialog by controller.pendingDialog
    dialog?.let { d ->
        EmbeddedJsDialogView(d) { confirmed, text, block -> controller.answerDialog(d.id, confirmed, text, block) }
    }

    // Android's runtime permission, asked only for what the user allowed the site to use.
    var runtimeRequest by remember { mutableStateOf<Pair<Long, Set<BrowserSitePermission>>?>(null) }
    val runtimeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val (requestId, allowed) = runtimeRequest ?: return@rememberLauncherForActivityResult
            runtimeRequest = null
            val granted = allowed.filter { ContextCompat.checkSelfPermission(context, androidPermissionFor(it)) == PackageManager.PERMISSION_GRANTED }.toSet()
            if (granted.size < allowed.size) Toast.makeText(context, CommonsR.string.browser_permission_system_denied, Toast.LENGTH_LONG).show()
            controller.answerPermission(requestId, granted)
        }

    fun grant(
        requestId: Long,
        allowed: Set<BrowserSitePermission>,
    ) {
        val missing = allowed.map(::androidPermissionFor).filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty() || runtimeRequest != null) {
            controller.answerPermission(requestId, if (missing.isEmpty()) allowed else emptySet())
        } else {
            runtimeRequest = requestId to allowed
            runtimeLauncher.launch(missing.toTypedArray())
        }
    }

    val permissionRequest by controller.pendingPermission
    permissionRequest?.let { request ->
        val decisions = remember(request.id) { request.permissions.associateWith { WebSitePermissionRegistry.decision(request.origin, it) } }
        val allowed = decisions.filterValues { it == BrowserSitePermission.Decision.ALLOW }.keys
        val ask = decisions.filterValues { it == BrowserSitePermission.Decision.ASK }.keys
        if (ask.isEmpty()) {
            LaunchedEffect(request.id) { grant(request.id, allowed) }
        } else {
            EmbeddedPermissionPrompt(request.origin, ask) { allow ->
                // null = dismissed without an answer: deny this once, remember nothing.
                if (allow != null) {
                    val decision = if (allow) BrowserSitePermission.Decision.ALLOW else BrowserSitePermission.Decision.BLOCK
                    ask.forEach { WebSitePermissionRegistry.set(request.origin, it, decision) }
                }
                grant(request.id, if (allow == true) allowed + ask else allowed)
            }
        }
    }

    if (showPageInfo) {
        val info by controller.pageInfo
        EmbeddedPageInfoDialog(
            host = hostLabel(currentUrl),
            info = info,
            onPermissions = if (browserOrigin(currentUrl) != null) onSiteSettings else null,
            onClearData = { controller.clearSiteData() },
            onDismiss = onPageInfoDismiss,
        )
    }
}

private fun androidPermissionFor(permission: BrowserSitePermission): String =
    when (permission) {
        BrowserSitePermission.CAMERA -> Manifest.permission.CAMERA
        BrowserSitePermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        BrowserSitePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
    }

/** The host of [url] for the tab title, falling back to the raw string. */
internal fun hostLabel(url: String): String = runCatching { url.toUri().host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url

/**
 * The `scheme://host[:port]` origin of [url] — the exact form the sandbox reports for NIP-07 consent, so
 * it matches the `browser:<origin>` permission-ledger key. Null when [url] has no usable scheme/host.
 */
internal fun browserOrigin(url: String): String? {
    val uri = runCatching { url.toUri() }.getOrNull() ?: return null
    val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    return "$scheme://$host" + if (uri.port > 0) ":${uri.port}" else ""
}
