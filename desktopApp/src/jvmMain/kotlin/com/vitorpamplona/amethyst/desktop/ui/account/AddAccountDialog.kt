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
package com.vitorpamplona.amethyst.desktop.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.ui.auth.LoginCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AddAccountDialog(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAccountAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loginProgress by accountManager.loginProgress.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(480.dp).padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Header with title + close button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Add Account",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                LoginCard(
                    onLogin = { keyInput ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                accountManager.ensureCurrentAccountInStorage()
                            }
                            val result = accountManager.loginWithKey(keyInput)
                            if (result.isSuccess) {
                                withContext(Dispatchers.IO) {
                                    accountManager.saveCurrentAccount()
                                }
                                onAccountAdded()
                            }
                        }
                        Result.success(Unit)
                    },
                    onGenerateNew = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                accountManager.ensureCurrentAccountInStorage()
                            }
                            accountManager.generateNewAccount()
                            withContext(Dispatchers.IO) {
                                accountManager.saveCurrentAccount()
                            }
                            onAccountAdded()
                        }
                    },
                    onLoginBunker = { bunkerUri ->
                        accountManager.ensureCurrentAccountInStorage()
                        val result = accountManager.loginWithBunker(bunkerUri)
                        if (result.isSuccess) {
                            onAccountAdded()
                        }
                        result.map { }
                    },
                    onLoginNostrConnect = { onUriGenerated ->
                        accountManager.ensureCurrentAccountInStorage()
                        val result = accountManager.loginWithNostrConnect(onUriGenerated)
                        if (result.isSuccess) {
                            onAccountAdded()
                        }
                        result.map { }
                    },
                    loginProgress = loginProgress,
                    cardWidth = 420.dp,
                    title = "Import Account",
                    subtitle = "Paste nsec, npub (view-only), or use a remote signer",
                )
            }
        }
    }
}
