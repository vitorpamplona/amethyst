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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.ui.auth.LoginCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    accountManager: AccountManager,
    onDismiss: () -> Unit,
    onAccountAdded: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val loginProgress by accountManager.loginProgress.collectAsState()

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Add Account",
        state = rememberDialogState(size = DpSize(480.dp, 600.dp)),
        resizable = false,
    ) {
        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Add Account") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LoginCard(
                        onLogin = { keyInput ->
                            // All steps must be sequential — no fire-and-forget
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
                            // Return success to dismiss any error in LoginCard
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
                            // ensureCurrentAccountInStorage first, then bunker login
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
}
