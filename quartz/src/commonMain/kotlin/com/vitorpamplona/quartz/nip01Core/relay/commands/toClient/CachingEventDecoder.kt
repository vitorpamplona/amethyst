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
package com.vitorpamplona.quartz.nip01Core.relay.commands.toClient

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A [MessageDecoder] that skips the full JSON parse for EVENT frames whose
 * event was already parsed once.
 *
 * Why: duplicates are a large share of real relay traffic — the same event
 * arrives once per matching subscription AND once per connected relay
 * (production measurements: 14–57% duplicate frames; see
 * `quartz/plans/2026-07-02-nostrclient-receiver-perf.md`). Each duplicate
 * costs a full JSON parse (~3–30µs depending on event size) even though the
 * client already holds the parsed [Event]. This decoder scans the frame for
 * the event id (~0.3µs), and on a cache hit synthesizes the [EventMessage]
 * from the cached [Event] with the *frame's own* subscription id — so every
 * subscription still receives its delivery, per-relay bookkeeping still
 * happens, and dispatch semantics are IDENTICAL to a full parse. Only the
 * redundant parse is skipped.
 *
 * Safety rules — the scan must never mis-attribute a frame, so it bails to a
 * full parse whenever anything is unusual:
 *  - the frame must start exactly with `["EVENT","`;
 *  - the subscription id must contain no escapes;
 *  - the id must be found as the literal `"id":"` key with a 64-lowercase-hex
 *    value. Valid JSON guarantees this substring cannot occur inside a string
 *    value (inner quotes are escaped as `\"`, e.g. kind-6 reposts embedding
 *    full event JSON in `content`), so a match is the top-level id key.
 *
 * The id → Event cache is generational: two maps, rotated when the live one
 * reaches [capacity]. A rotation only causes a re-parse (never a wrong
 * message). Thread-safe: frames arrive from every relay's consumer coroutine;
 * map access is guarded by a tiny spin lock (lookups/inserts, never I/O).
 *
 * Note the trust model is unchanged: events are identified by id here exactly
 * like in the app-level dedup (LocalCache), and signatures are verified
 * downstream regardless of which copy the Event object came from.
 */
@OptIn(ExperimentalAtomicApi::class)
class CachingEventDecoder(
    private val capacity: Int = 2048,
    private val fullParser: MessageDecoder = MessageDecoder.Default,
) : MessageDecoder {
    private val lock = AtomicBoolean(false)
    private var live = HashMap<HexKey, Event>(capacity)
    private var previous = HashMap<HexKey, Event>(0)

    // Observability + benchmark hooks.
    var parsedCount: Long = 0
        private set
    var reusedCount: Long = 0
        private set

    private inline fun <R> locked(block: () -> R): R {
        while (lock.exchange(true)) {
            while (lock.load()) { }
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }

    override fun decode(text: String): Message {
        val scanned = scanEventFrame(text)
        if (scanned != null) {
            val cached =
                locked {
                    val hit = live[scanned.eventId] ?: previous[scanned.eventId]
                    if (hit != null) reusedCount++
                    hit
                }
            if (cached != null) {
                return EventMessage(scanned.subId, cached)
            }
        }

        val msg = fullParser.decode(text)
        if (msg is EventMessage) {
            locked {
                parsedCount++
                if (live.size >= capacity) {
                    previous = live
                    live = HashMap(capacity)
                }
                live[msg.event.id] = msg.event
            }
        }
        return msg
    }

    private class ScannedEvent(
        val subId: String,
        val eventId: HexKey,
    )

    /**
     * Extracts (subId, eventId) from a compact `["EVENT","<subId>",{...}]`
     * frame, or null when the frame isn't an EVENT or anything about it is
     * unusual (whitespace variants, escaped subIds, missing/malformed id) —
     * null means "do the full parse".
     */
    private fun scanEventFrame(text: String): ScannedEvent? {
        if (!text.startsWith(EVENT_PREFIX)) return null

        // subId: read up to the closing quote; any escape → bail.
        var i = EVENT_PREFIX.length
        val subStart = i
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') return null
            if (c == '"') break
            i++
        }
        if (i >= text.length) return null
        val subId = text.substring(subStart, i)

        // id: the literal top-level key with a 64-char lowercase-hex value.
        val idKey = text.indexOf(ID_KEY, i)
        if (idKey < 0) return null
        val idStart = idKey + ID_KEY.length
        val idEnd = idStart + 64
        if (idEnd >= text.length || text[idEnd] != '"') return null
        for (j in idStart until idEnd) {
            val c = text[j]
            if (c !in '0'..'9' && c !in 'a'..'f') return null
        }
        return ScannedEvent(subId, text.substring(idStart, idEnd))
    }

    companion object {
        private const val EVENT_PREFIX = "[\"EVENT\",\""
        private const val ID_KEY = "\"id\":\""
    }
}
