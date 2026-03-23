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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vitorpamplona.amethyst.desktop.service.namecoin.DesktopNamecoinNameService
import com.vitorpamplona.amethyst.desktop.service.namecoin.LocalNamecoinService
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.coroutines.launch

/**
 * Import Follow List dialog for Desktop.
 *
 * Lets users enter an identifier (npub, hex, NIP-05, or Namecoin),
 * resolves it to a pubkey. The actual follow list fetching from relays
 * is a placeholder — full implementation requires relay subscription
 * infrastructure integration.
 */
@Composable
fun ImportFollowListDialog(
    onDismiss: () -> Unit,
    onImport: (List<String>) -> Unit,
) {
    val namecoinService = LocalNamecoinService.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var resolvedPubkey by remember { mutableStateOf<String?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var followList = remember { mutableStateListOf<FollowEntry>() }
    var isFetching by remember { mutableStateOf(false) }

    fun resolveIdentifier() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            error = "Enter an identifier"
            return
        }

        error = null
        isResolving = true
        resolvedPubkey = null
        followList.clear()

        scope.launch {
            try {
                // Try bech32 first
                val bech32Result = decodePublicKeyAsHexOrNull(trimmed)
                if (bech32Result != null) {
                    resolvedPubkey = bech32Result
                    isResolving = false
                    return@launch
                }

                // Try hex pubkey
                if (trimmed.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    resolvedPubkey = trimmed.lowercase()
                    isResolving = false
                    return@launch
                }

                // Try Namecoin
                if (NamecoinNameResolver.isNamecoinIdentifier(trimmed) && namecoinService != null) {
                    val result = namecoinService.resolvePubkey(trimmed)
                    if (result != null) {
                        resolvedPubkey = result
                        isResolving = false
                        return@launch
                    }
                }

                error = "Could not resolve identifier"
                isResolving = false
            } catch (e: Exception) {
                error = e.message ?: "Resolution failed"
                isResolving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
            ) {
                Text(
                    "Import Follow List",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter an npub, hex pubkey, NIP-05, or Namecoin identifier (.bit, d/, id/) " +
                        "to import their follow list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                // Input field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            error = null
                        },
                        label = { Text("Identifier") },
                        placeholder = { Text("npub1..., alice@example.bit, d/example") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                resolveIdentifier()
                                true
                            } else {
                                false
                            }
                        },
                    )
                    Button(
                        onClick = { resolveIdentifier() },
                        enabled = input.isNotBlank() && !isResolving,
                    ) {
                        if (isResolving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text("Resolve")
                        }
                    }
                }

                // Resolved pubkey display
                if (resolvedPubkey != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Resolved: ${resolvedPubkey!!.take(16)}...${resolvedPubkey!!.takeLast(8)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Follow list fetching requires relay subscriptions. " +
                            "The resolved pubkey can be used to follow this user directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Follow list preview (placeholder for when relay fetching is implemented)
                if (followList.isNotEmpty()) {
                    Text(
                        "Follow List (${followList.size} users)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(followList) { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                            ) {
                                Checkbox(
                                    checked = entry.selected,
                                    onCheckedChange = { entry.selected = it },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.pubkey.take(16) + "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    if (resolvedPubkey != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                resolvedPubkey?.let { pk ->
                                    onImport(listOf(pk))
                                }
                            },
                        ) {
                            Text("Follow User")
                        }
                    }
                }
            }
        }
    }
}

data class FollowEntry(
    val pubkey: String,
    var selected: Boolean = true,
)
