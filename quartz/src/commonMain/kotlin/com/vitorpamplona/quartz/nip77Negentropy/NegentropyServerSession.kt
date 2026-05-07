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
package com.vitorpamplona.quartz.nip77Negentropy

import com.vitorpamplona.negentropy.Negentropy
import com.vitorpamplona.negentropy.storage.StorageVector
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.utils.Hex

/**
 * Server-side handler for NIP-77 negentropy reconciliation.
 *
 * Used when acting as a relay (or relay-relay sync) to respond to
 * incoming NEG-OPEN and NEG-MSG from a client.
 *
 * The constructor takes [IdAndTime] entries (just `created_at` and the
 * 32-byte event id) to keep the per-session footprint at ~40 B/entry —
 * matching strfry's `MemoryView` path. A [List]<Event> overload is
 * kept for callers (and tests) that already hold full events.
 *
 * Usage:
 * 1. On NEG-OPEN: create a [NegentropyServerSession] with the matching local entries
 * 2. Call [processMessage] with the initial hex message from NEG-OPEN
 * 3. Send back the resulting [NegMsgMessage]
 * 4. On subsequent NEG-MSG: call [processMessage] again and send the response
 *
 * @param frameSizeLimit max bytes per NEG-MSG response (raw payload,
 *   before hex). Default `500_000` matches strfry's hard-coded
 *   `Negentropy ne(storage, 500'000)` so a single round-trip carries
 *   the same payload as strfry's reconciliation.
 */
class NegentropyServerSession(
    val subId: String,
    localEntries: List<IdAndTime>,
    frameSizeLimit: Long = DEFAULT_FRAME_SIZE_LIMIT,
) {
    private val storage = StorageVector()
    private val negentropy: Negentropy

    init {
        for (entry in localEntries) {
            storage.insert(entry.createdAt, entry.id)
        }
        storage.seal()
        negentropy = Negentropy(storage, frameSizeLimit)
    }

    companion object {
        /**
         * strfry parity: `Negentropy ne(storage, 500'000)` in
         * `RelayNegentropy.cpp`. Hex-encoded that's ~1 MB on the wire
         * per NEG-MSG, the de-facto sync round-trip size.
         */
        const val DEFAULT_FRAME_SIZE_LIMIT: Long = 500_000L

        /**
         * Convenience for callers that hold full [Event] objects
         * (mostly tests + relay-relay sync paths). Production server
         * code should call the [IdAndTime] constructor directly via
         * `IEventStore.snapshotIdsForNegentropy` to avoid the full
         * Event materialisation that this projection collapses.
         */
        fun fromEvents(
            subId: String,
            localEvents: List<Event>,
            frameSizeLimit: Long = DEFAULT_FRAME_SIZE_LIMIT,
        ): NegentropyServerSession =
            NegentropyServerSession(
                subId = subId,
                localEntries = localEvents.map { IdAndTime(it.createdAt, it.id) },
                frameSizeLimit = frameSizeLimit,
            )
    }

    fun processMessage(hexMessage: String): NegMsgMessage? {
        val msgBytes = Hex.decode(hexMessage)
        val result = negentropy.reconcile(msgBytes)
        return if (result.msg != null) {
            NegMsgMessage(
                subId = subId,
                message = Hex.encode(result.msg!!),
            )
        } else {
            null
        }
    }
}
