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
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.amethyst.desktop.network.DesktopHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jcodec.api.FrameGrab
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.AWTUtil
import org.jcodec.scale.ColorUtil
import org.jetbrains.skia.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Per-URL one-frame thumbnail extractor backing feed video posters.
 *
 * Cascade:
 *   1. **JCodec** (`org.jcodec:jcodec` + `jcodec-javase`, BSD-2) — pure-Java
 *      H.264 baseline/main/high decode. Handles the bulk of Nostr feed media
 *      (MP4/H.264).
 *   2. **LGPL FFmpeg subprocess** (driven via raw `ProcessBuilder`) — for
 *      everything else (HEVC, VP9, AV1, HLS, malformed faststart MP4s).
 *      Requires either a system `ffmpeg` on `$PATH` or a bundled binary at
 *      `src/jvmMain/appResources/<os>/ffmpeg/ffmpeg(.exe)`.
 *
 * Replaces the prior vlcj `RenderCallback` path. License moves from
 * GPL-3.0 (vlcj) to BSD-2 + LGPL-2.1, MIT-dominant overall.
 */
object VideoThumbnailCache {
    private const val MAX_THUMB_BYTES = 4 * 1024 * 1024 // 4 MiB cap per thumbnail

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val pending = ConcurrentHashMap<String, Boolean>()

    private val downloadCacheDir: File by lazy {
        val base =
            File(System.getProperty("user.home"), ".cache/amethyst-desktop/video-thumbs")
                .also { it.mkdirs() }
        base
    }

    private val ffmpegBinary: String? by lazy {
        // 1. System ffmpeg on PATH. Probe with `ffmpeg -version`; drain stdout
        // so the child doesn't block on a full pipe, kill it if it overruns
        // the probe budget so we don't leak the process when ffmpeg hangs.
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

        // 2. Bundled ffmpeg under appResources/<os>/ffmpeg/.
        // jpackage drops appResources at <app>/lib/app/resources/ — equivalently,
        // we can read from the working dir layout under desktopApp/src/jvmMain/appResources
        // during `./gradlew :desktopApp:run`. Look for it in well-known locations.
        val osName = System.getProperty("os.name").lowercase()
        val isWin = "win" in osName
        val binaryName = if (isWin) "ffmpeg.exe" else "ffmpeg"
        val candidates =
            listOf(
                File(System.getProperty("compose.application.resources.dir") ?: "", "ffmpeg/$binaryName"),
                File("desktopApp/src/jvmMain/appResources/${osTag(osName)}/ffmpeg/$binaryName"),
                File("src/jvmMain/appResources/${osTag(osName)}/ffmpeg/$binaryName"),
            )
        candidates.firstOrNull { it.exists() && it.canExecute() }?.absolutePath
    }

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
        // For HLS we skip straight to ffmpeg — JCodec can't read m3u8.
        val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("/hls/", ignoreCase = true)

        if (!isHls) {
            val downloaded = runCatching { downloadFirstChunk(url) }.getOrNull()
            if (downloaded != null) {
                try {
                    tryJCodec(downloaded.file)?.let { return it }
                    tryFfmpegFile(downloaded.file)?.let { return it }
                } finally {
                    // Origins that ignore Range: served the full body and we
                    // truncated to MAX_THUMB_BYTES; that file is unsuitable as
                    // a persistent cache hit (decoders may fail on every
                    // retry against a half-MP4). Discard so the next request
                    // re-downloads from scratch.
                    if (!downloaded.persistable) downloaded.file.deleteOrWarn("VideoThumbnailCache", "truncated video chunk")
                }
            }
        }

