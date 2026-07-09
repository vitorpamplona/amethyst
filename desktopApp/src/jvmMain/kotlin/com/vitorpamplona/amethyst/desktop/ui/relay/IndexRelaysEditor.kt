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
import androidx.compose.material3.Button
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.relays.index.PreferencesIndexRelays
import com.vitorpamplona.amethyst.desktop.model.DesktopRelayCategories
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editor for the shared "index relays" — the set used by
 * `FeedMetadataCoordinator` (Desktop) and `amy wot sync` (CLI) to fetch
 * profile metadata (kind 0) and follow lists (kind 3).
 *
 * Matches the buffered-Save UX of the sibling relay editors
 * (Search / DM / Blocked). Add/Remove mutate a local buffer; Save
 * commits the buffer to `PreferencesIndexRelays`. Reset restores the
 * built-in defaults into the buffer (still requires Save to persist).
 *
 * The running Desktop coordinator continues using its constructor-time
 * snapshot until the app is relaunched, so persisted changes take effect
 * on next launch.
 */
@Composable
fun IndexRelaysEditor(
    categories: DesktopRelayCategories,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val persisted by categories.indexRelays.collectAsState()
    val localRelays = remember { mutableStateListOf<com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl>() }
    var newRelayUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(persisted) {
        localRelays.clear()
        localRelays.addAll(persisted.sortedBy { it.url })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Relays queried for profile metadata and follow lists (Web-of-Trust). Changes take effect on next relaunch. Shared with the `amy` CLI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )

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
                                error = tryAddSimpleRelay(newRelayUrl, localRelays)
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
                    error = tryAddSimpleRelay(newRelayUrl, localRelays)
                    if (error == null) newRelayUrl = ""
                },
            ) {
                Icon(MaterialSymbols.Add, contentDescription = "Add relay")
            }
        }

        if (localRelays.isNotEmpty()) {
            Text(
                "${localRelays.size} relay(s) configured",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        localRelays.toList().forEach { url ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    url.displayUrl(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { localRelays.remove(url) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (newRelayUrl.isNotBlank()) {
                        val addError = tryAddSimpleRelay(newRelayUrl, localRelays)
                        if (addError != null) {
                            error = addError
                            return@Button
                        }
                        newRelayUrl = ""
                    }
                    if (localRelays.isEmpty()) {
                        error = "Add at least one relay before saving (or Reset to defaults)"
                        return@Button
                    }
                    categories.setIndexRelays(localRelays.toSet())
                    scope.launch {
                        savedMessage = "Saved ${localRelays.size} relay(s) — restart to apply"
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
                    localRelays.addAll(
                        PreferencesIndexRelays.DEFAULT_INDEX_RELAYS.sortedBy { it.url },
                    )
                    error = null
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
