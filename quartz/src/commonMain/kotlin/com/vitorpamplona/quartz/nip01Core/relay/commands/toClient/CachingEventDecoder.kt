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
import com.vitorpamplona.quartz.utils.cache.ConcurrentHashCache
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

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
 * The id → Event cache is generational: two lock-free concurrent maps
 * ([ConcurrentHashCache]: `ConcurrentHashMap` on JVM/Android), rotated when
 * the live one reaches [capacity]. Frames arrive from every relay's consumer
 * coroutine concurrently; the hit path is entirely lock-free, and the
 * rotation races are DELIBERATELY tolerated because every one of them is
 * benign — the worst outcome is always a redundant re-parse, never a wrong
 * message:
 *  - two threads rotating at once: one generation of ids is dropped early;
 *  - inserting into a map that just became `previous`: still found by the
 *    two-generation lookup until the next rotation;
 *  - two threads first-seeing the same id simultaneously: both full-parse.
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
    @Volatile private var live = ConcurrentHashCache<HexKey, Event>()

    @Volatile private var previous = ConcurrentHashCache<HexKey, Event>()

    // Observability + benchmark hooks.
    private val parsed = AtomicLong(0)
    private val reused = AtomicLong(0)

    val parsedCount: Long get() = parsed.load()
    val reusedCount: Long get() = reused.load()

    override fun decode(text: String): Message {
        val scanned = scanEventFrame(text)
        if (scanned != null) {
            val cached = live.get(scanned.eventId) ?: previous.get(scanned.eventId)
            if (cached != null) {
                reused.incrementAndFetch()
                return EventMessage(scanned.subId, cached)
            }
        }

        val msg = fullParser.decode(text)
        if (msg is EventMessage) {
            parsed.incrementAndFetch()
            if (live.size() >= capacity) {
                // Benign-race rotation: see class kdoc.
                previous = live
                live = ConcurrentHashCache()
            }
            live.put(msg.event.id, msg.event)
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
