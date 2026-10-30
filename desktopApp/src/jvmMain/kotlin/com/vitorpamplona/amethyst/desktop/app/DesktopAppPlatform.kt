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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcApp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_desktop_open_failed
import com.vitorpamplona.amethyst.commons.resources.nsite_open_failed
import com.vitorpamplona.amethyst.commons.resources.webxdc_open_failed
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.InlineNappletStatus
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.rememberDecryptedConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.napplet.DesktopNappletHost
import com.vitorpamplona.amethyst.desktop.napplet.InlineNappletPane
import com.vitorpamplona.amethyst.desktop.spellcheck.DesktopSpellCheck
import com.vitorpamplona.amethyst.desktop.ui.map.DesktopGeocacheMapTab
import com.vitorpamplona.amethyst.desktop.ui.qrcode.DesktopQrScannerDialog
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import com.vitorpamplona.quartz.nip5dNapplets.NappletManifest
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppBottomBar as SharedAppBottomBar

/**
 * The desktop halves of the shared app's platform hooks. What is not overridden here keeps
 * [AppPlatform]'s default, which hides the feature (location pickers, calls).
 */
class DesktopAppPlatform(
    override val appVersionName: String,
    override val isDebugBuild: Boolean,
    private val notifications: DesktopNotifications,
    // Decrypted community pictures: an owner-only folder in the app's data directory.
    private val concordImageDir: File,
    private val desktopSpellCheck: DesktopSpellCheck? = null,
    // Opens napplets (NIP-5D) and nSites (NIP-5A) in their own sandboxed window; null where Chromium can't run.
    private val nappletHost: DesktopNappletHost? = null,
) : AppPlatform {
    override val appFlavor: String get() = "desktop"

    /** Where the shared app's toasts show up: a snackbar at the bottom of the window. */
    val snackbarHostState = SnackbarHostState()

    @Composable
    override fun rememberToaster(): Toaster {
        val scope = rememberCoroutineScope()
        return remember(scope) {
            Toaster { message, long ->
                scope.launch {
                    snackbarHostState.showSnackbar(message, duration = if (long) SnackbarDuration.Long else SnackbarDuration.Short)
                }
            }
        }
    }

    // Desktop has no signer app to hand keys to; a NIP-46 remote signer plays that role.
    override val supportsRemoteSignerLogin: Boolean get() = true

    // Only the addresses the login screen makes: never a scheme a page or note could slip in.
    override fun openNostrConnect(
        uri: String,
        appId: String?,
    ): Boolean = uri.startsWith("nostrconnect://") && hasNostrConnectHandler() && DesktopBrowser.open(uri)

    // DesktopAppModules reports every connection as unmetered.
    override val detectsMeteredConnections: Boolean get() = false

    // The engine loads when a composer first asks for it (SpellCheckService.activate).
    override val spellCheck: SpellCheckService? get() = desktopSpellCheck?.service

    // The shared bar, imported under another name: inside this override the bare name is the override.
    @Composable
    override fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) = SharedAppBottomBar(selectedRoute, nav, accountViewModel, onClick)

    @Composable
    override fun rememberConcordImageModel(
        pointer: ImagePointer?,
        accountViewModel: AccountViewModel,
    ): String? = rememberDecryptedConcordImageModel(pointer, accountViewModel, concordImageDir)

    // No camera scanner: a picture of the code from a file or the clipboard.
    @Composable
    override fun QrCodeScannerDialog(
        onDismiss: () -> Unit,
        onScan: (String) -> ScanOutcome,
    ) = DesktopQrScannerDialog(onDismiss, onScan)

    @Composable
    override fun rememberAppLauncher(): AppLauncher {
        val toaster = rememberToaster()
        val nappletFailed = stringRes(Res.string.napplet_desktop_open_failed)
        val siteFailed = stringRes(Res.string.nsite_open_failed)
        val webxdcFailed = stringRes(Res.string.webxdc_open_failed)
        return remember(toaster, nappletFailed, siteFailed, webxdcFailed) { DesktopAppLauncher(nappletHost, toaster, nappletFailed, siteFailed, webxdcFailed) }
    }

    @Composable
    override fun PrefetchNappletFiles(
        author: String,
        identifier: String,
        paths: List<PathTag>,
        servers: List<String>,
    ) {
        val host = nappletHost ?: return
        LaunchedEffect(host, author, identifier, paths, servers) { runCatchingCancellable { host.prefetch(author, identifier, paths, servers) } }
    }

    // Off-screen Chromium, drawn by the note: wherever napplets can run at all.
    override val runsNappletsInline: Boolean get() = nappletHost != null

    @Composable
    override fun InlineNappletSurface(
        manifest: NappletManifest,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        val host = nappletHost ?: return
        InlineNappletPane(host, manifest, instanceId, onStatus, modifier)
    }

    @Composable
    override fun InlineWebxdcSurface(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
        instanceId: String,
        onStatus: (InlineNappletStatus) -> Unit,
        modifier: Modifier,
    ) {
        val host = nappletHost ?: return
        InlineNappletPane(host, app.stageKey(poster, postId), instanceId, onStatus, modifier) {
            host.openInlineWebxdc(app, postId, poster, instanceId)
        }
    }

    override val hasGeocacheMap: Boolean get() = true

    @Composable
    override fun GeocacheMapTab(
        feedContentState: FeedContentState,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = DesktopGeocacheMapTab(feedContentState, accountViewModel, nav)

    @Composable
    override fun NotificationDeliverySettings(accountViewModel: AccountViewModel) = DesktopNotificationDeliverySettings(notifications)
}

