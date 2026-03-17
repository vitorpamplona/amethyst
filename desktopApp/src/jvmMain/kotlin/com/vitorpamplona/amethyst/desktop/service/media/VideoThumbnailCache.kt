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
package com.vitorpamplona.amethyst.desktop.service.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.jetbrains.skia.Image as SkiaImage

object VideoThumbnailCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val pending = ConcurrentHashMap<String, Boolean>()

    fun getCached(url: String): ImageBitmap? = cache[url]

    suspend fun getThumbnail(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        if (pending.putIfAbsent(url, true) != null) return null

        return withContext(Dispatchers.IO) {
            try {
                extractFirstFrame(url)?.also { cache[url] = it }
            } finally {
                pending.remove(url)
            }
        }
    }

    private fun extractFirstFrame(url: String): ImageBitmap? {
        if (!VlcjPlayerPool.init()) return null
        val player = VlcjPlayerPool.acquire() ?: return null

        var result: ImageBitmap? = null
        val latch = CountDownLatch(1)
        var aspectRatio = 16f / 9f

        val bufferFormatCallback =
            object : BufferFormatCallback {
                override fun getBufferFormat(
                    sourceWidth: Int,
                    sourceHeight: Int,
                ): uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat {
                    if (sourceHeight > 0) {
                        aspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
                    }
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
            }

        val renderCallback =
            RenderCallback { _, nativeBuffers, bufferFormat ->
                if (result != null) return@RenderCallback
                try {
                    val w = bufferFormat.width
                    val h = bufferFormat.height
                    val bmp = Bitmap()
                    bmp.allocPixels(ImageInfo.makeN32(w, h, ColorAlphaType.PREMUL))
                    val bytes = ByteArray(w * h * 4)
                    val buffer = nativeBuffers[0]
                    buffer.rewind()
                    buffer.get(bytes)
                    bmp.installPixels(bytes)
                    result = SkiaImage.makeFromBitmap(bmp).toComposeImageBitmap()
                    latch.countDown()
                } catch (_: Exception) {
                    latch.countDown()
                }
            }

        val surface = VlcjPlayerPool.createVideoSurface(bufferFormatCallback, renderCallback)
        if (surface == null) {
            VlcjPlayerPool.release(player)
            return null
        }

        player.videoSurface().set(surface)
        player.audio().setVolume(0)
        player.audio().isMute = true

        player.events().addMediaPlayerEventListener(
            object : MediaPlayerEventAdapter() {
                override fun error(mediaPlayer: MediaPlayer) {
                    latch.countDown()
                }
            },
        )

        player.media().play(url)

        // Wait up to 5 seconds for first frame
        latch.await(5, TimeUnit.SECONDS)

        VlcjPlayerPool.release(player)
        return result
    }
}
