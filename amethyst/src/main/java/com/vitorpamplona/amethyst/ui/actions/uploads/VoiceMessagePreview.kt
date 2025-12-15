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
        mediaPlayer = player

        onDispose {
            player?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    // Update progress while playing
    LaunchedEffect(isPlaying) {
        if (isPlaying && mediaPlayer != null) {
            while (isActive && isPlaying) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition.toFloat()
                    val duration = player.duration.toFloat()
                    progress = if (duration > 0) current / duration else 0f
                    delay(100)
                } else {
                    isPlaying = false
                    progress = 0f
                }
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
                    mediaPlayer?.let { player ->
                        if (isPlaying) {
                            player.pause()
                            isPlaying = false
                        } else {
                            if (progress >= 1f) {
                                player.seekTo(0)
                                progress = 0f
                            }
                            player.start()
                            isPlaying = true
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
                        mediaPlayer?.let { player ->
                            val newPosition = (newProgress * player.duration).toInt()
                            player.seekTo(newPosition)
                            progress = newProgress
                        }
                    },
                )

                Text(
                    text = formatDuration(voiceMetadata.duration ?: 0),
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
            setOnCompletionListener {
                // Reset to beginning when playback completes
                seekTo(0)
            }
        }
    } catch (e: Exception) {
        null
    }

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}
