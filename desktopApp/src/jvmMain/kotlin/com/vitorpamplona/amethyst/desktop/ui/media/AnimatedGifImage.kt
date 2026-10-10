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
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

private const val MAX_BITMAP_MEMORY = 64L * 1024 * 1024 // 64MB per GIF
private const val MIN_FRAME_DURATION_MS = 20

private val gifHttpClient get() = DesktopHttpClient.currentClient()

private class GifFrames(
    val frames: List<ImageBitmap>,
    val durations: List<Int>,
    val bytes: Long,
)

// Decoded GIFs kept for the next time they show (scrolling back, reopening a thread), so they
// neither download nor decode again. Bounded by the frames' pixel memory.
private const val FRAME_CACHE_BYTES = 128L * 1024 * 1024

private object GifFrameCache {
    private val entries = LinkedHashMap<String, GifFrames>(16, 0.75f, true)
    private var total = 0L

    @Synchronized
    fun get(url: String): GifFrames? = entries[url]

    @Synchronized
    fun put(
        url: String,
        frames: GifFrames,
    ) {
        if (frames.bytes > FRAME_CACHE_BYTES) return
        entries.put(url, frames)?.let { total -= it.bytes }
        total += frames.bytes
        val oldest = entries.entries.iterator()
        while (total > FRAME_CACHE_BYTES && oldest.hasNext()) {
            val evicted = oldest.next()
            if (evicted.key == url) continue
            total -= evicted.value.bytes
            oldest.remove()
        }
    }
}

@Composable
fun AnimatedGifImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var gifFrames by remember(url) { mutableStateOf(GifFrameCache.get(url)) }
    var currentFrame by remember(url) { mutableIntStateOf(0) }
    var loadFailed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        currentFrame = 0
        loadFailed = false
        if (gifFrames != null) return@LaunchedEffect
        gifFrames = withContext(Dispatchers.IO) { decodeGifFrames(url)?.also { GifFrameCache.put(url, it) } }
        if (gifFrames == null) loadFailed = true
    }

    // Reset frame index when frames change
    DisposableEffect(url) {
        onDispose { currentFrame = 0 }
    }

    val data = gifFrames
    when {
        data != null && data.frames.size > 1 -> {
            val safeFrame = currentFrame.coerceIn(0, data.frames.size - 1)

            LaunchedEffect(data) {
                while (isActive) {
                    val frameIdx = currentFrame.coerceIn(0, data.frames.size - 1)
                    val duration = data.durations[frameIdx].coerceAtLeast(MIN_FRAME_DURATION_MS)
                    delay(duration.toLong())
                    currentFrame = (currentFrame + 1) % data.frames.size
                }
            }

            Image(
                bitmap = data.frames[safeFrame],
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
        }

        data != null && data.frames.isNotEmpty() -> {
            Image(
                bitmap = data.frames[0],
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
        }

        loadFailed -> {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
            )
        }

        else -> {
            Box(modifier)
        }
    }
}

private suspend fun decodeGifFrames(url: String): GifFrames? =
    try {
        val request = Request.Builder().url(url).build()
        val response = gifHttpClient.newCall(request).executeAsync()
        val bytes = response.body.bytes()

        val skData = Data.makeFromBytes(bytes)
        val codec = Codec.makeFromData(skData)
        val frameCount = codec.frameCount
        if (frameCount <= 0) return null
        // So the card reserves the GIF's shape the next time, before it decodes again.
        MediaAspectRatioCache.add(url, codec.width, codec.height)

        val frameBitmapSize = codec.width.toLong() * codec.height * 4
        val totalMemory = frameBitmapSize * frameCount
        val decodableFrames =
            if (totalMemory > MAX_BITMAP_MEMORY) {
                // Only decode first frame for huge GIFs
                1
            } else {
                frameCount
            }

        val frameInfos = codec.framesInfo
        val frames = ArrayList<ImageBitmap>(decodableFrames)
        val durations = ArrayList<Int>(decodableFrames)

        for (i in 0 until decodableFrames) {
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(codec.width, codec.height)
            codec.readPixels(bitmap, i)
            bitmap.setImmutable()
            frames.add(bitmap.asComposeImageBitmap())
            durations.add(if (frameInfos.size > i) frameInfos[i].duration else 100)
        }

        GifFrames(frames, durations, frameBitmapSize * decodableFrames)
    } catch (e: Exception) {
        println("AnimatedGif: failed to load $url — ${e.message}")
        null
    }
