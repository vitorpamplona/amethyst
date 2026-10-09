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
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.AppLauncher
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.Toaster
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.ui.navigation.bottombars.AppBottomBar as SharedAppBottomBar

/**
 * The desktop halves of the shared app's platform hooks. What is not overridden here keeps
 * [AppPlatform]'s default, which hides the feature (QR scanning, location pickers, calls).
 */
class DesktopAppPlatform(
    override val appVersionName: String,
    override val isDebugBuild: Boolean,
    private val notifications: DesktopNotifications,
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

    // DesktopAppModules reports every connection as unmetered.
    override val detectsMeteredConnections: Boolean get() = false

    // The shared bar, imported under another name: inside this override the bare name is the override.
    @Composable
    override fun AppBottomBar(
        selectedRoute: Route?,
        nav: INav,
        accountViewModel: AccountViewModel,
        onClick: (Route) -> Unit,
    ) = SharedAppBottomBar(selectedRoute, nav, accountViewModel, onClick)

    @Composable
    override fun rememberAppLauncher(): AppLauncher = DesktopAppLauncher

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
