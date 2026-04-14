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
package com.vitorpamplona.amethyst.service.playback.composable.controls

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

/**
 * Represents a quality option in the picker.
 */
sealed class QualityOption {
    data object Auto : QualityOption()

    data class Specific(
        val trackIndex: Int,
        val height: Int,
        val bitrate: Int,
        val codecs: String?,
    ) : QualityOption()
}

/**
 * Modal bottom sheet for selecting video quality (rendition).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQualitySheet(
    player: Player,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tracks by remember { mutableStateOf(player.currentTracks) }
    var isAutoSelected by remember { mutableStateOf(!hasVideoOverride(player)) }
    var currentlyPlayingHeight by remember { mutableStateOf(getCurrentPlayingHeight(player)) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                    currentlyPlayingHeight = getCurrentPlayingHeight(player)
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val videoGroup = getVideoTrackGroup(tracks)
    if (videoGroup == null) {
        onDismiss()
        return
    }

    val options = buildQualityOptions(videoGroup)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Quality",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))

            // Auto option
            QualityRow(
                label = "Auto",
                sublabel = currentlyPlayingHeight?.let { "${it}p" },
                isSelected = isAutoSelected,
                onClick = {
                    clearVideoOverride(player)
                    isAutoSelected = true
                    onDismiss()
                },
            )

            // Specific quality options
            options.forEach { option ->
                if (option is QualityOption.Specific) {
                    QualityRow(
                        label = "${option.height}p",
                        sublabel = formatBitrate(option.bitrate) + (option.codecs?.let { "  $it" } ?: ""),
                        isSelected = !isAutoSelected && currentlyPlayingHeight == option.height,
                        onClick = {
                            selectVideoTrack(player, videoGroup, option.trackIndex)
                            isAutoSelected = false
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    sublabel: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        } else {
            Spacer(Modifier.size(24.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildQualityOptions(group: Tracks.Group): List<QualityOption> {
    val options = mutableListOf<QualityOption>()

    for (i in 0 until group.length) {
        val format = group.getTrackFormat(i)
        if (format.height > 0) {
            options.add(
                QualityOption.Specific(
                    trackIndex = i,
                    height = format.height,
                    bitrate = format.bitrate,
                    codecs = format.codecs,
                ),
            )
        }
    }

    // Sort by height descending
    return options.sortedByDescending { (it as QualityOption.Specific).height }
}

private fun formatBitrate(bitrate: Int): String =
    if (bitrate > 0) {
        val mbps = bitrate / 1_000_000.0
        if (mbps >= 1.0) {
            String.format("%.1f Mbps", mbps)
        } else {
            String.format("%.0f kbps", bitrate / 1000.0)
        }
    } else {
        ""
    }

private fun hasVideoOverride(player: Player): Boolean =
    player.trackSelectionParameters.overrides.any { (key, _) ->
        key.type == C.TRACK_TYPE_VIDEO
    }

private fun getCurrentPlayingHeight(player: Player): Int? {
    val videoGroup = getVideoTrackGroup(player.currentTracks) ?: return null
    for (i in 0 until videoGroup.length) {
        if (videoGroup.isTrackSelected(i)) {
            return videoGroup.getTrackFormat(i).height
        }
    }
    return null
}

private fun clearVideoOverride(player: Player) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
}

private fun selectVideoTrack(
    player: Player,
    group: Tracks.Group,
    trackIndex: Int,
) {
    player.trackSelectionParameters =
        player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(
                TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
            ).build()
}
