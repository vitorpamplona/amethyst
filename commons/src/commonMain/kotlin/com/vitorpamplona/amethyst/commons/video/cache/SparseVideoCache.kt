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
package com.vitorpamplona.amethyst.commons.video.cache

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.deleteQuietly
import com.vitorpamplona.amethyst.commons.util.renameQuietly
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * A disk cache of video files that holds any parts of a file, not just whole ones: a player that
 * seeks to the end of an mp4 for its `moov` box, or a prefetch that took only the first bytes,
 * leaves those bytes here, and the next read takes them from disk and fetches only the gaps.
 *
 * Each URL is two files in [directory]: `<sha256>.data`, written at the offsets the bytes belong
 * to (the gaps stay holes in a sparse file), and `<sha256>.meta`, its [VideoCacheEntry]. The index
 * of every entry lives in memory and is read from the `.meta` files on first use, so open the
 * cache from a background thread.
 *
 * Least recently used entries go once the cache holds more than [maxBytes]; an entry in use
 * ([acquire] without its [release]) is never removed. Every call is thread-safe and blocks on disk.
 */
class SparseVideoCache(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val maxBytes: Long,
    private val now: () -> Long = { TimeUtils.nowMillis() },
) {
    private val lock = KmpLock()

    // Guarded by [lock]: null until the index is read from disk.
    private var entries: LinkedHashMap<String, Slot>? = null
    private var totalBytes = 0L

    private class Slot(
        val entry: VideoCacheEntry,
        val name: String,
    ) {
        var users = 0
        var handle: FileHandle? = null

        // Bytes written since the .meta file was last saved.
        var unsavedBytes = 0L
    }

    /** How many bytes the cache holds, across every entry. */
    fun totalBytes(): Long = withIndex { totalBytes }

    /**
     * Marks [url] in use and its entry most recently used, creating the entry if there is none.
     * Pair every call with [release]; [write] needs the entry acquired.
     */
    fun acquire(url: String) {
        withIndex { index ->
            val slot = index.getOrPut(url) { Slot(VideoCacheEntry(url), fileNameOf(url)) }
            slot.users++
            slot.entry.lastAccess = now()
            if (slot.handle == null) {
                slot.handle =
                    try {
                        fileSystem.openReadWrite(dataPath(slot.name))
                    } catch (e: IOException) {
                        Log.w(TAG, "Could not open the cache file of $url", e)
                        null
                    }
            }
        }
    }

    /** Ends one [acquire]: saves what was learned and, once nobody uses the entry, closes its file. */
    fun release(url: String) {
        withIndex { index ->
            val slot = index[url] ?: return@withIndex
            slot.users = (slot.users - 1).coerceAtLeast(0)
            save(slot)
            if (slot.users == 0) {
                closeQuietly(slot)
                evictIfNeeded(index)
            }
        }
    }

    /** The full length of [url]'s file, once a server has said it. */
    fun length(url: String): Long? = withIndex { it[url]?.entry?.length }

    /** The content type [url]'s server sent. */
    fun mimeType(url: String): String? = withIndex { it[url]?.entry?.mimeType }

    /** Records what a server said of [url]'s file. A length shorter than what is stored cuts the extra off. */
    fun setInfo(
        url: String,
        length: Long?,
        mimeType: String?,
    ) {
        withIndex { index ->
            val slot = index[url] ?: return@withIndex
            val entry = slot.entry
            var changed = false
            if (length != null && length >= 0 && entry.length != length) {
                entry.length = length
                val before = entry.ranges.cachedBytes
                entry.ranges.truncate(length)
                totalBytes -= before - entry.ranges.cachedBytes
                changed = true
            }
            if (!mimeType.isNullOrBlank() && entry.mimeType != mimeType) {
                entry.mimeType = mimeType
                changed = true
            }
            if (changed) save(slot)
        }
    }

    /** Where the stored run of [url] that holds [offset] ends; [offset] when that byte is not stored. */
    fun runEnd(
        url: String,
        offset: Long,
    ): Long = withIndex { it[url]?.entry?.ranges?.runEnd(offset) ?: offset }

    /** The start of the next stored span of [url] past [offset], or null when none is stored. */
    fun nextCachedStart(
        url: String,
        offset: Long,
    ): Long? = withIndex { it[url]?.entry?.ranges?.nextStartAfter(offset) }

    /** Whether every byte of `[start, end)` of [url] is stored. */
    fun covers(
        url: String,
        start: Long,
        end: Long,
    ): Boolean = withIndex { it[url]?.entry?.ranges?.covers(start, end) ?: (end <= start) }

    /**
     * Stores [byteCount] bytes of [bytes] as [url]'s bytes at [offset]. Bytes past the file's known
     * length are dropped. [url] must be acquired.
     */
    fun write(
        url: String,
        offset: Long,
        bytes: ByteArray,
        byteCount: Int = bytes.size,
    ) {
        if (byteCount <= 0) return
        val (handle, count) =
            withIndex { index ->
                val slot = index[url] ?: return
                val handle = slot.handle ?: return
                val length = slot.entry.length
                val count = if (length != null) minOf(byteCount.toLong(), length - offset).toInt() else byteCount
                handle to count
            }
        if (count <= 0) return
        try {
            handle.write(offset, bytes, 0, count)
        } catch (e: IOException) {
            // A full disk: the bytes still reach the player, they just are not kept.
            Log.w(TAG, "Could not store bytes of $url", e)
            return
        }
        withIndex { index ->
            val slot = index[url] ?: return@withIndex
            val ranges = slot.entry.ranges
            val before = ranges.cachedBytes
            ranges.add(offset, offset + count)
            val added = ranges.cachedBytes - before
            totalBytes += added
            slot.unsavedBytes += added
            if (slot.unsavedBytes >= SAVE_EVERY_BYTES) save(slot)
            if (totalBytes > maxBytes) evictIfNeeded(index)
        }
    }

    /**
     * Up to [byteCount] stored bytes of [url] from [offset], stopping where the stored run ends;
     * empty when the byte at [offset] is not stored or cannot be read.
     */
    fun read(
        url: String,
        offset: Long,
        byteCount: Int,
    ): ByteArray {
        val (handle, count, temporary) =
            withIndex { index ->
                val slot = index[url] ?: return EMPTY
                val available = slot.entry.ranges.runEnd(offset) - offset
                if (available <= 0) return EMPTY
                val count = minOf(available, byteCount.toLong()).toInt()
                val handle = slot.handle
                if (handle != null) {
                    Triple(handle, count, false)
                } else {
                    val opened =
                        try {
                            fileSystem.openReadOnly(dataPath(slot.name))
                        } catch (e: IOException) {
                            Log.w(TAG, "Could not open the cache file of $url", e)
                            return EMPTY
                        }
                    Triple(opened, count, true)
                }
            }
        try {
            val buffer = ByteArray(count)
            var done = 0
            while (done < count) {
                val n = handle.read(offset + done, buffer, done, count - done)
                if (n <= 0) break
                done += n
            }
            if (done < count) {
                // The file is shorter than its index says (cut short by a crash): forget the rest.
                withIndex { index -> index[url]?.let { forgetFrom(it, offset + done) } }
                return buffer.copyOf(done)
            }
            return buffer
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the cache file of $url", e)
            return EMPTY
        } finally {
            if (temporary) handle.close()
        }
    }

    private fun forgetFrom(
        slot: Slot,
        offset: Long,
    ) {
        val before = slot.entry.ranges.cachedBytes
        slot.entry.ranges.truncate(offset)
        totalBytes -= before - slot.entry.ranges.cachedBytes
        save(slot)
    }

    /** Callers hold [lock]. Removes least recently used entries nobody uses until the cache fits. */
    private fun evictIfNeeded(index: LinkedHashMap<String, Slot>) {
        if (totalBytes <= maxBytes) return
        val candidates = index.values.filter { it.users == 0 }.sortedBy { it.entry.lastAccess }
        for (slot in candidates) {
            if (totalBytes <= maxBytes) break
            remove(index, slot)
        }
    }

    private fun remove(
        index: LinkedHashMap<String, Slot>,
        slot: Slot,
    ) {
        closeQuietly(slot)
        index.remove(slot.entry.url)
        totalBytes -= slot.entry.ranges.cachedBytes
        if (!fileSystem.deleteQuietly(dataPath(slot.name))) Log.w(TAG) { "Could not delete ${slot.name}.data" }
        if (!fileSystem.deleteQuietly(metaPath(slot.name))) Log.w(TAG) { "Could not delete ${slot.name}.meta" }
    }

    private fun closeQuietly(slot: Slot) {
        val handle = slot.handle ?: return
        slot.handle = null
        try {
            handle.close()
        } catch (e: IOException) {
            Log.w(TAG, "Could not close ${slot.name}.data", e)
        }
    }

    /** Callers hold [lock]. Writes the entry to a temporary file and moves it over the old one. */
    private fun save(slot: Slot) {
        slot.unsavedBytes = 0
        val target = metaPath(slot.name)
        val temp = directory / "${slot.name}.meta.tmp"
        try {
            fileSystem.write(temp) { writeUtf8(slot.entry.encode()) }
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the index of ${slot.entry.url}", e)
            return
        }
        if (!fileSystem.renameQuietly(temp, target)) Log.w(TAG) { "Could not save the index of ${slot.entry.url}" }
    }

    private inline fun <T> withIndex(block: (LinkedHashMap<String, Slot>) -> T): T =
        lock.withLock {
            val index = entries ?: load().also { entries = it }
            block(index)
        }

    /** Callers hold [lock]. Reads every `.meta` file; drops data with no index and indexes with no data. */
    private fun load(): LinkedHashMap<String, Slot> {
        val index = LinkedHashMap<String, Slot>()
        totalBytes = 0
        try {
            fileSystem.createDirectories(directory)
        } catch (e: IOException) {
            Log.w(TAG, "Could not create the video cache at $directory", e)
            return index
        }
        val files =
            try {
                fileSystem.list(directory)
            } catch (e: IOException) {
                Log.w(TAG, "Could not list the video cache at $directory", e)
                return index
            }
        val names = files.map { it.name }.toSet()
        for (file in files) {
            val name = file.name
            when {
                name.endsWith(".meta") -> {
                    val base = name.removeSuffix(".meta")
                    val entry =
                        try {
                            VideoCacheEntry.decode(fileSystem.read(file) { readUtf8() })
                        } catch (e: IOException) {
                            null
                        }
                    val dataSize = fileSystem.metadataOrNull(dataPath(base))?.size
                    if (entry == null || dataSize == null || base != fileNameOf(entry.url)) {
                        if (!fileSystem.deleteQuietly(file)) Log.w(TAG) { "Could not delete $name" }
                        if (dataSize != null && !fileSystem.deleteQuietly(dataPath(base))) Log.w(TAG) { "Could not delete $base.data" }
                        continue
                    }
                    // Bytes the index lists past the end of the file were never written.
                    if (entry.ranges.maxEnd > dataSize) entry.ranges.truncate(dataSize)
                    index[entry.url] = Slot(entry, base)
                    totalBytes += entry.ranges.cachedBytes
                }

                name.endsWith(".data") -> {
                    if ("${name.removeSuffix(".data")}.meta" !in names && !fileSystem.deleteQuietly(file)) Log.w(TAG) { "Could not delete $name" }
                }

                name.endsWith(".tmp") -> {
                    if (!fileSystem.deleteQuietly(file)) Log.w(TAG) { "Could not delete $name" }
                }
            }
        }
        Log.d(TAG) { "Video cache: ${index.size} files, ${totalBytes / (1024 * 1024)} MB of ${maxBytes / (1024 * 1024)} MB" }
        evictIfNeeded(index)
        return index
    }

    private fun dataPath(name: String) = directory / "$name.data"

    private fun metaPath(name: String) = directory / "$name.meta"

    private fun fileNameOf(url: String): String = sha256(url.encodeToByteArray()).toHexKey()

    companion object {
        private const val TAG = "SparseVideoCache"
        private val EMPTY = ByteArray(0)

        // How many new bytes an entry takes before its index is saved again, besides on release.
        private const val SAVE_EVERY_BYTES = 2L * 1024 * 1024
    }
}
