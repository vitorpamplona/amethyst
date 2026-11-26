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
package com.vitorpamplona.amethyst.commons.ui.actions.uploads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.re_record
import com.vitorpamplona.amethyst.commons.resources.record_a_message
import com.vitorpamplona.amethyst.commons.resources.recording_indicator_description
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.ui.components.AudioWaveformReadOnly
import com.vitorpamplona.amethyst.commons.ui.note.platform.MAX_VOICE_RECORD_SECONDS
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.LocalPlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackState
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.formatSecondsToTime
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okio.Path

/**
 * Plays back a voice message before it is sent, from [localFile] only (there is nothing to play
 * without it); [voiceMetadata] gives its duration and waveform. [onRemove] drops it and
 * [onReRecord], where given, replaces it with a new recording.
 *
 * The clip plays through the platform's local player
 * ([com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackPlatform.rememberLocalAudioPlayer]).
 */
@Composable
fun VoiceMessagePreview(
    voiceMetadata: AudioMeta,
    localFile: Path? = null,
    onRemove: () -> Unit,
    onReRecord: ((RecordingResult) -> Unit)? = null,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Plays only the recording on the device, never a URL: every caller previews a recording that
    // has not been uploaded yet, so there is always a local file while there is something to play.
    val player = LocalPlaybackPlatform.current.rememberLocalAudioPlayer(localFile)

    WatchPlaybackEnd(
        player = player,
        onCompletion = {
            isPlaying = false
            progress = 0f
        },
        onRelease = { isPlaying = false },
    )

    TrackPlaybackProgress(
        player = player,
        isPlaying = isPlaying,
        onProgressUpdate = { progress = it },
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Play/Pause Button
                IconButton(
                    onClick = {
                        handlePlayPauseClick(
                            player = player,
                            isPlaying = isPlaying,
                            progress = progress,
                            onProgressReset = { progress = 0f },
                            onPlayingChanged = { isPlaying = it },
                        )
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
                        contentDescription = if (isPlaying) stringRes(Res.string.pause) else stringRes(Res.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Waveform and Duration
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AudioWaveformReadOnly(
                        amplitudes = voiceMetadata.waveform ?: emptyList(),
                        progress = progress,
                        waveformBrush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)),
                        progressBrush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                        onProgressChange = { newProgress ->
                            handleWaveformScrub(
                                newProgress = newProgress,
                                player = player,
                                onProgressChanged = { progress = it },
                            )
                        },
                    )

                    Text(
                        text = formatSecondsToTime(voiceMetadata.duration ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Remove Button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringRes(Res.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (onReRecord != null) {
                Spacer(modifier = Modifier.size(8.dp))
                ReRecordButton(
                    isUploading = isUploading,
                    isPlaying = isPlaying,
                    onRecordTaken = onReRecord,
                )
            }
        }
    }
}

@Composable
private fun ReRecordButton(
    isUploading: Boolean,
    isPlaying: Boolean,
    onRecordTaken: (RecordingResult) -> Unit,
) {
    if (isUploading || isPlaying) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Mic,
                contentDescription = stringRes(id = Res.string.record_a_message),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringRes(id = Res.string.re_record),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    RecordAudioBox(
        modifier = Modifier,
        onRecordTaken = onRecordTaken,
        maxDurationSeconds = MAX_VOICE_RECORD_SECONDS,
    ) { isRecording, elapsedSeconds, _ ->
        val contentColor =
            if (isRecording) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        val icon =
            if (isRecording) {
                MaterialSymbols.Stop
            } else {
                MaterialSymbols.Mic
            }
        val label =
            if (isRecording) {
                formatSecondsToTime(elapsedSeconds)
            } else {
                stringRes(id = Res.string.re_record)
            }
        val iconDescription =
            if (isRecording) {
                stringRes(id = Res.string.recording_indicator_description)
            } else {
                stringRes(id = Res.string.record_a_message)
            }
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                symbol = icon,
                contentDescription = iconDescription,
                tint = contentColor,
            )
            Text(
                text = label,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun WatchPlaybackEnd(
    player: AmethystPlayer?,
    onCompletion: () -> Unit,
    onRelease: () -> Unit,
) {
    DisposableEffect(player) {
        val listener =
            object : AmethystPlayer.Listener {
                override fun onPlaybackStateChanged(state: PlaybackState) {
                    if (state == PlaybackState.ENDED) onCompletion()
                }
            }
        player?.addListener(listener)

        onDispose {
            player?.removeListener(listener)
            onRelease()
        }
    }
}

@Composable
private fun TrackPlaybackProgress(
    player: AmethystPlayer?,
    isPlaying: Boolean,
    onProgressUpdate: (Float) -> Unit,
) {
    LaunchedEffect(player, isPlaying) {
        val current = player ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect

        // Runs until the end (or a pause) flips isPlaying: a player still loading the file reports
        // that it is not playing yet, and stopping on that would freeze the bar at the start.
        while (isActive) {
            calculateProgress(current.currentPosition, current.duration)?.let { onProgressUpdate(it) }
            delay(100)
        }
    }
}

/** How far [currentMs] is into [durationMs], from 0 to 1; 0 while the duration is not known yet. */
internal fun calculateProgress(
    currentMs: Long,
    durationMs: Long,
): Float? {
    val progress =
        if (durationMs > 0 && currentMs >= 0) {
            (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    return progress.takeIf { it.isFinite() }
}

/** Where a scrub to [progress] lands in a clip of [durationMs]; null when it cannot seek. */
internal fun scrubPositionMs(
    progress: Float,
    durationMs: Long,
): Long? {
    if (!progress.isFinite() || progress < 0f || progress > 1f) return null
    if (durationMs <= 0) return null
    return (progress * durationMs).toLong()
}

private fun handlePlayPauseClick(
    player: AmethystPlayer?,
    isPlaying: Boolean,
    progress: Float,
    onProgressReset: () -> Unit,
    onPlayingChanged: (Boolean) -> Unit,
) {
    val current = player ?: return
    if (isPlaying) {
        current.pause()
        onPlayingChanged(false)
        return
    }
    // A finished clip starts again from the top.
    if (current.playbackState == PlaybackState.ENDED || (progress.isFinite() && progress >= 1f)) {
        current.seekTo(0)
        onProgressReset()
    }
    current.play()
    onPlayingChanged(true)
}

private fun handleWaveformScrub(
    newProgress: Float,
    player: AmethystPlayer?,
    onProgressChanged: (Float) -> Unit,
) {
    val current = player ?: return
    val position = scrubPositionMs(newProgress, current.duration) ?: return
    current.seekTo(position)
    onProgressChanged(newProgress)
}
