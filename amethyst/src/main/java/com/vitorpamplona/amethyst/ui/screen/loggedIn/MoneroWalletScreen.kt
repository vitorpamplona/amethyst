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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.AccountMoneroManager
import com.vitorpamplona.amethyst.model.preferences.MoneroSettings
import com.vitorpamplona.amethyst.service.MoneroDataSource
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger

fun privateKeyToSpendKey(privateKey: ByteArray): String {
    val keyHex = privateKey.joinToString("") { "%02x".format(it) }
    val key = BigInteger(keyHex, 16)
    val l = BigInteger("7237005577332262213973186563042994240857116359379907606001950938285454250989")
    val reducedKey = key.mod(l)
    val spendKeyHex = reducedKey.toString(16)
    return "0".repeat(64 - spendKeyHex.length) + spendKeyHex
}

@Composable
fun MoneroWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var walletStarted by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var daemon by remember { mutableStateOf("node.xmr.rocks:18089") }

    val settings by AccountMoneroManager.moneroSettings.collectAsState()
    val hasPrivKey = accountViewModel.account.settings.keyPair.privKey != null

    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Monero Wallet",
                style = MaterialTheme.typography.headlineMedium,
            )

            if (!hasPrivKey) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Monero wallet requires a Nostr private key. Login with a private key to use the Monero wallet.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (!walletStarted) {
                OutlinedTextField(
                    value = daemon,
                    onValueChange = { daemon = it },
                    label = { Text("Monero Daemon") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Button(
                    onClick = {
                        connecting = true
                        error = null
                        val privKey = accountViewModel.account.settings.keyPair.privKey!!
                        val spendKey = privateKeyToSpendKey(privKey)
                        val moneroSettings = MoneroSettings(daemon = daemon)

                        AccountMoneroManager.startMonero(
                            context = context,
                            spendKey = spendKey,
                            walletName =
                                accountViewModel.account.signer.pubKey
                                    .take(8),
                            password = "",
                            settings = moneroSettings,
                        )

                        scope.launch(Dispatchers.IO) {
                            try {
                                // Give service time to bind
                                kotlinx.coroutines.delay(1000)
                                AccountMoneroManager.startMoneroWallet(
                                    walletName =
                                        accountViewModel.account.signer.pubKey
                                            .take(8),
                                    password = "",
                                    spendKey = spendKey,
                                    settings = moneroSettings,
                                )
                                walletStarted = true
                                connecting = false
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to connect"
                                connecting = false
                            }
                        }
                    },
                    enabled = !connecting && daemon.isNotBlank(),
                ) {
                    Text(if (connecting) "Connecting..." else "Connect Wallet")
                }
            } else {
                WalletInfoCard()
            }
        }
    }
}

@Composable
fun WalletInfoCard() {
    val balance by MoneroDataSource.balance().collectAsState(initial = 0L)
    val lockedBalance by MoneroDataSource.lockedBalance().collectAsState(initial = 0L)
    val walletHeight by MoneroDataSource.walletHeight().collectAsState(initial = 0L)
    val daemonHeight by MoneroDataSource.daemonHeight().collectAsState(initial = 0L)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "%.12f XMR".format(balance / 1_000_000_000_000.0),
                style = MaterialTheme.typography.headlineSmall,
            )

            if (lockedBalance > 0) {
                Text(
                    text = "Locked: %.12f XMR".format(lockedBalance / 1_000_000_000_000.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (daemonHeight > 0) {
                val synced = walletHeight >= daemonHeight
                Text(
                    text =
                        if (synced) {
                            "Synced (block $walletHeight)"
                        } else {
                            "Syncing: $walletHeight / $daemonHeight"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (synced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            } else {
                Text(
                    text = "Connecting to daemon...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AccountMoneroManager.getMoneroAddress()?.let { addr ->
                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Address (tap to open wallet, long press to copy)",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = addr,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.combinedClickable(
                            onClick = {
                                try {
                                    val intent =
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("monero:$addr"),
                                        )
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    clipboardManager.setText(AnnotatedString(addr))
                                    android.widget.Toast
                                        .makeText(context, "Address copied (no wallet app found)", android.widget.Toast.LENGTH_SHORT)
                                        .show()
                                }
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(addr))
                                android.widget.Toast
                                    .makeText(context, "Address copied", android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            },
                        ),
                )
            }
        }
    }
}
