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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.model.AccountMoneroManager
import com.vitorpamplona.amethyst.model.preferences.MoneroSettings
import com.vitorpamplona.amethyst.ui.navigation.navs.INav

@Composable
fun MoneroWalletScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    var walletStarted by remember { mutableStateOf(false) }
    val address = if (walletStarted) AccountMoneroManager.getMoneroAddress() else null
    val balance = if (walletStarted) AccountMoneroManager.getMoneroBalance() else 0L

    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Monero Wallet",
                style = MaterialTheme.typography.headlineMedium,
            )

            if (!walletStarted) {
                Text(
                    text = "Wallet not connected",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(onClick = {
                    AccountMoneroManager.startMonero(
                        context = context,
                        spendKey = "",
                        walletName = "default",
                        password = "",
                        settings = MoneroSettings(),
                    )
                    walletStarted = true
                }) {
                    Text("Connect Wallet")
                }
            } else {
                Text(
                    text = "Balance: ${balance / 1_000_000_000_000.0} XMR",
                    style = MaterialTheme.typography.titleLarge,
                )

                address?.let { addr ->
                    Text(
                        text = "Address:",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = addr,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
