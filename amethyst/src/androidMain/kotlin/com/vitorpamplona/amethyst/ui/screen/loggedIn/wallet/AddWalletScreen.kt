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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.util.getText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Size24Modifier
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val walletViewModel: WalletViewModel = viewModel()
    walletViewModel.init(accountViewModel)

    var walletName by remember { mutableStateOf("") }
    var nwcUri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var qrScanning by remember { mutableStateOf(false) }

    val uri = LocalUriHandler.current
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.wallet_add_connection)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringRes(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = walletName,
                onValueChange = { walletName = it },
                label = { Text(stringRes(R.string.wallet_name)) },
                placeholder = { Text(stringRes(R.string.wallet_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connect action buttons: Connect Wallet app, Paste, QR scan
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    shape = ButtonBorder,
                    onClick = {
                        try {
                            uri.openUri(
                                "nostrnwc://connect?appname=Amethyst&appicon=https%3A%2F%2Fraw.githubusercontent.com%2Fvitorpamplona%2Famethyst%2Frefs%2Fheads%2Fmain%2Ficon.png&callback=amethyst%2Bwalletconnect%3A%2F%2Fdlnwc",
                            )
                        } catch (_: IllegalArgumentException) {
                            accountViewModel.toastManager.toast(
                                R.string.couldnt_find_nwc_wallets,
                                R.string.couldnt_find_nwc_wallets_description,
                            )
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringRes(R.string.wallet_connect_connect_app))
                }

                Spacer(DoubleHorzSpacer)

                // Paste from clipboard
                IconButton(
                    onClick = {
                        scope.launch {
                            val clipText = clipboardManager.getText()
                            if (clipText != null) {
                                nwcUri = clipText
                                error = null
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = stringRes(id = R.string.paste_from_clipboard),
                        modifier = Size24Modifier,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                // QR code scanner
                IconButton(onClick = { qrScanning = true }) {
                    Icon(
                        painter = painterRes(R.drawable.ic_qrcode, 3),
                        contentDescription = stringRes(id = R.string.accessibility_scan_qr_code),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (qrScanning) {
                SimpleQrCodeScanner {
                    qrScanning = false
                    if (!it.isNullOrEmpty()) {
                        nwcUri = it
                        error = null
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nwcUri,
                onValueChange = {
                    nwcUri = it
                    error = null
                },
                label = { Text(stringRes(R.string.wallet_paste_uri)) },
                placeholder = { Text("nostr+walletconnect://...") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    try {
                        val parsed = Nip47WalletConnect.parse(nwcUri.trim())
                        walletViewModel.addWallet(walletName.trim(), parsed)
                        nav.popBack()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        error = e.message ?: "Invalid NWC connection URI"
                    }
                },
                enabled = nwcUri.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(R.string.wallet_save))
            }
        }
    }
}
