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
package com.vitorpamplona.amethyst.desktop.ui.tor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType

/**
 * Inline Tor settings section for the desktop settings screen.
 * Shows mode selector (Off/Internal/External) with status indicator
 * and an "Advanced..." button to open the full dialog.
 */
@Composable
fun TorSettingsSection(
    torStatus: TorServiceStatus,
    currentSettings: TorSettings,
    onSettingsChanged: (TorSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    var pendingSettings by remember { mutableStateOf<TorSettings?>(null) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tor",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.width(12.dp))
                TorStatusIndicator(status = torStatus)
            }
            TextButton(onClick = { showDialog = true }) {
                Text("Advanced...")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Route relay connections through Tor for privacy. Internal mode bundles a Tor daemon; External mode connects to your own SOCKS proxy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Mode selector
        SingleChoiceSegmentedButtonRow {
            TorType.entries.forEachIndexed { index, torType ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TorType.entries.size),
                    onClick = { pendingSettings = currentSettings.copy(torType = torType) },
                    selected = currentSettings.torType == torType,
                ) {
                    Text(torType.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        // External port input
        if (currentSettings.torType == TorType.EXTERNAL) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SOCKS Port:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = currentSettings.externalSocksPort.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { port ->
                            if (port in 1..65535) {
                                onSettingsChanged(currentSettings.copy(externalSocksPort = port))
                            }
                        }
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                )
            }
        }
    }

    if (showDialog) {
        TorSettingsDialog(
            currentSettings = currentSettings,
            torStatus = torStatus,
            onSettingsChanged = onSettingsChanged,
            onDismiss = { showDialog = false },
        )
    }

    // Restart confirmation dialog
    pendingSettings?.let { settings ->
        AlertDialog(
            onDismissRequest = { pendingSettings = null },
            title = { Text("Restart Required") },
            text = { Text("Changing Tor mode requires restarting. Your session will be briefly interrupted.") },
            confirmButton = {
                TextButton(onClick = {
                    onSettingsChanged(settings)
                    pendingSettings = null
                }) { Text("Restart") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSettings = null }) { Text("Cancel") }
            },
        )
    }
}
