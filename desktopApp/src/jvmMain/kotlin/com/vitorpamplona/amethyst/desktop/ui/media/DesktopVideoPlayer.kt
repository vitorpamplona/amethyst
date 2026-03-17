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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.service.media.VideoThumbnailCache
import com.vitorpamplona.amethyst.desktop.service.media.VlcjPlayerPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.UUID
import org.jetbrains.skia.Image as SkiaImage

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
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var vlcAvailable by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<EmbeddedMediaPlayer?>(null) }
    var volume by remember { mutableIntStateOf(100) }
    var isMuted by remember { mutableStateOf(false) }

    // Unique ID for single-player enforcement
    val playerId = remember { UUID.randomUUID().toString() }

    // Lazy activation — don't touch VLC until user clicks play (or autoPlay)
    var activated by remember { mutableStateOf(autoPlay) }

    // Load thumbnail when not activated
    var thumbnail by remember(url) { mutableStateOf(VideoThumbnailCache.getCached(url)) }
    LaunchedEffect(url, activated) {
        if (!activated && thumbnail == null) {
            thumbnail = VideoThumbnailCache.getThumbnail(url)
        }
    }

    // Pause when another player becomes active
    val activeId by ActiveMediaManager.activeId.collectAsState()
    LaunchedEffect(activeId) {
        if (activeId != null && activeId != playerId && isPlaying) {
            player?.controls()?.pause()
        }
    }

    // Set up player off the UI thread when activated
    LaunchedEffect(url, activated) {
        if (!activated) return@LaunchedEffect
        isBuffering = true

        val acquired =
            withContext(Dispatchers.IO) {
                if (!VlcjPlayerPool.init()) return@withContext null
                VlcjPlayerPool.acquire()
            }

        if (acquired == null) {
            vlcAvailable = false
            isBuffering = false
            return@LaunchedEffect
        }

        var skBitmap: Bitmap? = null
        var pixelBytes: ByteArray? = null
        var didSeek = initialSeekPosition <= 0f

        val bufferFormatCallback =
            object : BufferFormatCallback {
                override fun getBufferFormat(
                    sourceWidth: Int,
                    sourceHeight: Int,
                ): BufferFormat {
                    if (sourceHeight > 0) {
                        aspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
                    }
                    val bmp = Bitmap()
                    bmp.allocPixels(ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.PREMUL))
                    skBitmap = bmp
                    pixelBytes = ByteArray(sourceWidth * sourceHeight * 4)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
            }

        val renderCallback =
            RenderCallback { _, nativeBuffers, _ ->
                val bmp = skBitmap ?: return@RenderCallback
                val bytes = pixelBytes ?: return@RenderCallback
                val buffer = nativeBuffers[0]
                buffer.rewind()
                buffer.get(bytes)
                bmp.installPixels(bytes)
                frame = SkiaImage.makeFromBitmap(bmp).toComposeImageBitmap()
            }

        val surface = VlcjPlayerPool.createVideoSurface(bufferFormatCallback, renderCallback)
        acquired.videoSurface().set(surface)

        acquired.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    isPlaying = true
                    isBuffering = false
                    duration = mediaPlayer.status().length()
                    // Seek to initial position on first play
                    if (!didSeek) {
                        didSeek = true
                        mediaPlayer.controls().setPosition(initialSeekPosition)
                    }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    isPlaying = false
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    isPlaying = false
                    isBuffering = false
                }

                override fun buffering(
                    mediaPlayer: MediaPlayer,
                    newCache: Float,
                ) {
                    isBuffering = newCache < 100f
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
                    isBuffering = false
                    position = 0f
                    currentTime = 0L
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    isBuffering = false
                    println("VLC: playback error for $url")
                }
            },
        )

        player = acquired
        ActiveMediaManager.activate(playerId)
        acquired.media().play(url)
    }

    // Clean up player on leave or URL change
    DisposableEffect(url) {
        onDispose {
            ActiveMediaManager.deactivate(playerId)
            player?.let { p ->
                VlcjPlayerPool.release(p)
                player = null
            }
        }
    }

    // Position polling (VLCJ events sometimes miss updates)
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
        VlcNotAvailableMessage(url, modifier)
        return
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    RoundedCornerShape(8.dp),
                ),
        contentAlignment = Alignment.Center,
    ) {
        val displayBitmap = frame ?: thumbnail
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
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            position = position,
            duration = duration,
            currentTime = currentTime,
            volume = volume,
            isMuted = isMuted,
            viewMode = viewMode,
            onPlayPause = {
                val p = player
                if (p != null) {
                    if (isPlaying) {
                        p.controls().pause()
                    } else {
                        ActiveMediaManager.activate(playerId)
                        if (position <= 0f && !p.status().isPlaying) {
                            p.media().play(url)
                            isBuffering = true
                        } else {
                            p.controls().play()
                        }
                    }
                } else {
                    // First play — activate lazy init
                    activated = true
                }
            },
            onSeek = { pos ->
                player?.controls()?.setPosition(pos)
            },
            onVolumeChange = { vol ->
                volume = vol
                player?.audio()?.setVolume(vol)
            },
            onMuteToggle = {
                isMuted = !isMuted
                player?.audio()?.isMute = isMuted
            },
            onFullscreen =
                if (onFullscreen != null) {
                    { onFullscreen(position) }
                } else {
                    null
                },
            onViewModeChange = onViewModeChange,
            trailingControls = trailingControls,
        )
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
