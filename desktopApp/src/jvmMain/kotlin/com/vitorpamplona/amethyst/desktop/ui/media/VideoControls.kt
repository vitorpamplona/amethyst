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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_download_for_offline
import com.vitorpamplona.amethyst.commons.resources.live_stream_live_tag
import com.vitorpamplona.amethyst.commons.resources.mute_button
import com.vitorpamplona.amethyst.commons.resources.muted_button
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.skip_back
import com.vitorpamplona.amethyst.commons.resources.skip_forward
import com.vitorpamplona.amethyst.commons.resources.video_player_exit_fullscreen
import com.vitorpamplona.amethyst.commons.resources.video_player_settings_action_fullscreen
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlinx.coroutines.delay

private const val SKIP_SECONDS = 10
private const val SKIP_MILLIS = SKIP_SECONDS * 1000L

/** How long the controls stay up once playing with no mouse movement over the video. */
private const val AUTO_HIDE_MILLIS = 3000L

/** How long the mute button shows on its own when a video starts, as Android does. */
private const val MUTE_HINT_MILLIS = 2000L

private val SpeedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private val TopGradient = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Black.copy(alpha = 0.3f), Color.Transparent))
private val BottomGradient = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.7f)))

/**
 * The video controls, laid out as Android's player lays them out: the full-screen, save and mute
 * buttons along the top right, skip back, play/pause and skip forward in the middle, and the
 * progress bar over the time and the speed along the bottom, all over a gradient.
 *
 * They show while the video is not playing, and once it plays they hide [AUTO_HIDE_MILLIS] after
 * the mouse last moved over it. A click shows or hides them; a double click skips
 * [SKIP_SECONDS] back on the left half and forward on the right.
 */
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
    isLive: Boolean = false,
    onSkip: ((Long) -> Unit)? = null,
    speed: Float = 1f,
    onSpeedChange: ((Float) -> Unit)? = null,
    onSave: (() -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    // When the mouse last moved over the video. Kept out of snapshot state so a mouse move does
    // not recompose the controls; the hide loop below reads it.
    val lastMoveMillis = remember { longArrayOf(0L) }
    val widthPx = remember { intArrayOf(0) }

    LaunchedEffect(isPlaying, shown) {
        if (!isPlaying || !shown) return@LaunchedEffect
        lastMoveMillis[0] = maxOf(lastMoveMillis[0], System.currentTimeMillis())
        while (true) {
            val wait = lastMoveMillis[0] + AUTO_HIDE_MILLIS - System.currentTimeMillis()
            if (wait <= 0) break
            delay(wait)
        }
        shown = false
    }

    var muteHint by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(MUTE_HINT_MILLIS)
        muteHint = false
    }

    val visible = shown || !isPlaying
    val canSkip = onSkip != null && !isLive
    // The double-tap gesture starts once per canSkip; it must call the latest onSkip.
    val currentOnSkip by rememberUpdatedState(onSkip)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { widthPx[0] = it.width }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
                                lastMoveMillis[0] = System.currentTimeMillis()
                                shown = true
                            }
                        }
                    }
                }.pointerInput(canSkip) {
                    detectTapGestures(
                        onTap = { shown = !shown },
                        onDoubleTap = { offset ->
                            if (canSkip) currentOnSkip?.invoke(if (offset.x < widthPx[0] / 2) -SKIP_MILLIS else SKIP_MILLIS)
                        },
                    )
                },
    ) {
        FadingBox(visible, Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().height(80.dp).background(TopGradient))
        }
        FadingBox(visible, Modifier.align(Alignment.BottomCenter)) {
            Box(Modifier.fillMaxWidth().height(120.dp).background(BottomGradient))
        }

        Row(Modifier.align(Alignment.TopEnd).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            val fullscreenAction =
                when {
                    onViewModeChange != null -> {
                        { onViewModeChange(if (viewMode == ViewMode.FULLSCREEN) ViewMode.DEFAULT else ViewMode.FULLSCREEN) }
                    }

                    else -> {
                        onFullscreen
                    }
                }
            if (fullscreenAction != null) {
                FadingBox(visible) {
                    if (viewMode == ViewMode.FULLSCREEN) {
                        CircleButton(MaterialSymbols.FullscreenExit, stringRes(Res.string.video_player_exit_fullscreen), fullscreenAction)
                    } else {
                        CircleButton(MaterialSymbols.Fullscreen, stringRes(Res.string.video_player_settings_action_fullscreen), fullscreenAction)
                    }
                }
            }
            if (onSave != null && !isLive) {
                FadingBox(visible) {
                    CircleButton(MaterialSymbols.SaveAlt, stringRes(Res.string.accessibility_download_for_offline), onSave)
                }
            }
            if (onMuteToggle != null) {
                FadingBox(visible || muteHint) {
                    if (isMuted) {
                        CircleButton(MaterialSymbols.AutoMirrored.VolumeOff, stringRes(Res.string.muted_button), onMuteToggle)
                    } else {
                        CircleButton(MaterialSymbols.AutoMirrored.VolumeUp, stringRes(Res.string.mute_button), onMuteToggle)
                    }
                }
            }
            if (trailingControls != null) {
                FadingBox(visible) { trailingControls() }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canSkip) {
                FadingBox(visible) {
                    SkipButton(MaterialSymbols.Replay10, stringRes(Res.string.skip_back, SKIP_SECONDS)) { onSkip?.invoke(-SKIP_MILLIS) }
                }
            }
            if (isBuffering) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color.White, strokeWidth = 3.dp)
                }
            } else {
                FadingBox(visible) {
                    PlayPauseButton(isPlaying, onPlayPause)
                }
            }
            if (canSkip) {
                FadingBox(visible) {
                    SkipButton(MaterialSymbols.Forward10, stringRes(Res.string.skip_forward, SKIP_SECONDS)) { onSkip?.invoke(SKIP_MILLIS) }
                }
            }
        }

        FadingBox(visible, Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth()) {
                if (!isLive) {
                    // Seeking is costly, so a drag seeks once, where it is let go.
                    ThinSlider(position, onSeek, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isLive) {
                        Box(Modifier.size(8.dp).background(Color(0xFFD32F2F), shape = CircleShape))
                        Text(
                            text = stringRes(Res.string.live_stream_live_tag),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFFF5252),
                        )
                        Text(text = formatTime(currentTime), style = MaterialTheme.typography.labelLarge, color = Color.White)
                    } else {
                        Text(
                            text = "${formatTime(currentTime)} / ${formatTime(duration)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    if (onVolumeChange != null) {
                        ThinSlider(
                            value = volume / 100f,
                            onValueChange = { onVolumeChange((it * 100).toInt()) },
                            modifier = Modifier.width(80.dp),
                            changesWhileDragging = true,
                        )
                    }

                    if (onSpeedChange != null && !isLive) {
                        SpeedButton(speed, onSpeedChange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FadingBox(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}

/** A top-bar button: the icon over a circle of the theme's background, as on Android. */
@Composable
private fun CircleButton(
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(CircleShape).fillMaxSize(0.7f).background(MaterialTheme.colorScheme.background))
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(symbol, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(CircleShape).fillMaxSize(0.6f).background(MaterialTheme.colorScheme.background))
        IconButton(onClick = onClick, modifier = Modifier.size(80.dp)) {
            // The circle is the theme's background, so the icon takes its matching color, not the
            // surrounding content color.
            val tint = MaterialTheme.colorScheme.onBackground
            if (isPlaying) {
                Icon(MaterialSymbols.Pause, contentDescription = stringRes(Res.string.pause), tint = tint, modifier = Modifier.size(40.dp))
            } else {
                Icon(MaterialSymbols.PlayArrow, contentDescription = stringRes(Res.string.play), tint = tint, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun SkipButton(
    symbol: MaterialSymbol,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(symbol, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

/**
 * Android's thin white seek bar, used for the volume too: click or drag anywhere on it. A drag
 * reports where it is let go, or every step of the way with [changesWhileDragging].
 */
@Composable
private fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    changesWhileDragging: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    // The gestures below start once and outlive recompositions: they must call the latest values.
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentChangesWhileDragging by rememberUpdatedState(changesWhileDragging)

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset -> currentOnValueChange((offset.x / size.width).coerceIn(0f, 1f)) }
            }.pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragPosition = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (currentChangesWhileDragging) currentOnValueChange(dragPosition)
                    },
                    onDragEnd = {
                        currentOnValueChange(dragPosition)
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                )
            }.padding(vertical = 8.dp)
            .height(4.dp),
    ) {
        val shown = if (dragging) dragPosition else value.coerceIn(0f, 1f)
        val x = shown * size.width
        drawRect(Color.White.copy(alpha = 0.3f), size = size)
        drawRect(Color.White, size = Size(x, size.height))
        drawCircle(Color.White, radius = size.height * if (dragging) 3f else 2f, center = Offset(x, size.height / 2f))
    }
}

@Composable
private fun SpeedButton(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            text = speedLabel(speed),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable { open = true }.padding(4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (choice in SpeedChoices) {
                DropdownMenuItem(
                    text = {
                        Text(speedLabel(choice), fontWeight = if (choice == speed) FontWeight.Bold else FontWeight.Normal)
                    },
                    onClick = {
                        onSpeedChange(choice)
                        open = false
                    },
                )
            }
        }
    }
}

/** 1.0x, 1.5x, 0.75x: one decimal unless the speed needs two. */
private fun speedLabel(speed: Float): String = (if ((speed * 10f) % 1f == 0f) "%.1fx" else "%.2fx").format(speed)

internal fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
