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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.desktop.service.media.VlcjPlayerPool
import kotlinx.coroutines.delay
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
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun DesktopVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var vlcAvailable by remember { mutableStateOf(true) }
    var player by remember { mutableStateOf<EmbeddedMediaPlayer?>(null) }

    // Acquire player
    DisposableEffect(url) {
        if (!VlcjPlayerPool.init()) {
            vlcAvailable = false
            return@DisposableEffect onDispose {}
        }

        val acquired = VlcjPlayerPool.acquire()
        if (acquired == null) {
            vlcAvailable = false
            return@DisposableEffect onDispose {}
        }

        // Skia bitmap for DirectRendering — pre-allocated to avoid per-frame GC
        var skBitmap: Bitmap? = null
        var pixelBytes: ByteArray? = null

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
                    bmp.allocPixels(
                        ImageInfo.makeN32(sourceWidth, sourceHeight, ColorAlphaType.PREMUL),
                    )
                    skBitmap = bmp
                    pixelBytes = ByteArray(sourceWidth * sourceHeight * 4)
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {
                    // No-op: we copy from the buffer in render callback
                }
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

        val surface =
            VlcjPlayerPool.createVideoSurface(
                bufferFormatCallback,
                renderCallback,
            )

        acquired.videoSurface().set(surface)

        val listener =
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
            }

        acquired.events().addMediaPlayerEventListener(listener)

        player = acquired

        if (autoPlay) {
            acquired.media().play(url)
        } else {
            acquired.media().prepare(url)
        }

        onDispose {
            player = null
            acquired.events().removeMediaPlayerEventListener(listener)
            VlcjPlayerPool.release(acquired)
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
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        frame?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "Video",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        VideoControls(
            isPlaying = isPlaying,
            position = position,
            duration = duration,
            currentTime = currentTime,
            onPlayPause = {
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
            onSeek = { pos ->
                player?.controls()?.setPosition(pos)
            },
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
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
