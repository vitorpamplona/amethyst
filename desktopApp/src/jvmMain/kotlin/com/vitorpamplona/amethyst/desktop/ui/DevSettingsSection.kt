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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.account.AccountState
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Developer settings section - shows sensitive keys for debugging.
 * Only enable in debug builds or with explicit debug flag.
 */
@Composable
fun DevSettingsSection(
    account: AccountState.LoggedIn,
    modifier: Modifier = Modifier,
) {
    var showKeys by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 2.dp,
                    color = Color(0xFFFF9800), // Orange warning color
                    shape = RoundedCornerShape(8.dp),
                ).background(
                    color = Color(0xFF332200), // Dark orange tint
                    shape = RoundedCornerShape(8.dp),
                ).padding(16.dp),
    ) {
        // Warning header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Warning",
                tint = Color(0xFFFF9800),
            )
            Text(
                "Developer Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF9800),
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Debug mode only - Handle keys with care",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        HorizontalDivider(color = Color(0xFFFF9800).copy(alpha = 0.3f))

        Spacer(Modifier.height(16.dp))

        // Toggle button
        OutlinedButton(
            onClick = { showKeys = !showKeys },
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF9800),
                ),
        ) {
            Text(if (showKeys) "Hide Keys" else "Show Keys")
        }

        if (showKeys) {
            Spacer(Modifier.height(16.dp))

            // npub
            KeyRow(
                label = "Public Key (npub)",
                value = account.npub,
                isSensitive = false,
            )

            Spacer(Modifier.height(12.dp))

            // nsec (only if available)
            account.nsec?.let { nsec ->
                KeyRow(
                    label = "Private Key (nsec)",
                    value = nsec,
                    isSensitive = true,
                )
            } ?: run {
                Text(
                    "Read-only mode - No private key available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Hex key
            KeyRow(
                label = "Public Key (hex)",
                value = account.pubKeyHex,
                isSensitive = false,
            )
        }
    }
}

@Composable
private fun KeyRow(
    label: String,
    value: String,
    isSensitive: Boolean,
    modifier: Modifier = Modifier,
) {
    var copiedRecently by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (isSensitive) {
                        Color(0xFFFF5252)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                fontWeight = FontWeight.Bold,
            )

            Button(
                onClick = {
                    copyToClipboard(value)
                    copiedRecently = true
                },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (copiedRecently) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                    ),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(if (copiedRecently) "Copied!" else "Copy")
            }
        }

        Spacer(Modifier.height(4.dp))

        // Display the key in monospace
        Text(
            value,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
            color =
                if (isSensitive) {
                    Color(0xFFFF5252)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(4.dp),
                    ).padding(8.dp),
        )

        // Reset copied state after a delay
        if (copiedRecently) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2000)
                copiedRecently = false
            }
        }
    }
}

/**
 * Copy text to system clipboard using AWT Toolkit.
 */
private fun copyToClipboard(text: String) {
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
