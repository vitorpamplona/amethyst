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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun VideoControls(
    isPlaying: Boolean,
    position: Float,
    duration: Long,
    currentTime: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    volume: Int = 100,
    isMuted: Boolean = false,
    onVolumeChange: ((Int) -> Unit)? = null,
    onMuteToggle: (() -> Unit)? = null,
    onFullscreen: (() -> Unit)? = null,
    viewMode: ViewMode = ViewMode.DEFAULT,
    onViewModeChange: ((ViewMode) -> Unit)? = null,
    trailingControls: @Composable (() -> Unit)? = null,
) {
    var hoveringCenter by remember { mutableStateOf(false) }
    var hoveringBottom by remember { mutableStateOf(false) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable { onPlayPause() },
    ) {
        // Center hover zone — top 70% of the video
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Enter -> hoveringCenter = true
                                    PointerEventType.Exit -> hoveringCenter = false
                                }
                            }
                        }
                    },
        )

        // Center play/buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
                strokeWidth = 3.dp,
            )
        } else if (!isPlaying) {
            // Always show play button when paused
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        } else if (hoveringCenter) {
            // Show pause button on center hover when playing
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.Center).size(64.dp),
            ) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Bottom controls — show on hover over bottom area
        AnimatedVisibility(
            visible = hoveringBottom || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when (event.type) {
                                    PointerEventType.Enter -> hoveringBottom = true
                                    PointerEventType.Exit -> hoveringBottom = false
                                }
                            }
                        }
                    },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp),
            ) {
                // Seek slider (full width, no horizontal competition)
                Slider(
                    value = position,
                    onValueChange = onSeek,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                )

                // Buttons row
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Text(
                        text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )

                    // Spacer pushes right-side controls to the end
                    Box(Modifier.weight(1f))

                    // Volume
                    if (onMuteToggle != null) {
                        IconButton(onClick = onMuteToggle, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (isMuted) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    if (onVolumeChange != null) {
                        Slider(
                            value = volume / 100f,
                            onValueChange = { onVolumeChange((it * 100).toInt()) },
                            modifier = Modifier.width(80.dp),
                            colors =
                                SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White.copy(alpha = 0.7f),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                ),
                        )
                    }

                    // Two separate toggle buttons (lightbox) or simple fullscreen (inline)
                    if (onViewModeChange != null) {
                        // Theater toggle — hidden during fullscreen
                        if (viewMode != ViewMode.FULLSCREEN) {
                            IconButton(
                                onClick = {
                                    onViewModeChange(
                                        if (viewMode == ViewMode.THEATER) ViewMode.DEFAULT else ViewMode.THEATER,
                                    )
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    if (viewMode == ViewMode.THEATER) {
                                        Icons.Default.CloseFullscreen
                                    } else {
                                        Icons.Default.OpenInFull
                                    },
                                    contentDescription =
                                        if (viewMode == ViewMode.THEATER) "Exit theater" else "Theater mode",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Fullscreen toggle — always visible
                        IconButton(
                            onClick = {
                                onViewModeChange(
                                    if (viewMode == ViewMode.FULLSCREEN) ViewMode.DEFAULT else ViewMode.FULLSCREEN,
                                )
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                if (viewMode == ViewMode.FULLSCREEN) {
                                    Icons.Default.FullscreenExit
                                } else {
                                    Icons.Default.Fullscreen
                                },
                                contentDescription =
                                    if (viewMode == ViewMode.FULLSCREEN) "Exit fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else if (onFullscreen != null) {
                        IconButton(onClick = onFullscreen, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Trailing controls (e.g. more-options menu)
                    trailingControls?.invoke()
                }
            }
        }
    }
}

internal fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