        return tryFfmpegUrl(url)
    }

    /** Local result of [downloadFirstChunk]: the bytes + whether they're a real Range slice. */
    private data class Download(
        val file: File,
        val persistable: Boolean,
    )

    /**
     * Downloads up to [MAX_THUMB_BYTES] to a cache file, returning the file (or null on failure).
     *
     * Caps the copy regardless of whether the server honours `Range:` — some origins ignore it
     * and serve a 200 with the full body, which would otherwise stream the entire video.
     *
     * Rejects responses whose `Content-Type` starts with `text/` (e.g. HTML error pages from
     * broken origins) so we never persist non-video bytes into the cache.
     *
     * Cleans up zero-byte cache files on failure so a transient empty response isn't sticky.
     */
    private fun downloadFirstChunk(url: String): Download? {
        val hash = sha1Hex(url)
        val cached = File(downloadCacheDir, "$hash.mp4")
        if (cached.length() > 0L) return Download(cached, persistable = true)
        cached.deleteOrWarn("VideoThumbnailCache", "empty cached chunk")

        var wrote = false
        var rangeHonored = false
        DesktopHttpClient.currentClient().newCall(buildRangeRequest(url)).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return null
            val contentType = resp.header("Content-Type")?.lowercase().orEmpty()
            if (contentType.startsWith("text/") || "html" in contentType) return null
            rangeHonored = resp.code == 206
            Files.newOutputStream(cached.toPath()).use { out ->
                val copied = copyAtMost(resp.body.byteStream(), out, MAX_THUMB_BYTES.toLong())
                wrote = copied > 0L
            }
        }
        if (!wrote || cached.length() == 0L) {
            cached.deleteOrWarn("VideoThumbnailCache", "empty cached chunk")
            return null
        }
        return Download(cached, persistable = rangeHonored)
    }

    private fun buildRangeRequest(url: String): Request =
        Request
            .Builder()
            .url(url)
            .header("Range", "bytes=0-${MAX_THUMB_BYTES - 1}")
            .header("User-Agent", "Amethyst-Desktop/thumbnail")
            .build()

    private fun copyAtMost(
        src: java.io.InputStream,
        dst: java.io.OutputStream,
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

    private fun tryJCodec(mp4: File): ImageBitmap? =
        runCatching {
            NIOUtils.readableChannel(mp4).use { ch ->
                val grab = FrameGrab.createFrameGrab(ch).seekToSecondSloppy(1.0)
                val native: Picture = grab.nativeFrame ?: return null
                val rgb = Picture.create(native.width, native.height, ColorSpace.RGB)
                ColorUtil.getTransform(native.color, ColorSpace.RGB).transform(native, rgb)
                bufferedImageToImageBitmap(AWTUtil.toBufferedImage(rgb))
            }
        }.getOrNull()

    private fun tryFfmpegFile(file: File): ImageBitmap? = runFfmpegToImage(file.absolutePath)

    private fun tryFfmpegUrl(url: String): ImageBitmap? = runFfmpegToImage(url)

    /**
     * Spawns `ffmpeg -ss 1 -i <input> -frames:v 1 -f image2pipe -c:v png -an pipe:1`,
     * reads PNG bytes from stdout, decodes with Skia.
     *
     * `redirectError(DISCARD)` so a chatty ffmpeg cannot fill the stderr pipe
     * and stall our `copyTo`. `destroyForcibly()` runs on any unwind so we
     * never leak a ffmpeg process.
     */
    private fun runFfmpegToImage(input: String): ImageBitmap? {
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
        val out = ByteArrayOutputStream(256 * 1024)
        try {
            process.inputStream.use { it.copyTo(out) }
            if (!process.waitFor(8, TimeUnit.SECONDS)) return null
            if (process.exitValue() != 0 || out.size() == 0) return null
        } catch (_: Exception) {
            return null
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        return runCatching {
            Image.makeFromEncoded(out.toByteArray()).toComposeImageBitmap()
        }.getOrNull()
    }

    private fun bufferedImageToImageBitmap(img: BufferedImage): ImageBitmap {
        val baos = ByteArrayOutputStream(64 * 1024)
        ImageIO.write(img, "png", baos)
        return Image.makeFromEncoded(baos.toByteArray()).toComposeImageBitmap()
    }

    private fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun osTag(osName: String): String =
        when {
            "mac" in osName -> "macos"
            "win" in osName -> "windows"
            else -> "linux"
        }
}
