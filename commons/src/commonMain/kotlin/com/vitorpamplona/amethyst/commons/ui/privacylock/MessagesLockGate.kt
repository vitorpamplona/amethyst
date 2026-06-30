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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LocalMessagesLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import kotlinx.coroutines.launch

/**
 * Wraps the Messages route and gates entry behind the credential prompt.
 *
 * Branch selection happens SYNCHRONOUSLY in composition — no
 * [LaunchedEffect] guard — so the chat content composable never enters
 * composition while [LockState.Locked]. Closes the deep-link race
 * (plan §Security Hardening H1).
 *
 * The gate is an overlay, NOT a wrapper that disposes content. While
 * locked, the [content] lambda is not invoked at all; on unlock, the
 * lambda is invoked fresh. This means TextField drafts that use
 * `rememberSaveable` survive a lock cycle (SavedStateRegistry-backed).
 * For plain `remember` state, drafts are cleared — accept this trade-off.
 *
 * The gate also fires [MessagesLockState.onLeaveRoute] from its
 * [DisposableEffect.onDispose] block, so navigating away locks immediately.
 */
@Composable
fun MessagesLockGate(content: @Composable () -> Unit) {
    val lockState = LocalMessagesLockState.current
    val current by lockState.state.collectAsState()

    DisposableEffect(lockState) {
        onDispose { lockState.onLeaveRoute() }
    }

    when (current) {
        is LockState.Locked -> LockScreen()
        else -> content()
    }
}

@Composable
private fun LockScreen() {
    val lockState = LocalMessagesLockState.current
    val prompter = LocalCredentialPrompter.current
    val scope = rememberCoroutineScope()

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
                text = "Messages locked",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Box(modifier = Modifier.size(8.dp))
            Text(
                text = "Unlock to read or send messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Box(modifier = Modifier.size(32.dp))
            Button(
                onClick = {
                    scope.launch {
                        when (prompter.prompt()) {
                            PromptResult.Success -> lockState.onUnlockSuccess()
                            PromptResult.Unavailable -> lockState.onCredentialUnavailable()
                            else -> Unit
                        }
                    }
                },
                enabled = prompter.available,
            ) {
                Text(text = "Unlock")
            }
        }
    }
}
