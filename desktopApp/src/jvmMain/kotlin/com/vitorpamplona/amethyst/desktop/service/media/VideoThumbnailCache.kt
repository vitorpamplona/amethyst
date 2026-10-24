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
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.service.upload.video.FfmpegLocator
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.desktop.app.DesktopInstance
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.jcodec.api.FrameGrab
import org.jcodec.common.DemuxerTrackMeta
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.AWTUtil
import org.jcodec.scale.ColorUtil
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Per-URL one-frame poster for feed videos.
 *
 * Cascade:
 *   1. **JCodec** (`org.jcodec:jcodec` + `jcodec-javase`, BSD-2) — pure-Java H.264 decode of the
 *      first [MAX_CHUNK_BYTES] of the video. Handles the bulk of Nostr feed media (MP4/H.264).
 *   2. **LGPL FFmpeg subprocess** (raw `ProcessBuilder`) — for everything else (HEVC, VP9, AV1,
 *      HLS, malformed faststart MP4s). Needs a system `ffmpeg` on `$PATH` or a bundled binary in the
 *      app resources at `ffmpeg/ffmpeg(.exe)`.
 *
 * Posters are scaled to [MAX_POSTER_WIDTH], held in a bounded in-memory LRU, and kept on disk as
 * small JPEGs in an owner-only cache dir, so the next launch does not download the video again.
 * Videos that go over Tor and encrypted ones never touch that disk cache, and an encrypted video
 * gets a poster only once it has been decrypted for playing: making one would mean downloading and
 * decrypting the whole blob, for every encrypted video that scrolls by.
 */
object VideoThumbnailCache {
    private const val TAG = "VideoThumbnailCache"
    private const val MAX_CHUNK_BYTES = 4 * 1024 * 1024
    private const val MAX_POSTER_WIDTH = 640
    private const val MAX_MEMORY_POSTERS = 120
    private const val MAX_DISK_BYTES = 64L * 1024 * 1024
    private const val FAILURE_RETRY_MS = 60_000L
    private const val FFMPEG_TIMEOUT_SECONDS = 15L
    private const val PAR_SUFFIX = ".par"

    // How often, and how many times, keepFromStream looks for the start of a video its engine streams.
    private const val STREAM_POLL_MS = 2_000L
    private const val STREAM_POLLS = 15

