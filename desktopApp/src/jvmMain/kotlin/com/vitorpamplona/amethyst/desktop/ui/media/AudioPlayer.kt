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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * Audio-only player using VLCJ with no video surface.
 * Creates its own MediaPlayerFactory with "--no-video" flag.
 */
@Composable
fun AudioPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var vlcAvailable by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        val factory =
            try {
                MediaPlayerFactory("--no-video", "--no-xlib")
            } catch (_: Exception) {
                vlcAvailable = false
                return@DisposableEffect onDispose {}
            }

        val mp = factory.mediaPlayers().newMediaPlayer()

        mp.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    isPlaying = true
                    duration = mediaPlayer.status().length()
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    isPlaying = false
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    isPlaying = false
                }

                override fun positionChanged(
                    mediaPlayer: MediaPlayer,
                    newPosition: Float,
                ) {
                    position = newPosition
                    currentTime = (newPosition * duration).toLong()
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    isPlaying = false
                    position = 0f
                    currentTime = 0L
                }
            },
        )

        player = mp

        onDispose {
            player = null
            try {
                mp.controls().stop()
                mp.release()
            } catch (_: Exception) {
                // Ignore
            }
            try {
                factory.release()
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    // Position polling
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(500)
            player?.let {
                position = it.status().position()
                currentTime = it.status().time()
            }
        }
    }

    if (!vlcAvailable) {
        Text(
            "Audio: $url (install VLC to play)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        IconButton(
            onClick = {
                player?.let { p ->
                    if (isPlaying) {
                        p.controls().pause()
                    } else {
                        if (position <= 0f && !p.status().isPlaying) {
                            p.media().play(url)
                        } else {
                            p.controls().play()
                        }
                    }
                }
            },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(20.dp),
            )
        }

        Text(
            text = formatAudioTime(currentTime),
            style = MaterialTheme.typography.labelSmall,
        )

        Slider(
            value = position,
            onValueChange = { player?.controls()?.setPosition(it) },
            modifier = Modifier.weight(1f),
        )

        Text(
            text = formatAudioTime(duration),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatAudioTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
