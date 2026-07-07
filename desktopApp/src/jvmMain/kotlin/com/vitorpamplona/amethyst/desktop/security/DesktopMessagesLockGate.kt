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
import androidx.compose.runtime.LaunchedEffect
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
import com.vitorpamplona.amethyst.commons.privacylock.LockScope
import com.vitorpamplona.amethyst.commons.privacylock.LockState
import com.vitorpamplona.amethyst.commons.privacylock.lockStateFor
import kotlinx.coroutines.delay

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
 *
 * Enforces exponential backoff after repeated failed attempts (5 fails →
 * 30 s, doubling, capped at 5 min). Backoff state persists across restarts.
 */
@Composable
fun DesktopMessagesLockGate(content: @Composable () -> Unit) {
    val lockState = lockStateFor(LockScope.Messages)
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
    val lockState = lockStateFor(LockScope.Messages)
    val settings = LocalPrivacyLockSettings.current
    val stored by settings.passwordHashed.collectAsState()
    val lockedUntil by settings.lockedUntilEpochMs.collectAsState()

    var input by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var remainingMs by remember { mutableStateOf(lockoutRemaining(lockedUntil)) }

    LaunchedEffect(lockedUntil) {
        while (true) {
            val r = lockoutRemaining(lockedUntil)
            remainingMs = r
            if (r <= 0) break
            delay(500)
        }
    }

    val submit: () -> Unit = {
        if (remainingMs <= 0) {
            val ok = stored?.let { PasswordHasher.verify(input.toCharArray(), it) } == true
            if (ok) {
                input = ""
                showError = false
                lockState.onUnlockSuccess()
            } else {
                showError = true
                lockState.onFailedUnlockAttempt(System.currentTimeMillis())
            }
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
                    enabled = remainingMs <= 0,
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = showError,
                    supportingText =
                        when {
                            remainingMs > 0 -> {
                                { Text("Too many attempts. Try again in ${formatCountdown(remainingMs)}.") }
                            }
                            showError -> {
                                { Text("Wrong password") }
                            }
                            else -> null
                        },
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Box(modifier = Modifier.size(16.dp))
                Button(
                    onClick = submit,
                    enabled = input.isNotEmpty() && remainingMs <= 0,
                ) {
                    Text("Unlock")
                }
            }
        }
    }
}

private fun lockoutRemaining(untilEpochMs: Long?): Long {
    val until = untilEpochMs ?: return 0
    val diff = until - System.currentTimeMillis()
    return if (diff > 0) diff else 0
}

private fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
}
