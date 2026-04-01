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
package com.vitorpamplona.amethyst.desktop.ui.tor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.vitorpamplona.amethyst.commons.tor.TorPresetType
import com.vitorpamplona.amethyst.commons.tor.TorServiceStatus
import com.vitorpamplona.amethyst.commons.tor.TorSettings
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.tor.torDefaultPreset
import com.vitorpamplona.amethyst.commons.tor.torFullyPrivate
import com.vitorpamplona.amethyst.commons.tor.torOnlyWhenNeededPreset
import com.vitorpamplona.amethyst.commons.tor.torSmallPayloadsPreset
import com.vitorpamplona.amethyst.commons.tor.whichPreset

@Composable
fun TorSettingsDialog(
    currentSettings: TorSettings,
    torStatus: TorServiceStatus,
    onSettingsChanged: (TorSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var editSettings by remember { mutableStateOf(currentSettings) }

    DialogWindow(
        onCloseRequest = onDismiss,
        title = "Tor Settings",
        state = rememberDialogState(size = DpSize(480.dp, 640.dp)),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier =
                    Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                // Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TorStatusIndicator(status = torStatus)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (torStatus) {
                            is TorServiceStatus.Off -> "Tor is off"
                            is TorServiceStatus.Connecting -> "Connecting to Tor..."
                            is TorServiceStatus.Active -> "Connected via Tor"
                            is TorServiceStatus.Error -> "Error: ${torStatus.message}"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Mode selector
                Text("Mode", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TorType.entries.forEachIndexed { index, torType ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index, TorType.entries.size),
                            onClick = { editSettings = editSettings.copy(torType = torType) },
                            selected = editSettings.torType == torType,
                        ) {
                            Text(torType.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                if (editSettings.torType == TorType.EXTERNAL) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editSettings.externalSocksPort.toString(),
                        onValueChange = { text ->
                            text.toIntOrNull()?.let { port ->
                                if (port in 1..65535) {
                                    editSettings = editSettings.copy(externalSocksPort = port)
                                }
                            }
                        },
                        label = { Text("SOCKS Port") },
                        singleLine = true,
                        modifier = Modifier.width(150.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Presets
                Text("Preset", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                val currentPreset = whichPreset(editSettings)
                PresetRow("Only When Needed", TorPresetType.ONLY_WHEN_NEEDED, currentPreset) {
                    editSettings = torOnlyWhenNeededPreset.copy(torType = editSettings.torType, externalSocksPort = editSettings.externalSocksPort)
                }
                PresetRow("Default", TorPresetType.DEFAULT, currentPreset) {
                    editSettings = torDefaultPreset.copy(torType = editSettings.torType, externalSocksPort = editSettings.externalSocksPort)
                }
                PresetRow("Small Payloads", TorPresetType.SMALL_PAYLOADS, currentPreset) {
                    editSettings = torSmallPayloadsPreset.copy(torType = editSettings.torType, externalSocksPort = editSettings.externalSocksPort)
                }
                PresetRow("Full Privacy", TorPresetType.FULL_PRIVACY, currentPreset) {
                    editSettings = torFullyPrivate.copy(torType = editSettings.torType, externalSocksPort = editSettings.externalSocksPort)
                }
                PresetRow("Custom", TorPresetType.CUSTOM, currentPreset) {}

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Relay Routing
                Text("Relay Routing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ToggleRow(".onion relays via Tor", editSettings.onionRelaysViaTor) { editSettings = editSettings.copy(onionRelaysViaTor = it) }
                ToggleRow("DM relays via Tor", editSettings.dmRelaysViaTor) { editSettings = editSettings.copy(dmRelaysViaTor = it) }
                ToggleRow("Trusted relays via Tor", editSettings.trustedRelaysViaTor) { editSettings = editSettings.copy(trustedRelaysViaTor = it) }
                ToggleRow("New/unknown relays via Tor", editSettings.newRelaysViaTor) { editSettings = editSettings.copy(newRelaysViaTor = it) }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Content Routing
                Text("Content Routing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ToggleRow("URL previews via Tor", editSettings.urlPreviewsViaTor) { editSettings = editSettings.copy(urlPreviewsViaTor = it) }
                ToggleRow("Profile pictures via Tor", editSettings.profilePicsViaTor) { editSettings = editSettings.copy(profilePicsViaTor = it) }
                ToggleRow("Images via Tor", editSettings.imagesViaTor) { editSettings = editSettings.copy(imagesViaTor = it) }
                ToggleRow("Videos via Tor", editSettings.videosViaTor) { editSettings = editSettings.copy(videosViaTor = it) }
                ToggleRow("NIP-05 verifications via Tor", editSettings.nip05VerificationsViaTor) { editSettings = editSettings.copy(nip05VerificationsViaTor = it) }
                ToggleRow("Money operations via Tor", editSettings.moneyOperationsViaTor) { editSettings = editSettings.copy(moneyOperationsViaTor = it) }
                ToggleRow("Media uploads via Tor", editSettings.mediaUploadsViaTor) { editSettings = editSettings.copy(mediaUploadsViaTor = it) }

                Spacer(Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onSettingsChanged(editSettings)
                        onDismiss()
                    }) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    label: String,
    presetType: TorPresetType,
    currentPreset: TorPresetType,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(
            selected = currentPreset == presetType,
            onClick = onClick,
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