    private val memory =
        object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>) = size > MAX_MEMORY_POSTERS
        }

    // One extraction per URL: a second card asking for the same video waits for the first.
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    // When each URL last failed, so a video that has no poster is not downloaded again on every scroll.
    private val failedAt = ConcurrentHashMap<String, Long>()

    // Each extraction downloads up to MAX_CHUNK_BYTES and may run ffmpeg; a fast scroll would start dozens.
    private val extractions = Semaphore(2)

    // The videos keepFromStream waits on, one wait per URL. Its waits outlive the card that asked.
    private val keeping = ConcurrentHashMap.newKeySet<String>()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val ffmpegWatchdog =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "ffmpeg-watchdog").apply { isDaemon = true } }

    private val diskDir: File by lazy {
        // A second copy of the app gets its own, as for the video cache (see DesktopInstance).
        DesktopInstance.ownFolder(DesktopInstance.PerCopyFolder.VIDEO_THUMBS).also { dir ->
            dir.mkdirs()
            try {
                Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
            } catch (_: UnsupportedOperationException) {
                // Windows: the per-user profile's ACLs already keep it private.
            }
            trimDisk(dir)
        }
    }

    fun getCached(url: String): ImageBitmap? = synchronized(memory) { memory[url] }

    /** The poster of [url], extracting it if needed; null when it has none (or failed recently). */
    suspend fun getThumbnail(url: String): ImageBitmap? {
        getCached(url)?.let { return it }
        failedAt[url]?.let { if (System.currentTimeMillis() - it < FAILURE_RETRY_MS) return null }

        val mine = CompletableDeferred<ImageBitmap?>()
        val running = inFlight.putIfAbsent(url, mine)
        if (running != null) return running.await()

        try {
            val poster = withContext(Dispatchers.IO) { extractions.withPermit { load(url) } }
            if (poster != null) {
                synchronized(memory) { memory[url] = poster }
                failedAt.remove(url)
            } else {
                failedAt[url] = System.currentTimeMillis()
            }
            mine.complete(poster)
            return poster
        } catch (e: Throwable) {
            // Cancelled (the card scrolled away) or broken: whoever waits on it just gets no poster.
            mine.complete(null)
            throw e
        } finally {
            inFlight.remove(url, mine)
        }
    }

    /**
     * The poster of [url] only if it is already in memory or on disk: nothing is downloaded. For a
     * card whose own engine opens the video anyway and shows its first frame.
     */
    suspend fun getStored(url: String): ImageBitmap? {
        getCached(url)?.let { return it }
        val poster = withContext(Dispatchers.IO) { readDisk(url) } ?: return null
        synchronized(memory) { memory[url] = poster }
        return poster
    }

    /**
     * Makes [url]'s poster in the background from the bytes its engine streams through [MediaRelay],
     * which land in [VideoDiskCache], once the start of the video is there: nothing is downloaded
     * again. For a card whose own engine shows the video's frame: that engine goes when the card
     * leaves the screen, and the poster is what the card then shows, there and on the next launch.
     */
    fun keepFromStream(url: String) {
        if (getCached(url) != null || !VideoDiskCache.cacheable(url) || !keeping.add(url)) return
        background.launch {
            try {
                repeat(STREAM_POLLS) {
                    delay(STREAM_POLL_MS)
                    if (getCached(url) != null) return@launch
                    val (held, total) = VideoDiskCache.heldStart(url) ?: return@repeat
                    if (startIsHeld(held, total)) {
                        // Extracts from those bytes (see extractFirstFrame), deduplicated with any card asking.
                        getThumbnail(url)
                        return@launch
                    }
                }
            } finally {
                keeping.remove(url)
            }
        }
    }

    /** Whether [held] bytes from the start of a video [total] bytes long are as much as a poster reads. */
    internal fun startIsHeld(
        held: Long,
        total: Long,
    ): Boolean = held > 0 && held >= minOf(MAX_CHUNK_BYTES.toLong(), total)

    private fun isPersistable(url: String) = !MediaHttp.isEncrypted(url) && !MediaHttp.viaTor(url)

    // v2: frames from before rotation was applied are on disk sideways; v3: anamorphic ones
    // from before the pixel ratio was applied are squashed. Leave both behind.
    private fun diskKey(url: String) = sha1Hex("v3:$url")

    private fun readDisk(url: String): ImageBitmap? {
        if (!isPersistable(url)) return null
        val key = diskKey(url)
        val diskFile = File(diskDir, "$key.jpg")
        if (diskFile.length() <= 0) return null
        // The pixel ratio of an anamorphic video, beside its poster: the player needs it too.
        val parFile = File(diskDir, "$key$PAR_SUFFIX")
        val image = runCatching { ImageIO.read(diskFile) }.getOrNull() ?: return null
        // The poster is already at the video's display shape.
        recordShape(url, image)
        if (parFile.exists()) {
            runCatching { parFile.readText().toFloat() }.getOrNull()?.let { par -> VideoPixelAspect.record(url, par, image.width.toFloat() / image.height) }
        }
        return image.toComposeImageBitmap()
    }

    private suspend fun load(url: String): ImageBitmap? {
        readDisk(url)?.let { return it }

        val frame = extractFirstFrame(url) ?: return null
        recordShape(url, frame)
        if (isPersistable(url)) {
            val key = diskKey(url)
            writeAtomically(File(diskDir, "$key.jpg"), frame)
            VideoPixelAspect.get(url)?.let { par -> runCatching { File(diskDir, "$key$PAR_SUFFIX").writeText(par.toString()) } }
        }
        return frame.toComposeImageBitmap()
    }

    // A frame of the video, turned and with square pixels: the shape it plays at, which beats what
    // its event declares (see MediaAspectRatioCache.videoRatio).
    private fun recordShape(
        url: String,
        frame: BufferedImage,
    ) {
        if (frame.width > 0 && frame.height > 0) MediaAspectRatioCache.addMeasured(url, frame.width, frame.height)
    }

    private suspend fun extractFirstFrame(url: String): BufferedImage? {
        // An encrypted blob is ciphertext until decrypted whole: frame it only once it is.
        if (MediaHttp.isEncrypted(url)) {
            val plain = DecryptedMediaFiles.cachedFile(url) ?: return null
            return tryJCodec(plain, url) ?: runFfmpeg(plain.absolutePath)
        }

        // For HLS we skip straight to ffmpeg — JCodec can't read m3u8.
        val isHls = isHlsMedia(url, null)

        if (!isHls) {
            // What an engine already streamed of it, when that holds as much: else from the network.
            val chunk = streamedStart(url) ?: runCatchingCancellable { downloadFirstChunk(url) }.getOrNull()
            if (chunk != null) {
                try {
                    (tryJCodec(chunk, url) ?: runFfmpeg(chunk.absolutePath))?.let { return it }
                } finally {
                    chunk.deleteOrWarn(TAG, "video chunk")
                }
            }
        }

        // ffmpeg fetches by itself. A video that goes over Tor would reach it only through the
        // relay, whose secret would then sit in ffmpeg's command line for any local user to read.
        if (MediaHttp.viaTor(url)) return null
        return runFfmpeg(url)
    }

    /** The first [MAX_CHUNK_BYTES] of [url] (or all of it) from [VideoDiskCache] in a temp file, or null. */
    private fun streamedStart(url: String): File? {
        if (!VideoDiskCache.cacheable(url)) return null
        val (held, total) = VideoDiskCache.heldStart(url) ?: return null
        if (!startIsHeld(held, total)) return null
        val file = AmethystTempDir.createTempFile("amethyst_thumb_", ".mp4")
        val copied = runCatching { VideoDiskCache.copyStart(url, file, MAX_CHUNK_BYTES.toLong()) }.getOrDefault(0L)
        if (copied <= 0L) {
            file.deleteOrWarn(TAG, "video chunk")
            return null
        }
        return file
    }

    /**
     * The first [MAX_CHUNK_BYTES] of [url] in a temp file, or null. Capped even when the server
     * ignores Range and sends the whole video, and refused when it sends text (an HTML error page).
     */
    private suspend fun downloadFirstChunk(url: String): File? {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=0-${MAX_CHUNK_BYTES - 1}")
                .build()
        MediaHttp.client(url).newCall(request).executeAsync().use { resp ->
            if (!resp.isSuccessful) return null
            val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
            if (contentType.startsWith("text/") || "html" in contentType) return null
            val file = AmethystTempDir.createTempFile("amethyst_thumb_", ".mp4")
            val copied =
                try {
                    Files.newOutputStream(file.toPath()).use { out -> copyAtMost(resp.body.byteStream(), out, MAX_CHUNK_BYTES.toLong()) }
                } catch (e: Exception) {
                    file.deleteOrWarn(TAG, "partial video chunk")
                    throw e
                }
            if (copied == 0L) {
                file.deleteOrWarn(TAG, "empty video chunk")
                return null
            }
            return file
        }
    }

    private fun copyAtMost(
        src: InputStream,
        dst: OutputStream,
        limit: Long,
    ): Long {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        while (copied < limit) {
            val toRead = minOf(buf.size.toLong(), limit - copied).toInt()
            val n = src.read(buf, 0, toRead)
            if (n < 0) break
            dst.write(buf, 0, n)
            copied += n
        }
        return copied
    }

    /** A frame of [mp4], which holds the start of [url]; records the video's pixel ratio on the way. */
    private fun tryJCodec(
        mp4: File,
        url: String,
    ): BufferedImage? =
        runCatching {
            NIOUtils.readableChannel(mp4).use { ch ->
                val grab = FrameGrab.createFrameGrab(ch).seekToSecondSloppy(1.0)
                val frame = grab.nativeFrameWithMetadata ?: return null
                val native: Picture = frame.picture ?: return null
                val rgb = Picture.create(native.width, native.height, ColorSpace.RGB)
                ColorUtil.getTransform(native.color, ColorSpace.RGB).transform(native, rgb)
                val par =
                    grab.videoTrack
                        ?.meta
                        ?.videoCodecMeta
                        ?.pixelAspectRatio
                        ?.takeIf { it.num > 0 && it.den > 0 }
                        ?.let { it.num.toFloat() / it.den }
                // Phones store portrait video sideways with a rotation in the track header; the
                // decoded picture ignores it, so apply it (ffmpeg already does). An anamorphic
                // video's pixels are not square: stretch them back first, as they are stored.
                val poster = scaleDown(rotate(squarePixels(AWTUtil.toBufferedImage(rgb), par), frame.orientation))
                if (par != null) VideoPixelAspect.record(url, par, poster.width.toFloat() / poster.height)
                poster
            }
        }.getOrNull()

    /** [image] with its [par]-wide pixels made square: narrower for a ratio under 1, shorter over it. */
    private fun squarePixels(
        image: BufferedImage,
        par: Float?,
    ): BufferedImage {
        if (par == null || abs(par - 1f) <= 0.01f) return image
        val w = if (par < 1f) (image.width * par).roundToInt().coerceAtLeast(1) else image.width
        val h = if (par > 1f) (image.height / par).roundToInt().coerceAtLeast(1) else image.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(image, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /** [image] turned as the track's display [orientation] asks (clockwise degrees). */
    private fun rotate(
        image: BufferedImage,
        orientation: DemuxerTrackMeta.Orientation?,
    ): BufferedImage {
        val quarterTurns =
            when (orientation) {
                DemuxerTrackMeta.Orientation.D_90 -> 1
                DemuxerTrackMeta.Orientation.D_180 -> 2
                DemuxerTrackMeta.Orientation.D_270 -> 3
                else -> return image
            }
        val swap = quarterTurns % 2 == 1
        val w = if (swap) image.height else image.width
        val h = if (swap) image.width else image.height
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.translate(w / 2.0, h / 2.0)
            g.rotate(Math.toRadians(90.0 * quarterTurns))
            g.drawImage(image, -image.width / 2, -image.height / 2, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /**
     * One frame of [input] (a file or a URL) from `ffmpeg`, already scaled to the poster width,
     * as a PNG on its stdout. A watchdog kills it if it overruns: its HTTP has no read timeout, and
     * a stalled server would otherwise hold the process and this thread forever.
     */
    private fun runFfmpeg(input: String): BufferedImage? {
        val ffmpeg = FfmpegLocator.default.ffmpeg ?: return null
        val cmd =
            listOf(
                ffmpeg,
                "-hide_banner",
                "-loglevel",
                "error",
                "-ss",
                "1",
                "-i",
                input,
                "-frames:v",
                "1",
                "-vf",
                // Square pixels first: an anamorphic video's frame is stored at another shape.
                "scale='trunc(iw*sar/2)*2':ih,setsar=1,scale='min($MAX_POSTER_WIDTH,iw)':-2",
                "-an",
                "-f",
                "image2pipe",
                "-c:v",
                "png",
                "pipe:1",
            )
        val process =
            runCatching {
                ProcessBuilder(cmd)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            }.getOrNull() ?: return null
        val kill = ffmpegWatchdog.schedule({ process.destroyForcibly() }, FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val out = ByteArrayOutputStream(128 * 1024)
        try {
            process.inputStream.use { it.copyTo(out) }
            if (!process.waitFor(2, TimeUnit.SECONDS)) return null
            if (process.exitValue() != 0 || out.size() == 0) return null
        } catch (_: Exception) {
            return null
        } finally {
            kill.cancel(false)
            if (process.isAlive) process.destroyForcibly()
        }
        return runCatching { ImageIO.read(ByteArrayInputStream(out.toByteArray())) }.getOrNull()
    }

    private fun scaleDown(image: BufferedImage): BufferedImage {
        if (image.width <= MAX_POSTER_WIDTH) return image
        val height = (image.height.toLong() * MAX_POSTER_WIDTH / image.width).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(MAX_POSTER_WIDTH, height, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(image, 0, 0, MAX_POSTER_WIDTH, height, null)
        } finally {
            g.dispose()
        }
        return scaled
    }

    // Written beside the target and moved into place, so a crash never leaves a half poster behind.
    private fun writeAtomically(
        target: File,
        image: BufferedImage,
    ) {
        val rgb =
            if (image.type == BufferedImage.TYPE_INT_RGB) {
                image
            } else {
                BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB).also { it.createGraphics().apply { drawImage(image, 0, 0, null) }.dispose() }
            }
        val temp = File(target.parentFile, target.name + ".tmp")
        try {
            if (!ImageIO.write(rgb, "jpg", temp)) return
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Log.d(TAG) { "Could not cache the poster: ${e.message}" }
            temp.delete()
        }
    }

    // Drops the video chunks older versions cached here, then the oldest posters past MAX_DISK_BYTES,
    // and the pixel ratios of posters that are gone.
    private fun trimDisk(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        files.filter { !it.name.endsWith(".jpg") && !it.name.endsWith(PAR_SUFFIX) }.forEach { it.delete() }
        var total = 0L
        val kept = HashSet<String>()
        files
            .filter { it.name.endsWith(".jpg") }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                total += file.length()
                if (total > MAX_DISK_BYTES) file.delete() else kept.add(file.name.removeSuffix(".jpg"))
            }
        files.filter { it.name.endsWith(PAR_SUFFIX) && it.name.removeSuffix(PAR_SUFFIX) !in kept }.forEach { it.delete() }
    }

    private fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
