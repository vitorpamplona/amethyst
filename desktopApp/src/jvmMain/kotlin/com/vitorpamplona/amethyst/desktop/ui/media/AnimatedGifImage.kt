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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.ui.components.ReportCaptureLoading
import com.vitorpamplona.amethyst.desktop.service.media.MediaHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.impl.use

private const val MIN_FRAME_DURATION_MS = 20

// Shortest wait between two frames even when decoding ate the frame's whole duration, so a frame
// is on screen for at least one paint before its buffer is written again.
private const val MIN_FRAME_GAP_MS = 10L

/**
 * A downloaded GIF: its compressed [bytes] (null when it has a single frame, so there is nothing
 * to play) and its decoded [firstFrame], shown at once while the player starts.
 */
private class GifSource(
    val bytes: ByteArray?,
    val firstFrame: ImageBitmap,
) {
    val cost = (bytes?.size ?: 0) + firstFrame.width.toLong() * firstFrame.height * 4
}

// GIFs kept for the next time they show (scrolling back, reopening a thread), so they neither
// download again nor flash a placeholder. Bounded by their bytes plus their first frame's pixels.
private const val SOURCE_CACHE_BYTES = 64L * 1024 * 1024

private object GifSourceCache {
    private val entries = LinkedHashMap<String, GifSource>(16, 0.75f, true)
    private var total = 0L

    @Synchronized
    fun get(url: String): GifSource? = entries[url]

