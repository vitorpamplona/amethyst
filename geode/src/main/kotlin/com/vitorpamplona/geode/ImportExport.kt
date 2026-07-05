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
package com.vitorpamplona.geode

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/**
 * Bulk NDJSON import/export for a geode store — the `geode import` / `geode export`
 * verbs, geode's equivalent of `strfry import` / `strfry export`. One JSON event
 * per line (the same on-the-wire event object, no envelope), which is the de-facto
 * interchange format across relays (strfry dumps, corpus files, backups).
 *
 * Both directions stream: memory is bounded to one batch (import) or one event
 * (export) regardless of corpus size, so a multi-million-event dump round-trips in
 * roughly constant memory.
 */
object ImportExport {
    /** Events per [IEventStore.batchInsert]; one transaction per batch. */
    const val BATCH = 10_000

    class ImportStats(
        /** Non-blank lines read. */
        val read: Long,
        /** Events newly stored. */
        val imported: Long,
        /** Events the store rejected — overwhelmingly duplicates (unique-id). */
        val rejected: Long,
        /** Events dropped for a bad signature (only when verifying). */
        val invalid: Long,
        /** Lines that didn't parse as a NIP-01 event. */
        val malformed: Long,
    ) {
        operator fun plus(o: ImportStats) = ImportStats(read + o.read, imported + o.imported, rejected + o.rejected, invalid + o.invalid, malformed + o.malformed)

        companion object {
            val ZERO = ImportStats(0, 0, 0, 0, 0)
        }
    }

    /**
     * Reads one JSON event per line from [lines] and batch-inserts them into
     * [store]. When [verify], each event's Schnorr signature is checked with the
     * same `Event.verify()` the relay's `VerifyPolicy` uses, and a bad signature
     * is counted ([ImportStats.invalid]) and skipped — so `import` upholds the
     * relay's verify-by-default stance rather than trusting the file. Duplicates
     * are dropped by the store's unique-id constraint and counted as
     * [ImportStats.rejected].
     */
    suspend fun import(
        store: IEventStore,
        lines: Sequence<String>,
        verify: Boolean,
        batchSize: Int = BATCH,
    ): ImportStats {
        var read = 0L
        var imported = 0L
        var rejected = 0L
        var invalid = 0L
        var malformed = 0L
        val batch = ArrayList<Event>(batchSize)

        suspend fun flush() {
            if (batch.isEmpty()) return
            for (outcome in store.batchInsert(batch)) {
                when (outcome) {
                    IEventStore.InsertOutcome.Accepted -> imported++
                    is IEventStore.InsertOutcome.Rejected -> rejected++
                }
            }
            batch.clear()
        }

        for (line in lines) {
            if (line.isBlank()) continue
            read++
            val event = runCatching { OptimizedJsonMapper.fromJson(line) }.getOrNull()
            if (event == null) {
                malformed++
                continue
            }
            if (verify && !event.verify()) {
                invalid++
                continue
            }
            batch.add(event)
            if (batch.size >= batchSize) flush()
        }
        flush()
        return ImportStats(read, imported, rejected, invalid, malformed)
    }

    /**
     * Streams every stored event as NDJSON (one compact JSON object per line, no
     * trailing whitespace) to [out], newest-first. Returns the count written.
     */
    suspend fun export(
        store: IEventStore,
        out: Appendable,
    ): Long {
        var n = 0L
        store.query<Event>(Filter()) { event ->
            out.append(event.toJson()).append('\n')
            n++
        }
        return n
    }
}
