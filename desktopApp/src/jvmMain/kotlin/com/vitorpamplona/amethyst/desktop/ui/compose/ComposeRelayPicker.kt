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
package com.vitorpamplona.amethyst.desktop.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@Immutable
data class RelayPickerState(
    val allRelays: Set<NormalizedRelayUrl>,
    val connectedRelays: Set<NormalizedRelayUrl>,
) {
    companion object {
        val EMPTY = RelayPickerState(emptySet(), emptySet())
    }
}

@Composable
fun ComposeRelayPicker(
    pickerState: RelayPickerState,
    selectedRelays: Set<NormalizedRelayUrl>,
    onToggleRelay: (NormalizedRelayUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Collapsed header
        Row(
            modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) MaterialSymbols.ExpandMore else MaterialSymbols.ChevronRight,
                contentDescription = null,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Relays (${selectedRelays.size} of ${pickerState.allRelays.size})",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Collapsed: show chips
        if (!expanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 28.dp),
            ) {
                items(selectedRelays.toList().sortedBy { it.url }, key = { it.url }) { url ->
                    AssistChip(
                        onClick = {},
                        label = { Text(url.displayUrl(), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }

        // Expanded: scrollable checkboxes
        AnimatedVisibility(expanded) {
            val sortedRelays =
                remember(pickerState.allRelays) {
                    pickerState.allRelays.sortedBy { it.url }
                }
            LazyColumn(
                modifier = Modifier.padding(start = 28.dp).heightIn(max = 200.dp),
            ) {
                items(sortedRelays, key = { it.url }) { url ->
                    val connected = url in pickerState.connectedRelays
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = url in selectedRelays,
                            onCheckedChange = { onToggleRelay(url) },
                            enabled = connected,
                        )
                        Text(
                            url.displayUrl(),
                            color =
                                if (connected) {
                                    LocalContentColor.current
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                        )
                        if (!connected) {
                            Text(
                                " (disconnected)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}
