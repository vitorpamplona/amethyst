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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionManager
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.desktop_key_not_kept_description
import com.vitorpamplona.amethyst.commons.resources.desktop_key_not_kept_title
import com.vitorpamplona.amethyst.commons.resources.dismiss
import com.vitorpamplona.amethyst.commons.scheduledposts.ScheduledPostStatus
import com.vitorpamplona.amethyst.commons.ui.app.AppRoot
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * The desktop's answers to the shared app root. The window size comes from the default
 * ([AppRoot.rememberScreenLayoutSpec] reads the window), so a narrow window gets the bottom bar and a
 * wide one the rail or the drawer, as on a phone or a laptop.
 */
class DesktopAppRoot(
    private val modules: DesktopAppModules,
) : AppRoot {
    private val host by lazy { DesktopAccountViewModelHost(modules) }

    /** Hands the logged-in shell's navigator to the menu bar, which lives outside the shared app. */
    val navigator = DesktopNavigator()

    override fun createAccountViewModel(account: Account): AccountViewModel =
        AccountViewModel(
            account = account,
            settings = modules.uiState,
            torSettings = modules.torPrefs.value,
            dataSources = modules.sources,
            httpClientBuilder = modules.roleBasedHttpClientBuilder,
            nip05ClientBuilder = { modules.nip05Client },
            host = host,
        )

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
        DisposableEffect(accountViewModel.account) {
            val job = modules.notifications.watch(accountViewModel.account, modules.cache, focused)
            onDispose { job.cancel() }
        }

        // Each account publishes only its own scheduled posts, to its outbox relays.
        DisposableEffect(accountViewModel.account) {
            val account = accountViewModel.account
            modules.scheduledPostScheduler.start(
                client = modules.client,
                accountPubkey = account.signer.pubKey,
                resolveRelays = { account.outboxRelays.flow.value },
            )
            onDispose { modules.scheduledPostScheduler.stop() }
        }
    }

    @Composable
    override fun NavigationEffects(
        accountViewModel: AccountViewModel,
        nav: Nav,
        sessionManager: AccountSessionManager,
    ) {
        DisposableEffect(nav, accountViewModel) {
            navigator.nav = nav
            navigator.userPubKeyHex = accountViewModel.account.signer.pubKey
            onDispose {
                if (navigator.nav === nav) {
                    navigator.nav = null
                    navigator.userPubKeyHex = null
                }
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
