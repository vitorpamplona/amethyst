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
package com.vitorpamplona.amethyst.desktop.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LocalMessagesLockState
import com.vitorpamplona.amethyst.commons.privacylock.LockState

/**
 * Desktop equivalent of `MessagesLockGate`. Uses password verification
 * synchronously — no async CredentialPrompter round-trip needed.
 *
 * Renders content when Disabled / Unlocked; renders an inline password
 * input when Locked. If no password has been set, prompts the user to set
 * one first (this fires from the settings toggle in normal flow, so the
 * fallback exists only as a safety net).
 *
 * Branch selection is SYNCHRONOUS in composition — no LaunchedEffect
 * guard — closing the deep-link race (plan §Security Hardening H1).
 */
@Composable
fun DesktopMessagesLockGate(content: @Composable () -> Unit) {
    val lockState = LocalMessagesLockState.current
    val current by lockState.state.collectAsState()

    DisposableEffect(lockState) {
        onDispose { lockState.onLeaveRoute() }
    }

    when (current) {
        is LockState.Locked -> DesktopLockScreen()
        else -> content()
    }
}

@Composable
private fun DesktopLockScreen() {
    val lockState = LocalMessagesLockState.current
    val settings = com.vitorpamplona.amethyst.desktop.security.LocalPrivacyLockSettings.current
    val stored by settings.passwordHashed.collectAsState()

    var input by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val submit: () -> Unit = {
        val ok = stored?.let { PasswordHasher.verify(input.toCharArray(), it) } == true
        if (ok) {
            input = ""
            showError = false
            lockState.onUnlockSuccess()
        } else {
            showError = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
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
                text = "Enter your privacy-lock password to view messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
            Box(modifier = Modifier.size(24.dp))
            if (stored == null) {
                Text(
                    text = "No password is set yet. Open Settings → Privacy lock to set one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Box(modifier = Modifier.size(16.dp))
                Button(onClick = { lockState.onCredentialUnavailable() }) {
                    Text("Disable lock")
                }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        showError = false
                    },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = showError,
                    supportingText =
                        if (showError) {
                            { Text("Wrong password") }
                        } else {
                            null
                        },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Box(modifier = Modifier.size(16.dp))
                Button(onClick = submit, enabled = input.isNotEmpty()) {
                    Text("Unlock")
                }
            }
        }
    }
}