/**
 * Web apps and links open in the system browser; napplets and nSites open in [DesktopNappletHost]'s
 * sandboxed windows.
 */
private class DesktopAppLauncher(
    private val napplets: DesktopNappletHost?,
    private val toaster: Toaster,
    private val nappletFailed: String,
    private val siteFailed: String,
    private val webxdcFailed: String,
) : AppLauncher {
    override fun launch(
        app: FavoriteApp,
        stillLoading: String,
    ) {
        when (app) {
            is FavoriteApp.WebApp -> DesktopBrowser.open(app.url)
            is FavoriteApp.NostrApp -> {
                when (val event = LocalCache.getAddressableNoteIfExists(app.coordinate)?.event) {
                    null -> toaster.show(stillLoading, false)
                    is NappletManifest -> openNapplet(event)
                    is RootSiteEvent, is NamedSiteEvent -> openSite(event)
                    else -> Unit
                }
            }
        }
    }

    override fun launchUrl(
        url: String,
        preferTor: Boolean,
    ) {
        DesktopBrowser.open(url)
    }

    // Without Chromium (it couldn't be set up here) the cards offer no Open.
    override val opensNapplets: Boolean get() = napplets != null

    override fun openNapplet(manifest: NappletManifest) {
        val host = napplets ?: return
        host.open(manifest).onFailure {
            Log.w(TAG, "Refusing to open napplet ${(manifest as? Event)?.id}", it)
            toaster.show(nappletFailed, false)
        }
    }

    override fun openSite(site: Event) {
        val host = napplets ?: return
        host.openSite(site).onFailure {
            Log.w(TAG, "Refusing to open nSite ${site.id}", it)
            toaster.show(siteFailed, false)
        }
    }

    override fun openWebxdc(
        app: WebxdcApp,
        postId: HexKey,
        poster: HexKey,
    ) {
        val host = napplets ?: return
        host.openWebxdc(app, postId, poster).onFailure {
            Log.w(TAG, "Refusing to open webxdc app ${app.url}", it)
            toaster.show(webxdcFailed, false)
        }
    }

    private companion object {
        const val TAG = "DesktopAppLauncher"
    }
}

/**
 * False only where the desktop says for certain that no app takes `nostrconnect://`: xdg-open
 * starts (and so reports success) whether or not a handler exists, which left "Open signer"
 * doing nothing on a Linux desktop without one. Anything else, or a failed query, lets the
 * open go ahead as before.
 */
private fun hasNostrConnectHandler(): Boolean {
    if (!System
            .getProperty("os.name")
            .orEmpty()
            .lowercase()
            .contains("linux")
    ) {
        return true
    }
    return try {
        val query =
            ProcessBuilder("xdg-mime", "query", "default", "x-scheme-handler/nostrconnect")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        if (!query.waitFor(2, TimeUnit.SECONDS)) {
            query.destroy()
            return true
        }
        query.inputStream
            .bufferedReader()
            .readText()
            .isNotBlank()
    } catch (e: Exception) {
        Log.w("DesktopAppPlatform", "Could not ask xdg-mime for a nostrconnect handler", e)
        true
    }
}
