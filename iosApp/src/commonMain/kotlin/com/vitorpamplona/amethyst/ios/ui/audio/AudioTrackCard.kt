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
package com.vitorpamplona.amethyst.ios.ui.audio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.quartz.experimental.audio.track.AudioTrackEvent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.Foundation.NSURL

/**
 * Display data for an audio track (kind 31337, zapstr.live).
 */
data class AudioTrackDisplayData(
    val noteId: String,
    val title: String?,
    val artist: String?,
    val coverUrl: String?,
    val mediaUrl: String?,
    val type: String?,
    val price: String?,
)

/**
 * Converts an AudioTrackEvent to display data.
 */
fun AudioTrackEvent.toAudioTrackDisplayData(): AudioTrackDisplayData =
    AudioTrackDisplayData(
        noteId = id,
        title = dTag().ifBlank { null },
        artist = content.ifBlank { null },
        coverUrl = cover(),
        mediaUrl = media(),
        type = type(),
        price = price(),
    )

/**
 * Card displaying an audio track with play/pause and cover art.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun AudioTrackCard(
    data: AudioTrackDisplayData,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }

    val player =
        remember(data.mediaUrl) {
            data.mediaUrl?.let { url ->
                NSURL.URLWithString(url)?.let { nsUrl ->
                    AVPlayer(playerItem = AVPlayerItem(uRL = nsUrl))
                }
            }
        }

    DisposableEffect(player) {
        onDispose {
            player?.pause()
            player?.replaceCurrentItemWithPlayerItem(null)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cover art
            if (data.coverUrl != null) {
                AsyncImage(
                    model = data.coverUrl,
                    contentDescription = "Cover art",
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }

            // Play button
            IconButton(
                onClick = {
                    if (player != null) {
                        if (isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier.size(40.dp),
                enabled = player != null,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.title ?: "Untitled Track",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                data.artist?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Row {
                    data.type?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    data.price?.let {
                        Text(
                            " · $it sats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
