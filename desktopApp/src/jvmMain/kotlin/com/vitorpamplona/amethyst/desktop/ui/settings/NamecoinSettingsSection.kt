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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS

/**
 * Complete settings section for Namecoin ElectrumX server configuration.
 *
 * Desktop port of the Android `NamecoinSettingsSection.kt` composable.
 * Uses `onPreviewKeyEvent` for Enter-key handling instead of Android's
 * `KeyboardActions`/`LocalSoftwareKeyboardController`.
 *
 * @param settings      Current [NamecoinSettings] state
 * @param onToggleEnabled Called when user toggles the master switch
 * @param onAddServer     Called with `host:port[:tcp]` when user adds a server
 * @param onRemoveServer  Called with the server string to remove
 * @param onReset         Called when user resets to defaults
 */
@Composable
fun NamecoinSettingsSection(
    settings: NamecoinSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onAddServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        // ── Section header ─────────────────────────────────────────
        NamecoinSectionHeader(enabled = settings.enabled, onToggle = onToggleEnabled)

        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                // ── Explanation ─────────────────────────────────────
                Text(
                    "Namecoin names (.bit, d/, id/) are resolved via ElectrumX servers. " +
                        "By default, public community servers are used. " +
                        "For maximum privacy, add your own server below — when custom " +
                        "servers are set, the defaults are completely ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // ── Active servers display ─────────────────────────
                NamecoinActiveServersDisplay(settings = settings)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))

                // ── Custom servers list ────────────────────────────
                NamecoinCustomServersList(
                    servers = settings.customServers,
                    onRemove = onRemoveServer,
                )

                // ── Add server input ───────────────────────────────
                NamecoinAddServerInput(onAdd = onAddServer)

                Spacer(Modifier.height(8.dp))

                // ── Reset button ───────────────────────────────────
                if (settings.hasCustomServers) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onReset) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset to defaults")
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────

@Composable
private fun NamecoinSectionHeader(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF4A90D9), // Namecoin blue
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Namecoin Resolution",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Blockchain identity lookups (.bit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun NamecoinActiveServersDisplay(settings: NamecoinSettings) {
    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS
    val isCustom = settings.hasCustomServers

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Active servers",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (isCustom) {
                Text(
                    "CUSTOM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A90D9),
                    modifier = Modifier
                        .background(
                            Color(0xFF4A90D9).copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    "DEFAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        servers.forEach { server ->
            NamecoinServerRow(
                displayText = "${server.host}:${server.port}" +
                    if (!server.useSsl) " (tcp)" else " (tls)",
                isActive = true,
            )
        }
    }
}

@Composable
private fun NamecoinCustomServersList(
    servers: List<String>,
    onRemove: (String) -> Unit,
) {
    if (servers.isEmpty()) {
        Text(
            "No custom servers configured",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        Text(
            "Custom servers (used exclusively)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        servers.forEach { server ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = server,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(server) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove server",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NamecoinAddServerInput(onAdd: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun tryAdd() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            validationError = "Enter a server address"
            return
        }
        val parsed = NamecoinSettings.parseServerString(trimmed)
        if (parsed == null) {
            validationError = "Invalid format. Use host:port or host:port:tcp"
            return
        }
        validationError = null
        onAdd(trimmed)
        input = ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                validationError = null
            },
            label = { Text("Add ElectrumX server") },
            placeholder = { Text("host:port or host:port:tcp") },
            singleLine = true,
            isError = validationError != null,
            supportingText = validationError?.let { err ->
                { Text(err, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier
                .weight(1f)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                        tryAdd()
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { tryAdd() },
            modifier = Modifier
                .padding(top = 8.dp)
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    RoundedCornerShape(8.dp),
                ),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add server",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun NamecoinServerRow(
    displayText: String,
    isActive: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            fontSize = 10.sp,
            color = if (isActive) {
                Color(0xFF2E8B57)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
