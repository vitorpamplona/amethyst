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
package com.vitorpamplona.amethyst.commons.ui.components.animated

import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.ImagePainter
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.service.image.PixelExactImage
import com.vitorpamplona.amethyst.commons.service.image.drawAtWholePixels
import com.vitorpamplona.amethyst.commons.service.image.scaleInto
import com.vitorpamplona.amethyst.commons.ui.components.ReportCaptureLoading
import com.vitorpamplona.amethyst.commons.ui.render.SoftwareDrawing
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.AnimationDisposalMode
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.impl.use
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private const val TAG = "AnimatedGif"

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
private const val SOURCE_CACHE_BYTES = 64 * 1024 * 1024

private object GifSourceCache {
    // Least recently shown first out. LruCache locks itself, so the UI thread and the loaders share it.
    private val entries =
        object : LruCache<String, GifSource>(SOURCE_CACHE_BYTES) {
            override fun sizeOf(
                key: String,
                value: GifSource,
            ): Int = value.cost.toInt()
        }

    fun get(url: String): GifSource? = entries[url]

    fun put(
        url: String,
        source: GifSource,
    ) {
        // One bigger than the whole cache would evict everything else and then itself.
        if (source.cost > SOURCE_CACHE_BYTES) return
        entries.put(url, source)
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
    // The same, with the image loader's reason, for a failure card to word it.
    onFailure: ((Throwable?) -> Unit)? = null,
    // [url] is a file the user picked (the media Studio), read from disk. Off for anything a note
    // names, so remote content never makes the client read a local file.
    local: Boolean = false,
    // Off for a viewer that zooms with a layer scale, which frames scaled to the box couldn't follow.
    scaleToDrawnSize: Boolean = true,
    // Whether a frame is on screen, for a caller that draws a placeholder under it until then.
    onShown: (Boolean) -> Unit = {},
    // Off when the user turned autoplay off: the first frame stays, and no player runs.
    autoPlay: Boolean = true,
    // Where the file's bytes come from: the platform's HTTP client, local files, decryption.
    fetcher: AnimatedImageFetcher = AnimatedImageFetcher.installed,
) {
    var gif by remember(url) { mutableStateOf(GifSourceCache.get(url)) }
    var loadFailed by remember(url) { mutableStateOf(false) }
    if (gif == null && !loadFailed) ReportCaptureLoading()

    LaunchedEffect(url) {
        loadFailed = false
        if (gif != null) return@LaunchedEffect
        gif = withContext(Dispatchers.IO) { loadGif(url, local, fetcher)?.also { GifSourceCache.put(url, it) } }
        if (gif == null) loadFailed = true
    }

    val data = gif
    SideEffect { onShown(data != null) }
    when {
        data != null -> {
            var shown by remember(data) { mutableStateOf<GifFrame>(GifFrame.First) }
            val box = remember { GifBox() }
            val currentScale by rememberUpdatedState(contentScale)
            if (data.bytes != null && autoPlay) {
                LaunchedEffect(data) {
                    playGif(
                        data.bytes,
                        target = { width, height -> box.target(width, height, currentScale, scaleToDrawnSize) },
                    ) { shown = it }
                }
            }
            GifFrameImage(shown, data, scaleToDrawnSize, contentDescription, modifier.onSizeChanged { box.size = it }, contentScale)
        }

        loadFailed -> {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                onError =
                    if (onError != null || onFailure != null) {
                        { state ->
                            onError?.invoke()
                            onFailure?.invoke(state.result.throwable)
                        }
                    } else {
                        null
                    },
            )
        }

        else -> {
            Box(modifier)
        }
    }
}

/** What a GIF shows. */
private sealed interface GifFrame {
    /** Its first frame, before the player hands over one (or for good, in a still GIF). */
    data object First : GifFrame

    /** A frame at the GIF's own size, scaled as it is drawn (on a GPU, or in a zooming viewer). */
    class Native(
        val bitmap: ImageBitmap,
    ) : GifFrame

    /** A frame the player scaled (and cropped) to its box: an immutable snapshot, drawn 1:1. */
    class Exact(
        val image: Image,
    ) : GifFrame
}

