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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import kotlinx.coroutines.launch

/**
 * Wallet Connect (NWC) settings body. Lets the user connect/disconnect a
 * Lightning wallet via a `nostr+walletconnect://` string. Extracted verbatim
 * from the old inline settings screen so it can be slotted into the Settings
 * accordion; the section title is now owned by the accordion card header.
 */
@Composable
fun WalletConnectSettingsSection(
    account: AccountState.LoggedIn,
    accountManager: AccountManager,
    modifier: Modifier = Modifier,
) {
    val nwcConnection by accountManager.nwcConnection.collectAsState()
    var nwcInput by remember { mutableStateOf("") }
    var nwcError by remember { mutableStateOf<String?>(null) }
    val nwcScope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Connect a Lightning wallet to enable zaps. Get a connection string from Alby, Mutiny, or other NWC-compatible wallets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (nwcConnection != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Wallet Connected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Relay: ${nwcConnection!!.relayUri.url}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = { nwcScope.launch { accountManager.clearNwcConnection(account.npub) } },
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Disconnect")
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = nwcInput,
                    onValueChange = {
                        nwcInput = it
                        nwcError = null
                    },
                    label = { Text("NWC Connection String") },
                    placeholder = { Text("nostr+walletconnect://...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = nwcError != null,
                    supportingText = nwcError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                )
                Button(
                    onClick = {
                        nwcScope.launch {
                            val result = accountManager.setNwcConnection(account.npub, nwcInput)
                            result.fold(
                                onSuccess = { nwcInput = "" },
                                onFailure = { nwcError = it.message ?: "Invalid connection string" },
                            )
                        }
                    },
                    enabled = nwcInput.isNotBlank(),
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
