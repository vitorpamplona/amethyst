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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.model.DesktopRelayCategories
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Settings section for the shared "index relays" — the set used by
 * `FeedMetadataCoordinator` (Desktop) and `amy wot sync` (CLI) to fetch
 * profile metadata (kind 0) and follow lists (kind 3).
 *
 * The list mutates via [DesktopRelayCategories.setIndexRelays], which
 * writes through to [PreferencesIndexRelays]. Deletions land
 * immediately; the running Desktop coordinator continues using its
 * constructor-time snapshot until the app is relaunched (documented in
 * the explainer below the title).
 */
@Composable
fun IndexRelaysSection(
    categories: DesktopRelayCategories,
    modifier: Modifier = Modifier,
) {
    val relays by categories.indexRelays.collectAsState()
    var newUrl by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Index Relays",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Used to fetch profile metadata and follow lists (Web-of-Trust). Changes take effect on next relaunch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // Current relay list — each row with a remove button.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            relays.forEach { relay ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = relay.url,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { categories.setIndexRelays(relays - relay) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = "Remove ${relay.url}",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Add-row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                placeholder = { Text("wss://relay.example") },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Button(
                onClick = {
                    val normalized = RelayUrlNormalizer.normalizeOrNull(newUrl.trim())
                    if (normalized != null) {
                        categories.setIndexRelays(relays + normalized)
                        newUrl = ""
                    }
                },
                enabled = newUrl.isNotBlank(),
            ) {
                Text("Add")
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}
