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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateController
import com.vitorpamplona.amethyst.commons.keystorage.KeyStorageController
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.navigation.uri.uriToRoute
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_key_not_kept_description
import com.vitorpamplona.amethyst.commons.resources.desktop_key_not_kept_title
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.keystorage.LocalKeyStorage
import com.vitorpamplona.amethyst.commons.ui.navigation.host.LocalNavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.NavDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.host.UnavailableScreen
import com.vitorpamplona.amethyst.commons.ui.navigation.host.jvmDestinations
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.navigation.windows.ScreenWindowSpec
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings.buildSettingsCatalog
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.platform.AppWindow
import com.vitorpamplona.amethyst.desktop.platform.IconResources
import com.vitorpamplona.amethyst.desktop.service.startup.StartupTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The desktop's answers to the shared app root. The window size comes from the default
 * ([AppRoot.rememberScreenLayoutSpec] reads the window), so a narrow window gets the bottom bar and a
 * wide one the rail or the drawer, as on a phone or a laptop.
 */
class DesktopAppRoot(
    private val modules: DesktopAppModules,
) : AppRoot {
    override val privacyLockSettings: PrivacyLockSettings get() = modules.privacyLockSettings

    override val blurWalletWhenUnfocused: Boolean = true

    override val keyStorage: KeyStorageController get() = modules.keyStorageController

    override val appUpdates: StateFlow<AppUpdateController?> get() = modules.appUpdates

    private val host by lazy { DesktopAccountViewModelHost(modules) }

    /** Hands the logged-in shell's navigator to the menu bar, which lives outside the shared app. */
    val navigator = DesktopNavigator(DesktopWindowManager { File(modules.filesDir, "screen-windows.properties") })

    override fun createAccountViewModel(account: Account): AccountViewModel {
        // The logged-in shell starts composing here; "logged-in-effects" marks its effects running.
        StartupTimeline.mark("account-viewmodel")
        return AccountViewModel(
            account = account,
            settings = modules.uiState,
            torSettings = modules.torPrefs.value,
            dataSources = modules.sources,
            httpClientBuilder = modules.roleBasedHttpClientBuilder,
            nip05ClientBuilder = { modules.nip05Client },
            host = host,
        ).also { host.notices.target = it.toastManager }
    }

    /** Keeps the relay pool and the HTTP clients alive while the window is open. */
    @Composable
    override fun AppEffects() {
        modules.relayProxyClientConnector.relayServices.collectAsState()
        modules.okHttpClients.defaultHttpClient.collectAsState()
        modules.okHttpClients.defaultHttpClientWithoutProxy.collectAsState()

        KeyNotKeptDialog(modules)

        // Keeps the OS timer registered only while posts wait to go out, so they publish even
        // when the app is closed. A no-op from `gradle run`, where there is no app binary.
        LaunchedEffect(Unit) {
            var lastHadPending: Boolean? = null
            modules.scheduledPostStore.flow.collect { posts ->
                val hasPending = posts.any { it.status == ScheduledPostStatus.PENDING || it.status == ScheduledPostStatus.PUBLISHING }
                if (hasPending != lastHadPending) {
                    lastHadPending = hasPending
                    withContext(Dispatchers.IO) {
                        if (hasPending) modules.osScheduler.ensureRegistered() else modules.osScheduler.unregister()
                    }
                }
            }
        }
    }

    /** OS notifications for the logged-in account, held back while the window has focus. */
    @Composable
    override fun LoggedInEffects(accountViewModel: AccountViewModel) {
        val windowInfo = LocalWindowInfo.current
        val focused = remember { MutableStateFlow(windowInfo.isWindowFocused) }
        LaunchedEffect(windowInfo) {
            snapshotFlow { windowInfo.isWindowFocused }.collect { focused.value = it }
        }
        if (StartupTimeline.enabled) {
            LaunchedEffect(accountViewModel) {
                StartupTimeline.mark("logged-in-effects")
                StartupTimeline.watchFeed("home", accountViewModel.feedStates.homeNewThreads, this)
                StartupTimeline.watchFeed("everything", accountViewModel.feedStates.homeEverything, this)
            }
        }
        DisposableEffect(accountViewModel.account) {
            val job = modules.notifications.watch(accountViewModel.account, modules.cache, focused)
            onDispose { job.cancel() }
        }

        // Each account publishes only its own scheduled posts, to its outbox relays.
        DisposableEffect(accountViewModel.account) {
            val account = accountViewModel.account
            val loop =
                modules.scheduledPostScheduler.start(
                    client = modules.client,
                    accountPubkey = account.signer.pubKey,
                    resolveRelays = { account.outboxRelays.flow.value },
                )
            onDispose { modules.scheduledPostScheduler.stop(loop) }
        }
    }

    /**
     * A screen window: placed and sized by [DesktopWindowManager]. It carries the app's shortcuts
     * and, on macOS, its menu bar, and shows the toasts while it has the focus.
     */
    @Composable
    override fun ScreenWindow(
        window: ScreenWindowSpec,
        content: @Composable () -> Unit,
    ) {
        val windows = navigator.windows
        val state = remember(window.id) { windows.initialState(window) }
        val quit = { modules.quitRequests.tryEmit(Unit) }
        AppWindow(
            onCloseRequest = window.onClose,
            state = state,
            title = window.title,
            icon = IconResources.adaptedBitmapPainter,
            onPreviewKeyEvent = { navigator.handleShortcut(it, onQuit = { quit() }) },
        ) {
            val focused = LocalWindowInfo.current.isWindowFocused
            windows.ScreenWindowEffects(window, this.window, state, focused)
            frameScope?.DesktopMenuBar(navigator, onQuit = { quit() }, installAppMenu = false)

            val snackbars = (LocalAppPlatform.current as? DesktopAppPlatform)?.snackbarHostState
            Box(Modifier.fillMaxSize()) {
                content()
                // Toasts show in the window the user is in.
                if (focused && snackbars != null) SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    override fun registerDestinations(
        destinations: NavDestinations,
        accountViewModel: AccountViewModel,
    ) {
        destinations.jvmDestinations(accountViewModel)
        // Screens only Android has (Health Connect, the QR camera, share-as-image...) still have
        // ways in here: synced bar items, links, shared menus. Show a notice, not a crash.
        destinations.unavailable { UnavailableScreen(nav) }
    }

    @Composable
    override fun NavigationEffects(
        accountViewModel: AccountViewModel,
        nav: Nav,
        sessionManager: AccountSessionManager,
    ) {
        // The menu bar mirrors the drawer and the Settings screen, so it takes what they read here.
        val destinations = LocalNavDestinations.current
        val uriHandler = LocalUriHandler.current
        val platform = LocalAppPlatform.current
        val hasKeyStorage = LocalKeyStorage.current != null
        // Built when first read: only the macOS menu bar reads it, and building it reads every
        // Settings label, which cost the first frame ~9% of its UI-thread time elsewhere for nothing.
        val settingsCatalog =
            remember(accountViewModel, nav, destinations, uriHandler, platform, hasKeyStorage) {
                lazy {
                    buildSettingsCatalog(
                        nav = nav,
                        uriHandler = uriHandler,
                        hasPrivateKey = accountViewModel.account.settings.keyPair.privKey != null,
                        legal = platform.legalSettingsCategory(uriHandler),
                        // Its confirmation dialog belongs to the Settings screen; a menu has none.
                        onResetMarmot = null,
                        canOpen = { route -> destinations?.has(route) ?: true },
                        hasKeyStorage = hasKeyStorage,
                    )
                }
            }

        // A section asked for from a screen window, or a layout switch, brings the main window back.
        LaunchedEffect(nav) {
            snapshotFlow { nav.stacks.model.windowFocus }.collect { focus ->
                val model = nav.stacks.model
                if (focus != null && model.entries.any { it.id == focus.id && model.isMainScreen(it) }) navigator.windows.showMainWindow()
            }
        }
        LaunchedEffect(nav.stacks.arrangement) { navigator.windows.showMainWindow(raise = false) }

        DisposableEffect(nav, accountViewModel, destinations, settingsCatalog) {
            navigator.nav = nav
            navigator.userPubKeyHex = accountViewModel.account.signer.pubKey
            navigator.accountViewModel = accountViewModel
            navigator.destinations = destinations
            navigator.settingsCatalog = settingsCatalog
            onDispose {
                if (navigator.nav === nav) {
                    navigator.nav = null
                    navigator.userPubKeyHex = null
                    navigator.accountViewModel = null
                    navigator.destinations = null
                    navigator.settingsCatalog = lazyOf(emptyList())
                }
            }
        }

        // OS-opened nostr:/amethyst: links, queued by DesktopDeepLinks (main()'s argv, the macOS
        // open-URI event, or a second launch forwarded over the instance bus), routed through the
        // shared parser once a logged-in shell exists.
        LaunchedEffect(nav, accountViewModel) {
            DesktopDeepLinks.pending.filterNotNull().collect { uri ->
                DesktopDeepLinks.pending.value = null
                uriToRoute(uri, accountViewModel.account)?.let { route -> nav.nav(route) }
            }
        }
    }
}

/** Tells the user, once, that the OS keyring refused their private key: they will log in again next time. */
@Composable
private fun KeyNotKeptDialog(modules: DesktopAppModules) {
    val failure by modules.sessionStore.keyVaultFailure.collectAsState()
    if (failure == null) return

    AlertDialog(
        onDismissRequest = modules.sessionStore::clearKeyVaultFailure,
        title = { Text(stringRes(Res.string.desktop_key_not_kept_title)) },
        text = { Text(stringRes(Res.string.desktop_key_not_kept_description)) },
        confirmButton = {
            TextButton(onClick = modules.sessionStore::clearKeyVaultFailure) {
                Text(stringRes(Res.string.dismiss))
            }
        },
    )
}
