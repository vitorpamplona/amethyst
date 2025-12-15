/**
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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.AudioWaveformReadOnly
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun VoiceMessagePreview(
    voiceMetadata: AudioMeta,
    localFile: File? = null,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Initialize MediaPlayer
    DisposableEffect(voiceMetadata.url, localFile) {
        val player = createMediaPlayer(context, voiceMetadata.url, localFile)
        player?.setOnCompletionListener {
            isPlaying = false
            progress = 0f
        }
        mediaPlayer = player

        onDispose {
            // Stop playback and clean up
            try {
                player?.stop()
            } catch (e: IllegalStateException) {
                // Player might already be stopped
                Log.d("VoiceMessagePreview", "MediaPlayer stop failed (already stopped)", e)
            }
            player?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    // Update progress while playing
    LaunchedEffect(mediaPlayer, isPlaying) {
        // Capture player reference to avoid reading volatile state repeatedly
        val player = mediaPlayer
        if (player != null && isPlaying) {
            while (isActive) {
                try {
                    if (player.isPlaying) {
                        val current = player.currentPosition.toFloat()
                        val duration = player.duration.toFloat()
                        // Validate values before calculating progress
                        val newProgress =
                            if (duration > 0 && current >= 0) {
                                (current / duration).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                        // Only update if value is valid (not NaN or Infinity)
                        if (newProgress.isFinite()) {
                            progress = newProgress
                        }
                    } else {
                        // Player stopped, exit loop and let LaunchedEffect restart
                        break
                    }
                } catch (e: IllegalStateException) {
                    // Player in invalid state, stop tracking
                    Log.w("VoiceMessagePreview", "MediaPlayer in invalid state during progress tracking", e)
                    isPlaying = false
                    break
                }
                delay(100)
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                ).padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Play/Pause Button
            IconButton(
                onClick = {
                    val player = mediaPlayer
                    if (player != null) {
                        try {
                            if (isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                // Validate progress before comparison
                                if (progress.isFinite() && progress >= 1f) {
                                    player.seekTo(0)
                                    progress = 0f
                                }
                                player.start()
                                isPlaying = true
                            }
                        } catch (e: IllegalStateException) {
                            // MediaPlayer in invalid state, ignore
                            Log.w("VoiceMessagePreview", "MediaPlayer operation failed in onClick handler", e)
                            isPlaying = false
                        }
                    }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) stringRes(context, R.string.pause) else stringRes(context, R.string.play),
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
                        // Validate incoming progress value
                        if (newProgress.isFinite() && newProgress >= 0f && newProgress <= 1f) {
                            val player = mediaPlayer
                            if (player != null) {
                                try {
                                    val duration = player.duration
                                    // Only seek if duration is valid
                                    if (duration > 0) {
                                        val newPosition = (newProgress * duration).toInt()
                                        player.seekTo(newPosition)
                                        progress = newProgress
                                    }
                                } catch (e: IllegalStateException) {
                                    // MediaPlayer in invalid state, ignore
                                    Log.w("VoiceMessagePreview", "MediaPlayer seek failed in onProgressChange", e)
                                }
                            }
                        }
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
                    imageVector = Icons.Default.Close,
                    contentDescription = stringRes(context, R.string.remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun createMediaPlayer(
    context: Context,
    url: String,
    localFile: File?,
): MediaPlayer? =
    try {
        MediaPlayer().apply {
            if (localFile != null && localFile.exists()) {
                setDataSource(localFile.absolutePath)
            } else {
                setDataSource(url)
            }
            prepare()
        }
    } catch (e: Exception) {
        null
    }
