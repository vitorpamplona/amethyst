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

import com.sun.net.httpserver.HttpExchange
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.desktop.app.DesktopInstance
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Video bytes the desktop engines have streamed, kept on disk so a video seen again (scrolled back
 * to, reopened, or reached through a flaky host) plays from the machine instead of the network.
 *
 * The engines fetch through [MediaRelay], and they ask for a file in pieces: a 2-byte probe, the whole
 * file, then (when the index sits at the end) its tail and the middle, often abandoning a request
 * halfway. So this keeps whatever byte ranges pass through, each at its own offset in one sparse file
 * per video, with the ranges it holds listed beside it; a request whose range is all there is
 * served from disk, any other goes to the network and fills the file as it passes.
 *
 * Only plain http(s) media is kept. Like the poster cache, nothing that went over Tor and nothing
 * encrypted touches the disk; HLS is left alone (a live playlist must not be replayed). The whole
 * cache stays under [MAX_DISK_BYTES], oldest first out, and a file over [MAX_FILE_BYTES] is not kept.
 */
object VideoDiskCache {
    private const val TAG = "VideoDiskCache"
    private const val MAX_DISK_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_FILE_BYTES = 256L * 1024 * 1024
    private const val TRIM_EVERY_BYTES = 64L * 1024 * 1024
    private const val TRIM_EVERY_MILLIS = 60_000L

    private val dir: File by lazy {
        // Its index lives in memory, so a second copy of the app gets its own (see DesktopInstance).
        DesktopInstance.ownFolder(DesktopInstance.PerCopyFolder.VIDEOS).also { dir ->
            dir.mkdirs()
            try {
                Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("rwx------"))
            } catch (_: UnsupportedOperationException) {
                // Windows: the per-user profile's ACLs already keep it private.
            }
        }
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Whether [url]'s bytes may be kept on disk. */
    fun cacheable(url: String): Boolean =
        (url.startsWith("https://") || url.startsWith("http://")) &&
            !MediaHttp.viaTor(url) &&
            !MediaHttp.isEncrypted(url) &&
            !isHlsMedia(url, null)

