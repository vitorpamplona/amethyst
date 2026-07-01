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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.privacylock.LocalMessagesLockState

/**
 * One-time discovery banner at the top of the Desktop Messages column.
 * Nudges users who haven't enabled the privacy lock yet. Modeled on
 * `OfflineBanner.kt` (AnimatedVisibility + Surface + Row).
 *
 * Visibility: `!lockEnabled && !firstRunCardSeen`. Dismissal is sticky
 * per the `firstRunCardSeen` flag — the banner does NOT reappear if
 * the user later disables the lock.
 *
 * Renders nothing when the gate is Locked (implicit — the gate replaces
 * content, so this composable never composes in that case).
 */
@Composable
fun MessagesFirstRunBanner() {
    val settings = LocalPrivacyLockSettings.current
    val lockState = LocalMessagesLockState.current
    val enabled by settings.lockEnabled.collectAsState()
    val seen by settings.firstRunCardSeen.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !enabled && !seen,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lock the Messages tab?",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Require a password before Messages shows. Feed and profile stay open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { settings.setFirstRunCardSeen(true) }) {
                    Text("Not now")
                }
                Button(onClick = { showDialog = true }) {
                    Text("Enable")
                }
            }
        }
    }

    if (showDialog) {
        SetPasswordDialog(
            existingHash = null,
            onDismiss = { showDialog = false },
            onConfirm = { newHash ->
                settings.setPasswordHashed(newHash)
                settings.setLockEnabled(true)
                settings.setFirstRunCardSeen(true)
                // Keep the user Unlocked — don't kick them to the lock screen
                // right after they just entered the password.
                lockState.onUnlockSuccess()
                showDialog = false
            },
        )
    }
}