@Composable
private fun GifFrameImage(
    frame: GifFrame,
    data: GifSource,
    scaleToDrawnSize: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    when (frame) {
        is GifFrame.Exact -> {
            val painter = remember(frame) { ExactFramePainter(frame.image) }
            Image(painter = painter, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
        }

        is GifFrame.Native -> Image(bitmap = frame.bitmap, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)

        GifFrame.First ->
            if (scaleToDrawnSize) {
                // A still GIF, or one whose player has not handed over a frame yet (it restarts each
                // time the card scrolls back in): scaled once and drawn 1:1 in software
                // (PixelExactImage), not resampled on every draw.
                val painter = remember(data) { ImagePainter(PixelExactImage(data.firstFrame.asSkiaBitmap())) }
                Image(painter = painter, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
            } else {
                Image(bitmap = data.firstFrame, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
            }
    }
}

/**
 * Draws a frame the player scaled to its box: 1:1 at whole pixels when the slot is still the size it
 * was scaled for (so the frame lands as Compose would place it, unscaled), and resampled to the slot
 * when the box has changed since (a resize), until the player's next frame catches up.
 */
private class ExactFramePainter(
    private val image: Image,
) : Painter() {
    override val intrinsicSize: Size get() = Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        drawIntoCanvas { canvas ->
            val native = canvas.skiaCanvas
            if (size.width.roundToInt() == image.width && size.height.roundToInt() == image.height) {
                native.drawAtWholePixels(image)
            } else {
                native.drawImageRect(image, 0f, 0f, image.width.toFloat(), image.height.toFloat(), 0f, 0f, size.width, size.height, SamplingMode.LINEAR, null, true)
            }
        }
    }
}

/** Where the player is told to put the next frame. */
private sealed interface FrameTarget {
    /** At the GIF's own size, for the composable to scale (a GPU, or a zooming viewer). */
    data object Native : FrameTarget

    /** Not known yet: the box has not been laid out. */
    data object Wait : FrameTarget

    /** Scaled (and cropped) to the box. */
    class Scaled(
        val plan: FramePlan,
    ) : FrameTarget
}

/**
 * Loops the GIF in [bytes] for as long as the caller stays active, handing each frame to [show].
 * Frames decode one at a time, off the UI thread, the way Android's `AnimatedImageDrawable` plays
 * them, so a GIF costs two frames of memory however many it has. Stops on the last frame shown
 * if the GIF turns out to be broken halfway through.
 */
private suspend fun playGif(
    bytes: ByteArray,
    target: (width: Int, height: Int) -> FrameTarget,
    show: (GifFrame) -> Unit,
) {
    val player =
        try {
            withContext(Dispatchers.Default) { GifPlayer(bytes) }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "cannot play", e)
            return
        }

    // withContext only returns once its block has finished, so no decode is running when the
    // codec closes.
    try {
        var frame = 0
        // The snapshots shown last, released two frames on: by then nothing draws them but the
        // pictures already recorded with them, which keep their own reference to the pixels.
        val shownSnapshots = ArrayDeque<Image>()
        while (currentCoroutineContext().isActive) {
            val start = TimeSource.Monotonic.markNow()
            when (val next = target(player.width, player.height)) {
                // Until the box is laid out: a frame now would be scaled again on every draw.
                FrameTarget.Wait -> {
                    delay(MIN_FRAME_GAP_MS)
                    continue
                }

                FrameTarget.Native -> {
                    show(GifFrame.Native(withContext(Dispatchers.Default) { player.decode(frame) }))
                }

                is FrameTarget.Scaled -> {
                    val snapshot = withContext(Dispatchers.Default) { player.decodeScaled(frame, next.plan) }
                    show(GifFrame.Exact(snapshot))
                    shownSnapshots.addLast(snapshot)
                    while (shownSnapshots.size > 2) shownSnapshots.removeFirst().close()
                }
            }
            val spentMs = start.elapsedNow().inWholeMilliseconds
            delay((player.duration(frame) - spentMs).coerceAtLeast(MIN_FRAME_GAP_MS))
            frame = (frame + 1) % player.frameCount
        }
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        Log.w(TAG, "stopped playing", e)
    } finally {
        player.close()
    }
}

/** Where a GIF is drawn, as laid out, for the player's thread to scale its frames to. */
private class GifBox {
    // State, so a resize recomposes the frame on screen; read by the player's thread too.
    var size: IntSize? by mutableStateOf(null)

    fun target(
        width: Int,
        height: Int,
        contentScale: ContentScale,
        scaleToDrawnSize: Boolean,
    ): FrameTarget {
        if (!scaleToDrawnSize || !SoftwareDrawing.active) return FrameTarget.Native
        if (size == null) return FrameTarget.Wait
        return drawnSize(width, height, contentScale)?.let { FrameTarget.Scaled(it) } ?: FrameTarget.Native
    }

    /** How a [width] x [height] frame lands in this box with [contentScale]; null when unknown or too big to scale. */
    fun drawnSize(
        width: Int,
        height: Int,
        contentScale: ContentScale,
    ): FramePlan? {
        val box = size?.takeIf { it.width > 0 && it.height > 0 } ?: return null
        val factor = contentScale.computeScaleFactor(Size(width.toFloat(), height.toFloat()), Size(box.width.toFloat(), box.height.toFloat()))
        val w = (width * factor.scaleX).roundToInt().coerceAtLeast(1)
        val h = (height * factor.scaleY).roundToInt().coerceAtLeast(1)
        if (w.toLong() * h > MAX_SCALED_FRAME_PIXELS) return null
        return FramePlan.croppedTo(IntSize(w, h), box)
    }
}

/**
 * A frame scaled to [scaled] and kept only where it shows: an [out]-sized frame holding the scaled
 * one placed at ([dx], [dy]). A frame that overflows its box (a crop) is cut to the box, centred as
 * Compose centres it, so the player scales, and the UI thread copies, only what is drawn.
 */
internal class FramePlan(
    val scaled: IntSize,
    val out: IntSize = scaled,
    val dx: Int = 0,
    val dy: Int = 0,
) {
    companion object {
        fun croppedTo(
            scaled: IntSize,
            box: IntSize,
        ): FramePlan {
            val outW = minOf(scaled.width, box.width)
            val outH = minOf(scaled.height, box.height)
            val dx = if (scaled.width > box.width) ((box.width - scaled.width) / 2f).roundToInt() else 0
            val dy = if (scaled.height > box.height) ((box.height - scaled.height) / 2f).roundToInt() else 0
            return FramePlan(scaled, IntSize(outW, outH), dx, dy)
        }
    }
}

// A 2560x1600 screen: past it the frames are left at their own size.
private const val MAX_SCALED_FRAME_PIXELS = 4L * 1024 * 1024

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

    val width: Int = codec.width
    val height: Int = codec.height

    // The buffer holding [lastFrame], or -1 before the first decode.
    private var front = -1
    private var lastFrame = -1

    val frameCount: Int = codec.frameCount

    fun duration(frame: Int): Int = (frameInfos.getOrNull(frame)?.duration ?: 100).coerceAtLeast(MIN_FRAME_DURATION_MS)

    /** The pixels of [frame], decoded into the buffer that is not on screen. */
    fun decode(frame: Int): ImageBitmap = decodeNative(frame)

    /**
     * [frame] scaled to its box as [plan] says: in software a frame drawn at another size is resampled
     * on the UI thread every draw, so it is scaled here, on the player's thread, once per frame (see
     * SoftwareDrawing). An immutable snapshot, so the UI thread draws it without copying it.
     */
    fun decodeScaled(
        frame: Int,
        plan: FramePlan,
    ): Image {
        decodeNative(frame)
        val out = Bitmap()
        out.allocN32Pixels(plan.out.width, plan.out.height)
        // Shares the buffer's pixels: drawn from right away, before the next decode changes them.
        buffers[front].peekPixels()?.let { pixels ->
            Image.makeFromPixmap(pixels).use { source -> scaleInto(out, source, plan.scaled.width, plan.scaled.height, plan.dx, plan.dy) }
        }
        out.setImmutable()
        return Image.makeFromBitmap(out).also { out.close() }
    }

    private fun decodeNative(frame: Int): ImageBitmap {
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
private suspend fun gifBytes(
    url: String,
    local: Boolean,
    fetcher: AnimatedImageFetcher,
): ByteArray? {
    // A file the user picked to post (the media Studio, the composer's card): read it in place.
    if (local) return fetcher.readLocal(url)

    val disk = if (fetcher.mayCacheOnDisk(url)) SingletonImageLoader.get(PlatformContext.INSTANCE).diskCache else null
    val key = "gif:$url"
    // Its own copy, else the image loader's: an animated WebP reaches the player after the loader
    // fetched it ([AnimatedImageProbe]), so the file is already on disk under its URL.
    for (cached in arrayOf(key, url)) {
        disk?.openSnapshot(cached)?.use { snapshot ->
            runCatching { disk.fileSystem.read(snapshot.data) { readByteArray() } }.getOrNull()?.let { return it }
        }
    }

    val bytes = fetcher.download(url) ?: return null

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

/**
 * Downloads (or reads from the disk cache) and decodes the first frame of [url] into the player's
 * cache, so an [AnimatedGifImage] that takes over from a still image paints at once instead of
 * an empty box. True when the file has frames to play; a single frame is left to the image
 * loader, which downsamples it.
 */
internal suspend fun preloadAnimation(
    url: String,
    fetcher: AnimatedImageFetcher = AnimatedImageFetcher.installed,
): Boolean {
    GifSourceCache.get(url)?.let { return it.bytes != null }
    val source = withContext(Dispatchers.IO) { loadGif(url, local = false, fetcher) } ?: return false
    if (source.bytes != null) GifSourceCache.put(url, source)
    return source.bytes != null
}

private suspend fun loadGif(
    url: String,
    local: Boolean,
    fetcher: AnimatedImageFetcher,
): GifSource? =
    try {
        val bytes = gifBytes(url, local, fetcher) ?: return null

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
        Log.w(TAG, "failed to load $url", e)
        null
    }
