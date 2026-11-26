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
package com.vitorpamplona.amethyst.commons.ui.playback.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.call_settings_video_quality
import com.vitorpamplona.amethyst.commons.resources.video_quality_auto
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size50Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToLong

@Composable
fun VideoQualityButton(
    player: AmethystPlayer,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    var tracks by remember(player) { mutableStateOf(player.videoTracks) }
    var openDialog by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        tracks = player.videoTracks
        val listener =
            object : AmethystPlayer.Listener {
                override fun onTracksChanged() {
                    tracks = player.videoTracks
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val videoGroup = tracks
    if (videoGroup.size <= 1) return

    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier = PinBottomIconSize) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.7f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                onClick = { openDialog = true },
                modifier = Size50Modifier,
            ) {
                Icon(
                    symbol = MaterialSymbols.Settings,
                    contentDescription = stringRes(id = Res.string.call_settings_video_quality),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Size20Modifier,
                )
            }
        }
    }

    if (openDialog) {
        VideoQualityPopup(
            player = player,
            videoGroup = videoGroup,
            onDismiss = { openDialog = false },
        )
    }
}

@Composable
fun VideoQualityPopup(
    player: AmethystPlayer,
    videoGroup: List<VideoTrack>,
    onDismiss: () -> Unit,
) {
    // Scope videoSize tracking to the open popup: HLS ABR fires onVideoSizeChanged on every
    // rung switch, and we don't want to pay recomposition cost on cards whose menu isn't open.
    var videoSize by remember(player) { mutableStateOf(player.videoSize) }
    DisposableEffect(player) {
        val listener =
            object : AmethystPlayer.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoSize = size
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val currentShortSide = minOf(videoSize.width, videoSize.height).takeIf { it > 0 }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        VideoQualityChoices(
            videoGroup = videoGroup,
            currentShortSide = currentShortSide,
            isAuto = !player.hasVideoTrackOverride,
            onSelectAuto = {
                if (player.hasVideoTrackOverride) player.clearVideoTrackOverride()
                onDismiss()
            },
            onSelectTrack = { track ->
                player.selectVideoTrack(track)
                onDismiss()
            },
        )
    }
}

@Composable
private fun VideoQualityChoices(
    videoGroup: List<VideoTrack>,
    currentShortSide: Int?,
    isAuto: Boolean,
    onSelectAuto: () -> Unit,
    onSelectTrack: (VideoTrack) -> Unit,
) {
    val baseColors = ButtonDefaults.textButtonColors()
    val contentColor = MaterialTheme.colorScheme.onBackground
    val colors =
        remember(baseColors, contentColor) {
            baseColors.copy(contentColor = contentColor)
        }

    val choices: ImmutableList<QualityChoice> = remember(videoGroup) { buildQualityChoices(videoGroup) }

    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(colors = colors, onClick = onSelectAuto) {
            val suffix = currentShortSide?.let { " (${it}p)" } ?: ""
            Text(
                stringRes(Res.string.video_quality_auto) + suffix,
                fontWeight = if (isAuto) FontWeight(1000) else FontWeight(400),
            )
        }

        choices.forEach { choice ->
            TextButton(colors = colors, onClick = { onSelectTrack(choice.track) }) {
                Text(
                    "${choice.shortSide}p  ${formatBitrate(choice.bitrate)}",
                    fontWeight = if (!isAuto && currentShortSide == choice.shortSide) FontWeight(1000) else FontWeight(400),
                )
            }
        }
    }
}

// shortSide = min(width, height). Matches the streaming convention that "360p" means
// 360 pixels on the short side regardless of orientation, so portrait videos get sensible
// labels instead of "640p / 960p / 1280p" for the same ladder rungs.
private data class QualityChoice(
    val track: VideoTrack,
    val shortSide: Int,
    val bitrate: Int,
)

private fun buildQualityChoices(group: List<VideoTrack>): ImmutableList<QualityChoice> {
    val choices = mutableListOf<QualityChoice>()
    for (format in group) {
        if (!format.supported) continue
        val shortSide = minOf(format.width, format.height)
        if (shortSide > 0) {
            choices.add(QualityChoice(format, shortSide, format.bitrate))
        }
    }
    return choices.sortedByDescending { it.shortSide }.toImmutableList()
}

// Always a dot for the decimal mark, as `String.format(Locale.US, …)` printed it, rounded half up.
private fun formatBitrate(bitrate: Int): String =
    when {
        bitrate <= 0 -> ""
        bitrate >= 1_000_000 -> {
            val tenths = (bitrate / 100_000.0).roundToLong()
            "${tenths / 10}.${tenths % 10} Mbps"
        }
        else -> "${(bitrate / 1_000.0).roundToLong()} kbps"
    }
