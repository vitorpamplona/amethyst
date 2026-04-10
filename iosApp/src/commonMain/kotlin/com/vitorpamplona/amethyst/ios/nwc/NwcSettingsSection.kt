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
package com.vitorpamplona.amethyst.ios.nwc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Settings section for Nostr Wallet Connect (NIP-47).
 * Allows pasting a NWC connection string and shows connection status.
 */
@Composable
fun NwcSettingsSection() {
    val connectionUri by NwcSettings.connectionUri.collectAsState()
    val parsedConfig by NwcSettings.parsedConfig.collectAsState()
    var editUri by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Section header
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Text(
            "⚡",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Wallet Connect (NWC)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }

    if (parsedConfig != null && !isEditing) {
        // Connected state
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                    ).padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("●", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Wallet Connected",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4CAF50),
                )
            }

            Spacer(Modifier.height(8.dp))

            parsedConfig?.let { config ->
                Text(
                    "Relay: ${config.relayUri.url}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Wallet: ${config.pubKeyHex.take(12)}...${config.pubKeyHex.takeLast(8)}",
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row {
                TextButton(onClick = {
                    editUri = connectionUri ?: ""
                    isEditing = true
                }) {
                    Text("Change")
                }
                IconButton(
                    onClick = {
                        NwcSettings.setConnectionUri(null)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove wallet connection",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    } else {
        // Not connected / editing state
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                    ).padding(16.dp),
        ) {
            Text(
                "Connect a wallet to send zaps. Paste your NWC connection string below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = editUri,
                onValueChange = {
                    editUri = it
                    error = null
                },
                label = { Text("NWC Connection String") },
                placeholder = { Text("nostr+walletconnect://...") },
                singleLine = false,
                maxLines = 3,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { msg ->
                Spacer(Modifier.height(4.dp))
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            Row {
                TextButton(
                    onClick = {
                        val trimmed = editUri.trim()
                        if (trimmed.isBlank()) {
                            error = "Please paste a NWC connection string"
                            return@TextButton
                        }
                        if (!trimmed.startsWith("nostr+walletconnect://") &&
                            !trimmed.startsWith("nostrwalletconnect://") &&
                            !trimmed.startsWith("amethyst+walletconnect://")
                        ) {
                            error = "Invalid format. Must start with nostr+walletconnect://"
                            return@TextButton
                        }
                        try {
                            NwcSettings.setConnectionUri(trimmed)
                            if (NwcSettings.isConfigured()) {
                                isEditing = false
                                error = null
                            } else {
                                error = "Failed to parse connection string"
                            }
                        } catch (e: Exception) {
                            error = "Error: ${e.message}"
                        }
                    },
                ) {
                    Text("Connect")
                }

                if (connectionUri != null) {
                    TextButton(onClick = {
                        isEditing = false
                        error = null
                    }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
