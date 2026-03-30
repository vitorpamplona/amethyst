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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import kotlinx.coroutines.launch

enum class MediaType { AUDIO, VIDEO }

@Composable
fun NowPlayingBar(modifier: Modifier = Modifier) {
    val videoState by GlobalMediaPlayer.videoState.collectAsState()
    val audioState by GlobalMediaPlayer.audioState.collectAsState()
    val videoFrame by GlobalMediaPlayer.videoFrame.collectAsState()

    val hasVideo = videoState.url != null
    val hasAudio = audioState.url != null
    val visible = hasVideo || hasAudio

    // Show video bar if video is active, otherwise audio
    val activeState = if (hasVideo) videoState else audioState
    val activeType = if (hasVideo) MediaType.VIDEO else MediaType.AUDIO

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        if (!visible) return@AnimatedVisibility

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Mini video thumbnail or music icon
            if (activeType == MediaType.VIDEO && videoFrame != null) {
                Image(
                    bitmap = videoFrame!!,
                    contentDescription = "Video thumbnail",
                    modifier =
                        Modifier
                            .size(width = 48.dp, height = 36.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Play/pause
            IconButton(
                onClick = {
                    if (activeType == MediaType.VIDEO) {
                        GlobalMediaPlayer.toggleVideoPlayPause()
                    } else {
                        GlobalMediaPlayer.toggleAudioPlayPause()
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (activeState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (activeState.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(20.dp),
                )
            }

            // Current time
            Text(
                text = formatTime(activeState.currentTime),
                style = MaterialTheme.typography.labelSmall,
            )

            // Seek bar
            Slider(
                value = activeState.position,
                onValueChange = {
                    if (activeType == MediaType.VIDEO) {
                        GlobalMediaPlayer.seekVideo(it)
                    } else {
                        GlobalMediaPlayer.seekAudio(it)
                    }
                },
                modifier = Modifier.weight(1f),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
            )

            // Duration
            Text(
                text = formatTime(activeState.duration),
                style = MaterialTheme.typography.labelSmall,
            )

            // URL label (truncated)
            Text(
                text = activeState.url?.substringAfterLast('/')?.substringBefore('?') ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(120.dp),
            )

            // Volume / Mute
            IconButton(
                onClick = {
                    if (activeType == MediaType.VIDEO) {
                        GlobalMediaPlayer.toggleVideoMute()
                    } else {
                        GlobalMediaPlayer.toggleAudioMute()
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    if (activeState.isMuted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (activeState.isMuted) "Unmute" else "Mute",
                    modifier = Modifier.size(16.dp),
                )
            }

            Slider(
                value = activeState.volume / 100f,
                onValueChange = {
                    val vol = (it * 100).toInt()
                    if (activeType == MediaType.VIDEO) {
                        GlobalMediaPlayer.setVideoVolume(vol)
                    } else {
                        GlobalMediaPlayer.setAudioVolume(vol)
                    }
                },
                modifier = Modifier.width(80.dp),
                colors =
                    SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
            )

            // Save button
            val scope = rememberCoroutineScope()
            IconButton(
                onClick = {
                    activeState.url?.let { url ->
                        scope.launch {
                            SaveMediaAction.saveMedia(url = url)
                        }
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Save,
                    contentDescription = "Save",
                    modifier = Modifier.size(16.dp),
                )
            }

            // Fullscreen (video only)
            if (activeType == MediaType.VIDEO) {
                IconButton(
                    onClick = { GlobalMediaPlayer.toggleFullscreen() },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Close/stop
            IconButton(
                onClick = {
                    if (activeType == MediaType.VIDEO) {
                        GlobalMediaPlayer.stopVideo()
                    } else {
                        GlobalMediaPlayer.stopAudio()
                    }
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Stop",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
