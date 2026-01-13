/**
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.SharedRes
import com.vitorpamplona.amethyst.desktop.account.AccountManager
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.ui.auth.LoginCard
import com.vitorpamplona.amethyst.desktop.ui.auth.NewKeyWarningCard
import dev.icerock.moko.resources.compose.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    accountManager: AccountManager,
    onLoginSuccess: () -> Unit,
) {
    var showNewKeyDialog by remember { mutableStateOf(false) }
    var generatedAccount by remember { mutableStateOf<AccountState.LoggedIn?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(SharedRes.strings.login_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(SharedRes.strings.login_subtitle_desktop),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(48.dp))

        LoginCard(
            onLogin = { keyInput ->
                accountManager.loginWithKey(keyInput).map {
                    // Save account to secure storage (use IO dispatcher to avoid blocking UI)
                    scope.launch(Dispatchers.IO) {
                        accountManager.saveCurrentAccount()
                        onLoginSuccess()
                    }
                }
            },
            onGenerateNew = {
                generatedAccount = accountManager.generateNewAccount()
                showNewKeyDialog = true
            },
        )

        if (showNewKeyDialog && generatedAccount != null) {
            Spacer(Modifier.height(24.dp))
            NewKeyWarningCard(
                npub = generatedAccount!!.npub,
                nsec = generatedAccount!!.nsec,
                onContinue = {
                    showNewKeyDialog = false
                    // Save generated account (use IO dispatcher to avoid blocking UI)
                    scope.launch(Dispatchers.IO) {
                        accountManager.saveCurrentAccount()
                        onLoginSuccess()
                    }
                },
            )
        }
    }
}
