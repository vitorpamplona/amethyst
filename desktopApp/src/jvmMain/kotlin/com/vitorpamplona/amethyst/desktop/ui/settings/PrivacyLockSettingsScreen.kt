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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.vitorpamplona.amethyst.commons.privacylock.DmRedactionLevel
import com.vitorpamplona.amethyst.commons.privacylock.InactivityTimer
import com.vitorpamplona.amethyst.commons.privacylock.PrivacyLockSettings
import com.vitorpamplona.amethyst.desktop.security.LocalPrivacyLockSettings
import com.vitorpamplona.amethyst.desktop.security.RemovePasswordDialog
import com.vitorpamplona.amethyst.desktop.security.SetPasswordDialog
import kotlinx.coroutines.launch

/**
 * Desktop privacy-lock settings pane. Column + Card layout (no Scaffold) —
 * matches `LocalRelaySettingsScreen`.
 */
@Composable
fun PrivacyLockSettingsScreen() {
    val settings = LocalPrivacyLockSettings.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LockToggleCard(
                settings = settings,
                onSaved = { message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
            )
            InactivityCard(settings)
            RedactionCard(settings)
            LimitationsCard()
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun LockToggleCard(
    settings: PrivacyLockSettings,
    onSaved: (String) -> Unit,
) {
    val enabled by settings.lockEnabled.collectAsState()
    val stored by settings.passwordHashed.collectAsState()
    var showSetPassword by remember { mutableStateOf(false) }
    var showRemovePassword by remember { mutableStateOf(false) }
    var pendingEnable by remember { mutableStateOf(false) }

    SettingsCard(title = "Lock the Messages tab") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    "Require a password before the Messages column shows. " +
                        "The rest of the app stays open.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) {
                        if (stored == null) {
                            pendingEnable = true
                            showSetPassword = true
                        } else {
                            settings.setLockEnabled(true)
                        }
                    } else {
                        // Disabling requires the current password. If none is set
                        // (corner case — user cleared prefs manually), just
                        // disable directly.
                        if (stored != null) {
                            showRemovePassword = true
                        } else {
                            settings.setLockEnabled(false)
                        }
                    }
                },
            )
        }
        if (enabled && stored != null) {
            Row {
                OutlinedButton(onClick = { showSetPassword = true }) {
                    Text("Change password")
                }
            }
        }
    }

    if (showSetPassword) {
        val wasFirstSet = stored == null
        SetPasswordDialog(
            existingHash = stored,
            onDismiss = {
                showSetPassword = false
                pendingEnable = false
            },
            onConfirm = { newHash ->
                settings.setPasswordHashed(newHash)
                if (pendingEnable) settings.setLockEnabled(true)
                showSetPassword = false
                pendingEnable = false
                onSaved(if (wasFirstSet) "Privacy lock enabled" else "Password updated")
            },
        )
    }

    stored?.let { hash ->
        if (showRemovePassword) {
            RemovePasswordDialog(
                existingHash = hash,
                onDismiss = { showRemovePassword = false },
                onConfirm = {
                    settings.setLockEnabled(false)
                    settings.setPasswordHashed(null)
                    showRemovePassword = false
                    onSaved("Privacy lock removed")
                },
            )
        }
    }
}

@Composable
private fun InactivityCard(settings: PrivacyLockSettings) {
    val enabled by settings.lockEnabled.collectAsState()
    val timer by settings.inactivityTimer.collectAsState()
    if (!enabled) return

    SettingsCard(title = "Auto-lock after") {
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Re-lock Messages after this much inactivity.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { expanded = true }) {
                Text(timer.label())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                InactivityTimer.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label()) },
                        onClick = {
                            settings.setInactivityTimer(entry)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RedactionCard(settings: PrivacyLockSettings) {
    val enabled by settings.lockEnabled.collectAsState()
    val level by settings.dmRedactionLevel.collectAsState()
    if (!enabled) return

    SettingsCard(title = "DM notification preview") {
        var expanded by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    "When lock is on, DM notifications hide sender + message. " +
                        "Change to Full to show them.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { expanded = true }) {
                Text(level.label())
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DmRedactionLevel.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label()) },
                        onClick = {
                            settings.setDmRedactionLevel(entry)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LimitationsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "What this lock does not protect against",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    "This lock hides the Messages column on an unattended device. " +
                        "It does NOT protect against: filesystem access, memory dumps, " +
                        "attached debuggers, or screen-recording apps you've granted access. " +
                        "Your Nostr private key is still stored as it is today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content(this)
        }
    }
}

private fun InactivityTimer.label(): String =
    when (this) {
        InactivityTimer.OneMin -> "1 min"
        InactivityTimer.FiveMin -> "5 min"
        InactivityTimer.FifteenMin -> "15 min"
        InactivityTimer.OneHour -> "1 hour"
        InactivityTimer.Never -> "Never"
    }

private fun DmRedactionLevel.label(): String =
    when (this) {
        DmRedactionLevel.Generic -> "Hidden"
        DmRedactionLevel.Full -> "Full"
    }
