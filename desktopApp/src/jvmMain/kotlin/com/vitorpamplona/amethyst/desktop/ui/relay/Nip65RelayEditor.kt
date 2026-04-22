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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.desktop.network.DefaultRelays
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Nip65RelayEditor(
    nip65State: Nip65RelayListState,
    signer: NostrSigner,
    onPublish: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val localRelays = remember { mutableStateListOf<AdvertisedRelayInfo>() }
    var loaded by remember { mutableStateOf(false) }
    var newRelayUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    // React to NIP-65 flow changes (external updates)
    val nip65NoteState by nip65State.getNIP65RelayListFlow().collectAsState()
    val currentNip65Relays =
        remember(nip65NoteState) {
            nip65State.getNIP65RelayList()?.relays() ?: emptyList()
        }

    LaunchedEffect(currentNip65Relays) {
        if (currentNip65Relays.isNotEmpty()) {
            localRelays.clear()
            localRelays.addAll(currentNip65Relays)
        }
        loaded = true
    }

    // Delay showing empty state to allow async cache load
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        if (!loaded) loaded = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (loaded && localRelays.isEmpty()) {
            Text(
                "No NIP-65 relay list published yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedButton(
                onClick = {
                    localRelays.clear()
                    DefaultRelays.RELAYS.forEach { url ->
                        RelayUrlNormalizer.normalizeOrNull(url)?.let { normalized ->
                            localRelays.add(AdvertisedRelayInfo(normalized, AdvertisedRelayType.BOTH))
                        }
                    }
                },
            ) {
                Text("Populate from defaults")
            }
            Spacer(Modifier.height(8.dp))
        }

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
                                error = tryAddNip65Relay(newRelayUrl, localRelays)
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
                    error = tryAddNip65Relay(newRelayUrl, localRelays)
                    if (error == null) newRelayUrl = ""
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add relay")
            }
        }

        // Relay list
        localRelays.toList().forEachIndexed { index, relay ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    relay.relayUrl.displayUrl(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = relay.type == AdvertisedRelayType.READ,
                        onClick = {
                            localRelays[index] = AdvertisedRelayInfo(relay.relayUrl, AdvertisedRelayType.READ)
                        },
                        label = { Text("Read", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = relay.type == AdvertisedRelayType.WRITE,
                        onClick = {
                            localRelays[index] = AdvertisedRelayInfo(relay.relayUrl, AdvertisedRelayType.WRITE)
                        },
                        label = { Text("Write", style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = relay.type == AdvertisedRelayType.BOTH,
                        onClick = {
                            localRelays[index] = AdvertisedRelayInfo(relay.relayUrl, AdvertisedRelayType.BOTH)
                        },
                        label = { Text("Both", style = MaterialTheme.typography.labelSmall) },
                    )
                }

                IconButton(onClick = { localRelays.remove(relay) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // Auto-add pending input
                    if (newRelayUrl.isNotBlank()) {
                        val addError = tryAddNip65Relay(newRelayUrl, localRelays)
                        if (addError != null) {
                            error = addError
                            return@Button
                        }
                        newRelayUrl = ""
                    }
                    if (localRelays.isEmpty()) {
                        error = "Add at least one relay before saving"
                        return@Button
                    }
                    scope.launch {
                        try {
                            val event = nip65State.saveRelayList(localRelays.toList())
                            onPublish(event)
                            savedMessage = "Published ${localRelays.size} relay(s)"
                        } catch (e: Exception) {
                            savedMessage = "Failed: ${e.message}"
                        }
                        delay(3000)
                        savedMessage = null
                    }
                },
            ) {
                Text("Save")
            }

            OutlinedButton(
                onClick = {
                    localRelays.clear()
                    DefaultRelays.RELAYS.forEach { url ->
                        RelayUrlNormalizer.normalizeOrNull(url)?.let { normalized ->
                            localRelays.add(AdvertisedRelayInfo(normalized, AdvertisedRelayType.BOTH))
                        }
                    }
                },
            ) {
                Text("Reset to defaults")
            }

            savedMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun tryAddNip65Relay(
    url: String,
    existing: MutableList<AdvertisedRelayInfo>,
): String? {
    val input = normalizeRelayInput(url)
    val error = validateRelayUrl(input)
    if (error != null) return error
    val normalized = RelayUrlNormalizer.normalizeOrNull(input)!!
    if (existing.any { it.relayUrl.url == normalized.url }) {
        return "Relay already added"
    }
    existing.add(AdvertisedRelayInfo(normalized, AdvertisedRelayType.BOTH))
    return null
}
