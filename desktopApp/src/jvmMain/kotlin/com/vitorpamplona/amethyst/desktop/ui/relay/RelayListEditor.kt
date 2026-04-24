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
package com.vitorpamplona.amethyst.desktop.ui.relay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isOnion

@Composable
fun RelayListEditor(
    relays: List<NormalizedRelayUrl>,
    connectedRelays: Set<NormalizedRelayUrl>,
    onAdd: (String) -> NormalizedRelayUrl?,
    onRemove: (NormalizedRelayUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newRelayUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Add relay input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = newRelayUrl,
                onValueChange = {
                    newRelayUrl = it
                    error = null
                },
                label = { Text("wss://relay.example.com") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                modifier =
                    Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) {
                                error = tryAddRelay(newRelayUrl, relays, onAdd)
                                if (error == null) newRelayUrl = ""
                                true
                            } else {
                                false
                            }
                        },
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    error = tryAddRelay(newRelayUrl, relays, onAdd)
                    if (error == null) newRelayUrl = ""
                },
            ) {
                Icon(MaterialSymbols.Add, contentDescription = "Add relay")
            }
        }

        // Relay list
        relays.forEach { url ->
            val isConnected = url in connectedRelays
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        MaterialSymbols.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint =
                            if (isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        url.displayUrl(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (url.isOnion()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            ".onion",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                IconButton(onClick = { onRemove(url) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun tryAddRelay(
    url: String,
    existing: List<NormalizedRelayUrl>,
    onAdd: (String) -> NormalizedRelayUrl?,
): String? {
    val input = normalizeRelayInput(url)
    val error = validateRelayUrl(input)
    if (error != null) return error
    val normalized = RelayUrlNormalizer.normalizeOrNull(input) ?: return "Invalid relay URL"
    if (existing.any { it.url == normalized.url }) {
        return "Relay already added"
    }
    onAdd(input)
    return null
}