    @Synchronized
    fun put(
        url: String,
        source: GifSource,
    ) {
        if (source.cost > SOURCE_CACHE_BYTES) return
        entries.put(url, source)?.let { total -= it.cost }
        total += source.cost
        val oldest = entries.entries.iterator()
        while (total > SOURCE_CACHE_BYTES && oldest.hasNext()) {
            val evicted = oldest.next()
            if (evicted.key == url) continue
            total -= evicted.value.cost
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
    // Called when neither the GIF decoder nor the image loader can draw [url].
    onError: (() -> Unit)? = null,
) {
    var gif by remember(url) { mutableStateOf(GifSourceCache.get(url)) }
    var loadFailed by remember(url) { mutableStateOf(false) }
    if (gif == null && !loadFailed) ReportCaptureLoading()

    LaunchedEffect(url) {
        loadFailed = false
        if (gif != null) return@LaunchedEffect
        gif = withContext(Dispatchers.IO) { loadGif(url)?.also { GifSourceCache.put(url, it) } }
        if (gif == null) loadFailed = true
    }

    val data = gif
    when {
        data != null -> {
            var shown by remember(data) { mutableStateOf(data.firstFrame) }
            if (data.bytes != null) {
                LaunchedEffect(data) { playGif(data.bytes) { shown = it } }
            }

            Image(
                bitmap = shown,
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
                onError = onError?.let { callback -> { callback() } },
            )
        }

        else -> {
            Box(modifier)
        }
    }
}

/**
 * Loops the GIF in [bytes] for as long as the caller stays active, handing each frame to [show].
 * Frames decode one at a time, off the UI thread, the way Android's `AnimatedImageDrawable` plays
 * them, so a GIF costs two frames of memory however many it has. Stops on the last frame shown
 * if the GIF turns out to be broken halfway through.
 */
private suspend fun playGif(
    bytes: ByteArray,
    show: (ImageBitmap) -> Unit,
) {
    val player =
        try {
            withContext(Dispatchers.Default) { GifPlayer(bytes) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            println("AnimatedGif: cannot play — ${e.message}")
            return
        }

    // withContext only returns once its block has finished, so no decode is running when the
    // codec closes.
    try {
        var frame = 0
        while (currentCoroutineContext().isActive) {
            val start = System.nanoTime()
            show(withContext(Dispatchers.Default) { player.decode(frame) })
            val spentMs = (System.nanoTime() - start) / 1_000_000
            delay((player.duration(frame) - spentMs).coerceAtLeast(MIN_FRAME_GAP_MS))
            frame = (frame + 1) % player.frameCount
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        println("AnimatedGif: stopped playing — ${e.message}")
    } finally {
        player.close()
    }
}

/**
 * Decodes the frames of a GIF in order into two buffers that take turns: the next frame is drawn
 * into the buffer that is not on screen, starting from the frame on screen when it builds on it.
 *
 * The buffers stay mutable so Compose copies their pixels when it draws them (it makes a Skia
 * image from the bitmap on every draw) and a recorded frame never changes under it. They are not
 * closed here: a draw already queued may still read one, so they are left to the garbage
 * collector like any other bitmap.
 */
internal class GifPlayer(
    bytes: ByteArray,
) : AutoCloseable {
    private val data = Data.makeFromBytes(bytes)
    private val codec = Codec.makeFromData(data)
    private val frameInfos = codec.framesInfo
    private val buffers =
        Array(2) {
            Bitmap().apply { allocN32Pixels(codec.width, codec.height) }
        }
    private val images = buffers.map { it.asComposeImageBitmap() }

    // The buffer holding [lastFrame], or -1 before the first decode.
    private var front = -1
    private var lastFrame = -1

    val frameCount: Int = codec.frameCount

    fun duration(frame: Int): Int = (frameInfos.getOrNull(frame)?.duration ?: 100).coerceAtLeast(MIN_FRAME_DURATION_MS)

    /** The pixels of [frame], decoded into the buffer that is not on screen. */
    fun decode(frame: Int): ImageBitmap {
        val back = if (front == 0) 1 else 0
        val target = buffers[back]
        val required = frameInfos.getOrNull(frame)?.requiredFrame ?: -1

        if (required >= 0 && canBuildOn(lastFrame, required, frame)) {
            // The frame draws over the one on screen: start from it instead of decoding every
            // frame it depends on again.
            buffers[front].peekPixels()?.use { onScreen ->
                target.peekPixels()?.use { onScreen.readPixels(it) }
            }
            codec.readPixels(target, frame, lastFrame)
        } else {
            // Starts from a transparent canvas, or depends on frames that are not on screen, in
            // which case Skia decodes those first.
            target.erase(0)
            codec.readPixels(target, frame)
        }
        target.notifyPixelsChanged()

        front = back
        lastFrame = frame
        return images[back]
    }

    // Skia accepts a prior frame between the frame's required one and the frame itself, unless
    // the prior frame is meant to be undone ("restore previous").
    private fun canBuildOn(
        prior: Int,
        required: Int,
        frame: Int,
    ) = prior in required until frame && frameInfos[prior].disposalMethod != AnimationDisposalMode.RESTORE_PREVIOUS

    override fun close() {
        codec.close()
        data.close()
    }
}

/**
 * The bytes of the GIF at [url]: from the image loader's disk cache when it holds them, else
 * downloaded and kept there. GIFs decode here rather than through the image loader, so without this
 * one would be downloaded again on every launch (and lost the day its host goes down). Kept under its
 * own key so it never collides with the loader's own entry for the same URL, and, like the other
 * media caches, nothing that went over Tor or was encrypted is written to disk. Null when the
 * download fails: an error page is not a GIF.
 */
private suspend fun gifBytes(url: String): ByteArray? {
    val disk = if (MediaHttp.viaTor(url) || MediaHttp.isEncrypted(url)) null else SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache
    val key = "gif:$url"
    disk?.openSnapshot(key)?.use { snapshot ->
        runCatching { disk.fileSystem.read(snapshot.data) { readByteArray() } }.getOrNull()?.let { return it }
    }

    val request = Request.Builder().url(url).build()
    val bytes =
        MediaHttp.imageClient(url).newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) return null
            response.body.bytes()
        }

    disk?.openEditor(key)?.let { editor ->
        try {
            disk.fileSystem.write(editor.data) { write(bytes) }
            editor.commit()
        } catch (e: Exception) {
            editor.abort()
        }
    }
    return bytes
}

private suspend fun loadGif(url: String): GifSource? =
    try {
        val bytes = gifBytes(url) ?: return null

        Data.makeFromBytes(bytes).use { skData ->
            Codec.makeFromData(skData).use { codec ->
                val frameCount = codec.frameCount
                if (frameCount <= 0) return null
                // So the card reserves the GIF's shape the next time, before it decodes again.
                MediaAspectRatioCache.add(url, codec.width, codec.height)

                val firstFrame =
                    Bitmap().apply {
                        allocN32Pixels(codec.width, codec.height)
                        codec.readPixels(this, 0)
                        setImmutable()
                    }
                GifSource(bytes.takeIf { frameCount > 1 }, firstFrame.asComposeImageBitmap())
            }
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        println("AnimatedGif: failed to load $url — ${e.message}")
        null
    }
