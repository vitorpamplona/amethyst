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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.relay.LocalRelayStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun LocalRelaySettingsScreen(
    localRelayStore: LocalRelayStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val enabled by localRelayStore.enabled.collectAsState()
    val writesDisabled by localRelayStore.writesDisabled.collectAsState()
    val lastError by localRelayStore.lastError.collectAsState()
    val eventCount by localRelayStore.eventCount.collectAsState()
    val dbSizeBytes by localRelayStore.dbSizeBytes.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Local Relay",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status section
        StatusSection(
            enabled = enabled,
            writesDisabled = writesDisabled,
            onToggle = { localRelayStore.setEnabled(it) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Disk full warning
        DiskFullWarning(
            writesDisabled = writesDisabled,
            onPrune = {
                scope.launch(Dispatchers.IO) {
                    localRelayStore.pruneOldEvents()
                    localRelayStore.enableWrites()
                }
            },
            onClear = {
                scope.launch(Dispatchers.IO) {
                    localRelayStore.clearAll()
                    localRelayStore.enableWrites()
                }
            },
        )

        // Statistics section
        CollapsibleSection(title = "Statistics", initiallyExpanded = true) {
            StatisticsContent(eventCount = eventCount, dbSizeBytes = dbSizeBytes)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Storage management
        CollapsibleSection(title = "Storage", initiallyExpanded = false) {
            StorageContent(
                onPrune = { scope.launch(Dispatchers.IO) { localRelayStore.pruneOldEvents() } },
                onVacuum = { scope.launch(Dispatchers.IO) { localRelayStore.vacuum() } },
                onClearAll = { scope.launch(Dispatchers.IO) { localRelayStore.clearAll() } },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Export / Import
        CollapsibleSection(title = "Export / Import", initiallyExpanded = false) {
            ExportImportContent(
                onExport = { file -> scope.launch(Dispatchers.IO) { localRelayStore.exportEvents(file) } },
                onImport = { file -> scope.launch(Dispatchers.IO) { localRelayStore.importEvents(file) } },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Errors section
        CollapsibleSection(title = "Errors", initiallyExpanded = false) {
            ErrorsContent(lastError = lastError, onClear = { localRelayStore.clearError() })
        }
    }
}

@Composable
private fun StatusSection(
    enabled: Boolean,
    writesDisabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol =
                    if (enabled && !writesDisabled) {
                        MaterialSymbols.CheckCircle
                    } else {
                        MaterialSymbols.Storage
                    },
                contentDescription = null,
                tint =
                    if (enabled && !writesDisabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Local Event Cache", style = MaterialTheme.typography.titleSmall)
                Text(
                    text =
                        when {
                            !enabled -> "Disabled"
                            writesDisabled -> "Active (writes paused)"
                            else -> "Active"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun DiskFullWarning(
    writesDisabled: Boolean,
    onPrune: () -> Unit,
    onClear: () -> Unit,
) {
    AnimatedVisibility(
        visible = writesDisabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Writes paused \u2014 disk space low or error occurred",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrune) { Text("Prune Old Events") }
                    OutlinedButton(onClick = onClear) { Text("Clear Cache") }
                }
            }
        }
    }
}

@Composable
private fun StatisticsContent(
    eventCount: Long,
    dbSizeBytes: Long,
) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
        StatRow("Total events", "$eventCount")
        StatRow("Database size", formatBytes(dbSizeBytes))
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorageContent(
    onPrune: () -> Unit,
    onVacuum: () -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onPrune, modifier = Modifier.fillMaxWidth()) {
            Text("Prune events older than 30 days")
        }
        OutlinedButton(onClick = onVacuum, modifier = Modifier.fillMaxWidth()) {
            Text("Reclaim disk space (VACUUM)")
        }
        HorizontalDivider()
        Button(
            onClick = onClearAll,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(symbol = MaterialSymbols.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Clear all cached events")
        }
    }
}

@Composable
private fun ExportImportContent(
    onExport: (File) -> Unit,
    onImport: (File) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                val dialog = FileDialog(null as Frame?, "Export Events", FileDialog.SAVE)
                dialog.file = "events.jsonl"
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) onExport(File(dir, file))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(symbol = MaterialSymbols.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export events (JSONL)")
        }
        OutlinedButton(
            onClick = {
                val dialog = FileDialog(null as Frame?, "Import Events", FileDialog.LOAD)
                dialog.isVisible = true
                val dir = dialog.directory
                val file = dialog.file
                if (dir != null && file != null) onImport(File(dir, file))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(symbol = MaterialSymbols.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import events (JSONL)")
        }
    }
}

@Composable
private fun ErrorsContent(
    lastError: String?,
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
        if (lastError != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onClear) { Text("Clear errors") }
        } else {
            Text(
                text = "No errors",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            content()
        }
    }
}

private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
