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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.service.media.VideoThumbnailCache
import com.vitorpamplona.amethyst.desktop.service.media.VlcjPlayerPool
import kotlinx.coroutines.delay

@Composable
fun DesktopVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    initialSeekPosition: Float = 0f,
    onFullscreen: ((Float) -> Unit)? = null,
    viewMode: ViewMode = ViewMode.DEFAULT,
    onViewModeChange: ((ViewMode) -> Unit)? = null,
    trailingControls: @Composable (() -> Unit)? = null,
) {
    // Check if this URL is the active video
    val videoState by GlobalMediaPlayer.videoState.collectAsState()
    val videoFrame by GlobalMediaPlayer.videoFrame.collectAsState()
    val isActiveVideo = videoState.url == url

    // Thumbnail for inactive videos
    var thumbnail by remember(url) { mutableStateOf(VideoThumbnailCache.getCached(url)) }
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    // Load thumbnail when not active
    LaunchedEffect(url, isActiveVideo) {
        if (!isActiveVideo && thumbnail == null) {
            for (attempt in 1..3) {
                val result = VideoThumbnailCache.getThumbnail(url)
                if (result != null) {
                    thumbnail = result
                    break
                }
                if (attempt < 3) delay(2000L * attempt)
            }
        }
    }

    // Auto-play on mount if requested
    LaunchedEffect(url, autoPlay) {
        if (autoPlay) {
            GlobalMediaPlayer.playVideo(url, initialSeekPosition)
        }
    }

    // Sync aspect ratio from global state when active
    if (isActiveVideo && videoState.aspectRatio != 16f / 9f) {
        aspectRatio = videoState.aspectRatio
    }

    if (!VlcjPlayerPool.isAvailable() && VlcjPlayerPool.init().not()) {
        VlcNotAvailableMessage(url, modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val desiredHeight = maxWidth / aspectRatio
        val constrainedHeight = if (constraints.hasBoundedHeight) minOf(desiredHeight, maxHeight) else desiredHeight

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(constrainedHeight)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(8.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val displayBitmap: ImageBitmap? = if (isActiveVideo) videoFrame ?: thumbnail else thumbnail
            displayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = "Video",
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }

            VideoControls(
                isPlaying = if (isActiveVideo) videoState.isPlaying else false,
                isBuffering = if (isActiveVideo) videoState.isBuffering else false,
                position = if (isActiveVideo) videoState.position else 0f,
                duration = if (isActiveVideo) videoState.duration else 0L,
                currentTime = if (isActiveVideo) videoState.currentTime else 0L,
                volume = if (isActiveVideo) videoState.volume else 100,
                isMuted = if (isActiveVideo) videoState.isMuted else false,
                viewMode = viewMode,
                onPlayPause = {
                    if (isActiveVideo) {
                        GlobalMediaPlayer.toggleVideoPlayPause()
                    } else {
                        GlobalMediaPlayer.playVideo(url, initialSeekPosition)
                    }
                },
                onSeek = { pos ->
                    if (isActiveVideo) {
                        GlobalMediaPlayer.seekVideo(pos)
                    }
                },
                onVolumeChange = { vol ->
                    GlobalMediaPlayer.setVideoVolume(vol)
                },
                onMuteToggle = {
                    GlobalMediaPlayer.toggleVideoMute()
                },
                onFullscreen =
                    if (onFullscreen != null) {
                        {
                            val pos = if (isActiveVideo) videoState.position else 0f
                            onFullscreen(pos)
                        }
                    } else {
                        null
                    },
                onViewModeChange = onViewModeChange,
                trailingControls = trailingControls,
            )
        }
    }
}

@Composable
private fun VlcNotAvailableMessage(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(8.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Video: $url\nInstall VLC to play videos: https://www.videolan.org/vlc/",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
