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
package com.vitorpamplona.amethyst.commons.ui.privacylock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.lockStateFor
import kotlinx.coroutines.launch

/**
 * Shared lock-screen surface used by [MessagesLockGate] and [WalletLockGate].
 * Runs the async [CredentialPrompter] path (biometric / OS credential on
 * Android + iOS). Desktop platforms use a password-input inline lock screen
 * instead — see `DesktopMessagesLockGate` / `DesktopWalletLockGate`.
 *
 * Kept `internal` so the only public entry points are the per-scope Gates.
 */
@Composable
internal fun LockScreen(
    scope: LockScope,
    title: String,
    subtitle: String,
    unlockLabel: String,
) {
    val lockState = lockStateFor(scope)
    val prompter = LocalCredentialPrompter.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(prompter) {
        if (!prompter.available) {
            lockState.onCredentialUnavailable()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                symbol = MaterialSymbols.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Box(modifier = Modifier.size(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.size(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Box(modifier = Modifier.size(32.dp))
            Button(
                onClick = {
                    coroutineScope.launch {
                        when (prompter.prompt()) {
                            PromptResult.Success -> lockState.onUnlockSuccess()
                            PromptResult.Unavailable -> lockState.onCredentialUnavailable()
                            else -> Unit
                        }
                    }
                },
                enabled = prompter.available,
            ) {
                Text(text = unlockLabel)
            }
        }
    }
}
