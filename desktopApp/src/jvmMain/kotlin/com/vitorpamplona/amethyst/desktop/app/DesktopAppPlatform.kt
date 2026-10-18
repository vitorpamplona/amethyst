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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.feeds.FeedContentState
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.concord.rememberDecryptedConcordImageModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.spellcheck.DesktopSpellCheck
import com.vitorpamplona.amethyst.desktop.ui.map.DesktopGeocacheMapTab
import com.vitorpamplona.amethyst.desktop.ui.qrcode.DesktopQrScannerDialog
import com.vitorpamplona.quartz.concord.cord02Community.ImagePointer
import com.vitorpamplona.quartz.utils.Log
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
    override fun rememberAppLauncher(): AppLauncher = DesktopAppLauncher

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
 * Web apps and links open in the system browser. Nostr apps (nsites, napplets) need the sandboxed
 * web view the desktop does not host yet.
 */
private object DesktopAppLauncher : AppLauncher {
    override fun launch(
        app: FavoriteApp,
        stillLoading: String,
    ) {
        when (app) {
            is FavoriteApp.WebApp -> DesktopBrowser.open(app.url)
            is FavoriteApp.NostrApp -> Unit
        }
    }

    override fun launchUrl(
        url: String,
        preferTor: Boolean,
    ) {
        DesktopBrowser.open(url)
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
