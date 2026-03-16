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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

@Composable
fun VideoControls(
    isPlaying: Boolean,
    position: Float,
    duration: Long,
    currentTime: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showControls by remember { mutableStateOf(true) }
    var hovering by remember { mutableStateOf(false) }

    // Auto-hide controls after 2s when playing
    LaunchedEffect(isPlaying, hovering) {
        if (isPlaying && !hovering) {
            delay(2000)
            showControls = false
        } else {
            showControls = true
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clickable { onPlayPause() }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Enter -> {
                                    hovering = true
                                    showControls = true
                                }

                                PointerEventType.Exit -> {
                                    hovering = false
                                }
                            }
                        }
                    }
                },
    ) {
        // Center play button when paused
        if (!isPlaying) {
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
        }

        // Bottom controls bar
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    text = formatTime(currentTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )

                Slider(
                    value = position,
                    onValueChange = onSeek,
                    modifier = Modifier.weight(1f),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                )

                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
