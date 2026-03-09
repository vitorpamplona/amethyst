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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.login_subtitle_desktop
import com.vitorpamplona.amethyst.commons.resources.login_title
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.network.RelayStatus
import com.vitorpamplona.amethyst.desktop.ui.auth.LoginCard
import com.vitorpamplona.amethyst.desktop.ui.auth.NewKeyWarningCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun LoginScreen(
    accountManager: AccountManager,
    onLoginSuccess: () -> Unit,
) {
    var showNewKeyDialog by remember { mutableStateOf(false) }
    var generatedAccount by remember { mutableStateOf<AccountState.LoggedIn?>(null) }
    val scope = rememberCoroutineScope()
    val loginProgress by accountManager.loginProgress.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(Res.string.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(Res.string.login_subtitle_desktop),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(48.dp))

        LoginCard(
            onLogin = { keyInput ->
                accountManager.loginWithKey(keyInput).map {
                    scope.launch {
                        withContext(Dispatchers.IO) { accountManager.saveCurrentAccount() }
                        onLoginSuccess()
                    }
                }
            },
            onGenerateNew = {
                generatedAccount = accountManager.generateNewAccount()
                showNewKeyDialog = true
            },
            onLoginBunker = { bunkerUri ->
                accountManager.loginWithBunker(bunkerUri).map {
                    onLoginSuccess()
                }
            },
            onLoginNostrConnect = { onUriGenerated ->
                accountManager.loginWithNostrConnect(onUriGenerated).map {
                    onLoginSuccess()
                }
            },
            loginProgress = loginProgress,
        )

        val account = generatedAccount
        if (showNewKeyDialog && account != null) {
            Spacer(Modifier.height(24.dp))
            NewKeyWarningCard(
                npub = account.npub,
                nsec = account.nsec,
                onContinue = {
                    showNewKeyDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { accountManager.saveCurrentAccount() }
                        onLoginSuccess()
                    }
                },
            )
        }
    }
}

@Composable
fun ConnectingRelaysScreen(
    subtitle: String = "Restoring session",
    relayStatuses: Map<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl, RelayStatus> = emptyMap(),
) {
    val total = relayStatuses.size
    val connected = relayStatuses.values.count { it.connected }
    val failed = relayStatuses.values.count { it.error != null }
    val progress = if (total > 0) connected.toFloat() / total else 0f

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Amethyst",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(24.dp))

        CircularProgressIndicator(modifier = Modifier.size(32.dp))

        Spacer(Modifier.height(16.dp))

        Text(
            if (total > 0) "Connecting to relays ($connected/$total)" else "Connecting to relays...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )

        // Progress bar
        if (total > 0) {
            Spacer(Modifier.height(16.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(300),
                label = "relay-progress",
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            // Per-relay status rows
            Column(
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                relayStatuses.values.forEach { status ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        when {
                            status.connected -> {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(14.dp),
                                )
                            }

                            status.error != null -> {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp),
                                )
                            }

                            else -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 1.5.dp,
                                )
                            }
                        }
                        Text(
                            status.url.url
                                .removePrefix("wss://")
                                .removeSuffix("/"),
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (status.error != null) {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        if (status.connected && status.pingMs != null) {
                            Text(
                                "${status.pingMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
