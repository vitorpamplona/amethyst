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
package com.vitorpamplona.amethyst.service.images

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Persistent shadow index of Coil's disk cache: maps each Coil key to its
 * last-known access timestamp.
 *
 * Coil 3's `DiskCache` interface does not expose iteration, so we cannot ask
 * Coil "which keys exist, in LRU order." This class observes every
 * `openEditor` / `openSnapshot` call via [TrackingDiskCache] and writes the
 * pair `(timestamp, key)` to an append-only log. On startup the log is
 * replayed into [accessTimes] so that the trimmer
 * ([CoilDiskTrimmer]) can choose oldest-first eviction candidates without
 * touching Coil internals.
 *
 * Format:
 * ```
 * <epochMs>\t<key>\n
 * <epochMs>\t<key>\n
 * ...
 * ```
 * Multiple lines for the same key are tolerated; the latest wins on replay.
 *
 * Capacity: bounded at [maxEntries]. When exceeded, the oldest entries are
 * dropped from the in-memory map. Those keys become invisible to the trimmer
 * (the trimmer will not call `remove` on them) but Coil's own internal trim
 * remains as a backstop.
 *
 * Compaction: when the on-disk log grows past [compactionRatio] × in-memory
 * size, the log is rewritten as a fresh snapshot.
 */
class KeyAccessLog(
    private val logFile: File,
    private val scope: CoroutineScope,
    private val maxEntries: Int = 200_000,
    private val flushIntervalMs: Long = 30_000L,
    private val compactionRatio: Int = 3,
) {
    companion object {
        private const val TAG = "KeyAccessLog"
    }

    private val accessTimes = ConcurrentHashMap<String, Long>()
    private val pendingWrites = ConcurrentHashMap<String, Long>()
    private val ioMutex = Mutex()
    private val bytesWritten = AtomicLong(0L)
    private val loaded = AtomicLong(0L)

    @Volatile private var flushJob: Job? = null

    /** Reload the in-memory map from the log file. Idempotent. */
    suspend fun load() {
        if (!loaded.compareAndSet(0L, 1L)) return
        withContext(Dispatchers.IO) {
            ioMutex.withLock {
                if (!logFile.exists()) {
                    bytesWritten.set(0L)
                    return@withLock
                }
                var size = 0L
                try {
                    logFile.bufferedReader().useLines { lines ->
                        for (line in lines) {
                            size += line.length + 1
                            val tab = line.indexOf('\t')
                            if (tab <= 0 || tab >= line.length - 1) continue
                            val ts = line.substring(0, tab).toLongOrNull() ?: continue
                            val key = line.substring(tab + 1)
                            val prev = accessTimes[key]
                            if (prev == null || ts > prev) accessTimes[key] = ts
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed reading log; resetting", e)
                    runCatching { logFile.delete() }
                    accessTimes.clear()
                    bytesWritten.set(0L)
                    return@withLock
                }
                bytesWritten.set(size)
                if (accessTimes.size > maxEntries) {
                    dropOldestLocked(accessTimes.size - maxEntries)
                    rewriteLocked()
                }
                Log.d(TAG) { "Loaded ${accessTimes.size} entries (${size}B on disk)" }
            }
        }
    }

    /** Record that [key] was accessed at [timeMs] (defaults to now). */
    fun recordAccess(
        key: String,
        timeMs: Long = System.currentTimeMillis(),
    ) {
        val prev = accessTimes.put(key, timeMs)
        if (prev == null || timeMs - prev >= flushIntervalMs / 2) {
            pendingWrites[key] = timeMs
            scheduleFlush()
        }
    }

    /** Forget a key. Caller is the eviction path (after `remove`). */
    fun forget(key: String) {
        accessTimes.remove(key)
        pendingWrites.remove(key)
    }

    /** Forget every key. Caller is `DiskCache.clear()`. */
    fun forgetAll() {
        accessTimes.clear()
        pendingWrites.clear()
        scope.launch(Dispatchers.IO) {
            ioMutex.withLock {
                runCatching { logFile.delete() }
                bytesWritten.set(0L)
            }
        }
    }

    /** Snapshot of all known (key, lastAccess) pairs, oldest first. */
    fun snapshotOldestFirst(): List<Entry> =
        accessTimes
            .entries
            .map { Entry(it.key, it.value) }
            .sortedBy { it.lastAccessMs }

    fun size(): Int = accessTimes.size

    private fun scheduleFlush() {
        if (flushJob?.isActive == true) return
        synchronized(this) {
            if (flushJob?.isActive == true) return
            flushJob =
                scope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(flushIntervalMs)
                    if (scope.isActive) flushNow()
                }
        }
    }

    /** Flush pending writes immediately. Visible for tests. */
    suspend fun flushNow() {
        val toWrite =
            ioMutex.withLock {
                if (pendingWrites.isEmpty()) return@flushNow
                val drained = pendingWrites.toMap()
                pendingWrites.clear()
                drained
            }
        appendBatch(toWrite)
        maybeCompact()
    }

    private suspend fun appendBatch(batch: Map<String, Long>) {
        if (batch.isEmpty()) return
        ioMutex.withLock {
            try {
                logFile.parentFile?.mkdirs()
                var addedBytes = 0L
                BufferedWriter(FileWriter(logFile, true)).use { writer ->
                    for ((key, ts) in batch) {
                        if ('\n' in key || '\t' in key) continue
                        val line = "$ts\t$key\n"
                        writer.write(line)
                        addedBytes += line.length.toLong()
                    }
                }
                bytesWritten.addAndGet(addedBytes)
            } catch (e: Exception) {
                Log.w(TAG, "appendBatch failed", e)
            }
        }
    }

    private suspend fun maybeCompact() {
        val mapSize = accessTimes.size
        if (mapSize == 0) return
        val avgLine = 80L
        val expectedBytes = mapSize * avgLine
        if (bytesWritten.get() < expectedBytes * compactionRatio) return
        ioMutex.withLock {
            if (accessTimes.size > maxEntries) {
                dropOldestLocked(accessTimes.size - maxEntries)
            }
            rewriteLocked()
        }
    }

    private fun dropOldestLocked(count: Int) {
        if (count <= 0) return
        val victims =
            accessTimes.entries
                .asSequence()
                .sortedBy { it.value }
                .take(count)
                .map { it.key }
                .toList()
        for (k in victims) accessTimes.remove(k)
    }

    private fun rewriteLocked() {
        try {
            logFile.parentFile?.mkdirs()
            val tmp = File(logFile.parentFile, "${logFile.name}.tmp")
            var bytes = 0L
            BufferedWriter(FileWriter(tmp, false)).use { writer ->
                for ((key, ts) in accessTimes) {
                    if ('\n' in key || '\t' in key) continue
                    val line = "$ts\t$key\n"
                    writer.write(line)
                    bytes += line.length.toLong()
                }
            }
            if (!tmp.renameTo(logFile)) {
                tmp.copyTo(logFile, overwrite = true)
                tmp.delete()
            }
            bytesWritten.set(bytes)
        } catch (e: Exception) {
            Log.w(TAG, "rewrite failed", e)
        }
    }

    data class Entry(
        val key: String,
        val lastAccessMs: Long,
    )
}