    /**
     * Answers a GET for [url] from disk when the whole requested range is there. Returns false,
     * having sent nothing, when it is not (the caller then goes to the network).
     */
    fun serve(
        exchange: HttpExchange,
        url: String,
        rangeHeader: String?,
    ): Boolean {
        val entry = entryFor(url, create = false) ?: return false
        val total = entry.total
        val range = parseRange(rangeHeader, total) ?: return false
        if (!entry.covers(range.first, range.last)) return false

        val length = range.last - range.first + 1
        exchange.responseHeaders.add("Content-Type", entry.contentType)
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        val partial = rangeHeader != null
        if (partial) exchange.responseHeaders.add("Content-Range", "bytes ${range.first}-${range.last}/$total")
        exchange.sendResponseHeaders(if (partial) 206 else 200, length)
        entry.touch()
        // From here the request is answered, however it ends: an engine hanging up mid-body (it
        // abandons most whole-file reads) must not send the caller off to the network for bytes
        // already on disk.
        try {
            RandomAccessFile(entry.data, "r").use { file ->
                file.seek(range.first)
                val buf = ByteArray(64 * 1024)
                var left = length
                exchange.responseBody.use { out ->
                    while (left > 0) {
                        val n = file.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
        } catch (_: IOException) {
            // The engine hung up.
        }
        return true
    }

    /**
     * A sink for bytes of [url] streaming from the network, starting at [start] of a file [total]
     * bytes long; null when they should not be kept.
     */
    fun writer(
        url: String,
        start: Long,
        total: Long,
        contentType: String?,
    ): Writer? {
        if (total <= 0 || total > MAX_FILE_BYTES || start < 0 || start >= total) return null
        val entry = entryFor(url, create = true, total = total, contentType = contentType) ?: return null
        // The server now reports another size: what is on disk is another file.
        if (entry.total != total) {
            entry.discard()
            entries.remove(url)
            return writer(url, start, total, contentType)
        }
        return Writer(entry, start)
    }

    /**
     * How many of [url]'s first bytes are on disk without a gap, and how long the whole file is; null
     * when none of it is.
     */
    fun heldStart(url: String): Pair<Long, Long>? = entryFor(url, create = false)?.let { it.heldFromStart() to it.total }

    /**
     * Copies [url]'s first bytes on disk, up to [max] and without a gap, into [target], and says how
     * many it copied (0 when it holds none). For a poster made from what an engine already streamed.
     */
    fun copyStart(
        url: String,
        target: File,
        max: Long,
    ): Long = entryFor(url, create = false)?.copyStart(target, max) ?: 0L

    class Writer internal constructor(
        private val entry: Entry,
        private var position: Long,
    ) {
        fun write(
            buf: ByteArray,
            count: Int,
        ) {
            if (count <= 0) return
            entry.write(position, buf, count)
            position += count
            writtenSinceTrim.addAndGet(count.toLong())
        }

        fun close() {
            entry.persist()
            trimNowAndThen()
        }
    }

    /** The first and last byte of [header] within a file [total] bytes long; the whole file without one. */
    internal fun parseRange(
        header: String?,
        total: Long,
    ): LongRange? {
        if (total <= 0) return null
        if (header == null) return 0L until total
        val spec = header.removePrefix("bytes=").takeIf { it != header && ',' !in it } ?: return null
        val dash = spec.indexOf('-').takeIf { it >= 0 } ?: return null
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()
        return if (startText.isEmpty()) {
            // A suffix: the last N bytes.
            val n = endText.toLongOrNull()?.takeIf { it > 0 } ?: return null
            maxOf(0L, total - n) until total
        } else {
            val start = startText.toLongOrNull()?.takeIf { it in 0 until total } ?: return null
            val end = if (endText.isEmpty()) total - 1 else (endText.toLongOrNull() ?: return null).coerceAtMost(total - 1)
            if (end < start) null else start..end
        }
    }

    private fun entryFor(
        url: String,
        create: Boolean,
        total: Long = 0,
        contentType: String? = null,
    ): Entry? {
        entries[url]?.let { return it }
        val key = sha256Hex(url)
        val loaded = Entry.load(File(dir, "$key.data"), File(dir, "$key.meta"))
        if (loaded != null) return entries.putIfAbsent(url, loaded) ?: loaded
        if (!create) return null
        val fresh = Entry(File(dir, "$key.data"), File(dir, "$key.meta"), total, contentType ?: "video/mp4")
        return entries.putIfAbsent(url, fresh) ?: fresh
    }

    // Bytes written since the last trim, and when that was: trimming lists and stats every file, so it
    // runs after a fair amount of writing or a while, not after each of the engine's many requests.
    private val writtenSinceTrim = AtomicLong(0)
    private val lastTrimMillis = AtomicLong(0)

    private fun trimNowAndThen() {
        val now = System.currentTimeMillis()
        val last = lastTrimMillis.get()
        if (writtenSinceTrim.get() < TRIM_EVERY_BYTES && now - last < TRIM_EVERY_MILLIS) return
        if (!lastTrimMillis.compareAndSet(last, now)) return
        writtenSinceTrim.set(0)
        trim()
    }

    private fun trim() {
        val metas = dir.listFiles { f -> f.name.endsWith(".meta") }?.toList() ?: return
        var used = 0L
        metas.sortedByDescending { it.lastModified() }.forEach { meta ->
            val data = File(meta.path.removeSuffix(".meta") + ".data")
            used += data.length()
            if (used > MAX_DISK_BYTES) {
                entries.entries.removeIf { it.value.data == data }
                data.delete()
                meta.delete()
            }
        }
    }

    private fun sha256Hex(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * One video on disk: [data] holds its bytes at their own offsets (a sparse file, up to [total]
     * bytes long), and [meta] lists its size, type and the byte ranges written so far.
     */
    class Entry internal constructor(
        val data: File,
        private val meta: File,
        val total: Long,
        val contentType: String,
        initialSpans: List<LongRange> = emptyList(),
    ) {
        // Held byte ranges, sorted and merged. Guarded by this.
        private val spans = ArrayList(initialSpans)
        private var file: FileChannel? = null

        @Synchronized
        fun covers(
            first: Long,
            last: Long,
        ): Boolean = spans.any { first >= it.first && last <= it.last }

        /** How many bytes from the start of the file it holds without a gap. */
        @Synchronized
        fun heldFromStart(): Long = spans.firstOrNull()?.takeIf { it.first == 0L }?.let { it.last + 1 } ?: 0L

        /** Copies its first bytes, up to [max] and without a gap, into [target]; how many it copied. */
        fun copyStart(
            target: File,
            max: Long,
        ): Long {
            val length = minOf(heldFromStart(), max)
            if (length <= 0) return 0
            FileChannel.open(data.toPath(), StandardOpenOption.READ).use { src ->
                FileChannel.open(target.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { dst ->
                    var at = 0L
                    while (at < length) {
                        val n = src.transferTo(at, length - at, dst)
                        if (n <= 0) break
                        at += n
                    }
                    return at
                }
            }
        }

        @Synchronized
        fun write(
            position: Long,
            buf: ByteArray,
            count: Int,
        ) {
            if (position + count > total) return
            // Not sized up front: a piece written far in (the engine reads a file's tail early) leaves
            // a hole, which takes no disk where the file is sparse. A new file asks for that, which
            // NTFS needs; APFS and ext4 do it anyway.
            val f =
                file ?: run {
                    val path = data.toPath()
                    if (Files.exists(path)) {
                        FileChannel.open(path, StandardOpenOption.WRITE)
                    } else {
                        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SPARSE)
                    }
                }.also { file = it }
            val bytes = ByteBuffer.wrap(buf, 0, count)
            var at = position
            while (bytes.hasRemaining()) at += f.write(bytes, at)
            add(position, position + count - 1)
        }

        // Inserts a range and merges it with any it touches.
        private fun add(
            first: Long,
            last: Long,
        ) {
            var lo = first
            var hi = last
            val it = spans.iterator()
            while (it.hasNext()) {
                val r = it.next()
                if (r.last + 1 >= lo && r.first <= hi + 1) {
                    lo = minOf(lo, r.first)
                    hi = maxOf(hi, r.last)
                    it.remove()
                }
            }
            spans.add(lo..hi)
            spans.sortBy { it.first }
        }

        @Synchronized
        fun persist() {
            runCatching {
                file?.close()
                file = null
                val text =
                    buildString {
                        appendLine(total)
                        appendLine(contentType)
                        spans.forEach { appendLine("${it.first} ${it.last}") }
                    }
                val tmp = File(meta.path + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(meta)
            }.onFailure { Log.w(TAG, "Could not save ${meta.name}", it) }
        }

        fun touch() {
            meta.setLastModified(System.currentTimeMillis())
        }

        @Synchronized
        fun discard() {
            runCatching { file?.close() }
            file = null
            data.delete()
            meta.delete()
        }

        companion object {
            fun load(
                data: File,
                meta: File,
            ): Entry? {
                if (!meta.isFile || !data.isFile) return null
                return runCatching {
                    val lines = meta.readLines()
                    val total = lines[0].trim().toLong()
                    val type = lines[1].trim()
                    val spans =
                        lines.drop(2).mapNotNull { line ->
                            val parts = line.trim().split(' ')
                            if (parts.size == 2) parts[0].toLong()..parts[1].toLong() else null
                        }
                    // Shorter than the video is normal: the file ends where its furthest piece does.
                    if (data.length() > total || spans.any { it.last >= data.length() }) null else Entry(data, meta, total, type, spans)
                }.getOrNull()
            }
        }
    }
}
