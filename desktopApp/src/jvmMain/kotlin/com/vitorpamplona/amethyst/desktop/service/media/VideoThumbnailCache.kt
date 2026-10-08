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
import com.vitorpamplona.amethyst.commons.service.upload.AmethystTempDir
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.jcodec.api.FrameGrab
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

    private val ffmpegWatchdog =
        Executors.newSingleThreadScheduledExecutor { Thread(it, "ffmpeg-watchdog").apply { isDaemon = true } }

    private val diskDir: File by lazy {
        File(System.getProperty("user.home"), ".cache/amethyst-desktop/video-thumbs").also { dir ->
            dir.mkdirs()
            try {
                Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
            } catch (_: UnsupportedOperationException) {
                // Windows: the per-user profile's ACLs already keep it private.
            }
            trimDisk(dir)
        }
    }

    private val ffmpegBinary: String? by lazy {
        // 1. System ffmpeg on PATH. Probe with `ffmpeg -version`, killing it if it overruns.
        val onPath =
            runCatching {
                val probe =
                    ProcessBuilder("ffmpeg", "-version")
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start()
                val exited = probe.waitFor(2, TimeUnit.SECONDS)
                if (!exited) {
                    probe.destroyForcibly()
                    return@runCatching false
                }
                probe.exitValue() == 0
            }.getOrDefault(false)
        if (onPath) return@lazy "ffmpeg"

        // 2. Bundled ffmpeg in the app resources (also set by `./gradlew :desktopApp:run`). Never a
        // path relative to the working directory, which whoever launches the app controls.
        val binaryName = if ("win" in System.getProperty("os.name").lowercase()) "ffmpeg.exe" else "ffmpeg"
        System
            .getProperty("compose.application.resources.dir")
            ?.let { File(it, "ffmpeg/$binaryName") }
            ?.takeIf { it.exists() && it.canExecute() }
            ?.absolutePath
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

    private suspend fun load(url: String): ImageBitmap? {
        val persistable = !MediaHttp.isEncrypted(url) && !MediaHttp.viaTor(url)
        val diskFile = File(diskDir, sha1Hex(url) + ".jpg")
        if (persistable && diskFile.length() > 0) {
            runCatching { ImageIO.read(diskFile) }.getOrNull()?.let { return it.toComposeImageBitmap() }
        }

        val frame = extractFirstFrame(url) ?: return null
        if (persistable) writeAtomically(diskFile, frame)
        return frame.toComposeImageBitmap()
    }

    private suspend fun extractFirstFrame(url: String): BufferedImage? {
        // An encrypted blob is ciphertext until decrypted whole: frame it only once it is.
        if (MediaHttp.isEncrypted(url)) {
            val plain = DecryptedMediaFiles.cachedFile(url) ?: return null
            return tryJCodec(plain) ?: runFfmpeg(plain.absolutePath)
        }

        // For HLS we skip straight to ffmpeg — JCodec can't read m3u8.
        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("/hls/", ignoreCase = true)

        if (!isHls) {
            val chunk = runCatching { downloadFirstChunk(url) }.getOrNull()
            if (chunk != null) {
                try {
                    (tryJCodec(chunk) ?: runFfmpeg(chunk.absolutePath))?.let { return it }
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

    private fun tryJCodec(mp4: File): BufferedImage? =
        runCatching {
            NIOUtils.readableChannel(mp4).use { ch ->
                val grab = FrameGrab.createFrameGrab(ch).seekToSecondSloppy(1.0)
                val native: Picture = grab.nativeFrame ?: return null
                val rgb = Picture.create(native.width, native.height, ColorSpace.RGB)
                ColorUtil.getTransform(native.color, ColorSpace.RGB).transform(native, rgb)
                scaleDown(AWTUtil.toBufferedImage(rgb))
            }
        }.getOrNull()

    /**
     * One frame of [input] (a file or a URL) from `ffmpeg`, already scaled to the poster width,
     * as a PNG on its stdout. A watchdog kills it if it overruns: its HTTP has no read timeout, and
     * a stalled server would otherwise hold the process and this thread forever.
     */
    private fun runFfmpeg(input: String): BufferedImage? {
        val ffmpeg = ffmpegBinary ?: return null
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
                "scale='min($MAX_POSTER_WIDTH,iw)':-2",
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

    // Drops the video chunks older versions cached here, then the oldest posters past MAX_DISK_BYTES.
    private fun trimDisk(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        files.filter { !it.name.endsWith(".jpg") }.forEach { it.delete() }
        var total = 0L
        files
            .filter { it.name.endsWith(".jpg") }
            .sortedByDescending { it.lastModified() }
            .forEach { file ->
                total += file.length()
                if (total > MAX_DISK_BYTES) file.delete()
            }
    }

    private fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
