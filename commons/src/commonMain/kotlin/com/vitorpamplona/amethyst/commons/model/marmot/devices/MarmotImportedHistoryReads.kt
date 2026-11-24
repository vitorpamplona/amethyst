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
package com.vitorpamplona.amethyst.commons.model.marmot.devices

import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * How far each chat's imported device-group history counts as read (`account-sync.md` "History
 * deliveries"): the newest imported message's `created_at`, never later than when it was imported.
 *
 * Imported history is old news: it stays in Notifications by its own dates, but lights no unread
 * badge and raises no notification. marmot-kmp writes it to the message store as it imports it, so
 * if this were only the chat's in-memory last-read time, a process death, a logout or a cancel
 * before the app shows the import would restore those messages as ordinary unread ones. Written
 * when [MarmotDevices] takes the import from the device group, before it lets go of its lock, and
 * applied to the chat's last-read time on every start.
 *
 * Kept in [blobs] (encrypted at rest by the app) as one `<chat id> <read up to>` line per chat;
 * null keeps it for this run only.
 */
class MarmotImportedHistoryReads(
    private val blobs: DeviceGroupBlobs?,
) {
    private val mutex = Mutex()
    private var loaded: MutableMap<HexKey, Long>? = null

    /** Each chat's read-up-to time, as saved. */
    suspend fun load(): Map<HexKey, Long> = mutex.withLock { readLocked().toMap() }

    /** Moves each chat of [imports] to its newest imported message, clamped to [now], and saves. */
    suspend fun record(
        imports: List<MarmotDeviceGroupManager.ImportedHistory>,
        now: Long,
    ) = mutex.withLock {
        val reads = readLocked()
        var changed = false
        for (history in imports) {
            val upTo = importedHistoryReadUpTo(history.events.map { it.createdAt }, now) ?: continue
            if ((reads[history.nostrGroupId] ?: 0L) >= upTo) continue
            reads[history.nostrGroupId] = upTo
            changed = true
        }
        if (changed) saveLocked(reads)
    }

    private suspend fun readLocked(): MutableMap<HexKey, Long> {
        loaded?.let { return it }
        val bytes =
            try {
                blobs?.load(RECORD)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "could not read the imported history read times", e)
                null
            }
        val reads = HashMap<HexKey, Long>()
        bytes?.decodeToString()?.lineSequence()?.forEach { line ->
            val id = line.substringBefore(' ')
            val at = line.substringAfter(' ', "").toLongOrNull()
            if (id.isNotEmpty() && at != null) reads[id] = at
        }
        loaded = reads
        return reads
    }

    private suspend fun saveLocked(reads: Map<HexKey, Long>) {
        val target = blobs ?: return
        val text = reads.entries.sortedBy { it.key }.joinToString("") { "${it.key} ${it.value}\n" }
        try {
            target.save(RECORD, text.encodeToByteArray())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "could not save the imported history read times", e)
        }
    }

    companion object {
        private const val TAG = "MarmotImportedHistoryReads"
        const val RECORD = "marmot_imported_history_reads"
    }
}

/**
 * The last-read time an import of messages created at [createdAts] moves its chat to: the newest of
 * them, but never past [now]. `created_at` is the sender's to set, so one message dated years ahead
 * would otherwise silence the chat, its badge and its notifications until then.
 */
fun importedHistoryReadUpTo(
    createdAts: List<Long>,
    now: Long,
): Long? = createdAts.maxOrNull()?.let { minOf(it, now) }
