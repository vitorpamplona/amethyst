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
package com.vitorpamplona.quartz.nip01Core.cache.interning

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * Process-wide interner that canonicalises [Event] instances by id so
 * every consumer (relay client, store deserialization, projections,
 * tests) sees the same object reference for the same event id.
 *
 * Backed by weak references — entries vanish when no live consumer
 * holds the event, so the cache only grows as long as projections /
 * UI state actually need the events. Sized for ~5000 hot events; the
 * underlying map resizes if usage exceeds that.
 *
 * Use [intern] on every event arrival path. The first occurrence wins
 * and becomes canonical; subsequent equivalent decodes return that
 * canonical instance.
 *
 * Platforms without weak references fall back to a passthrough that
 * returns [event] unchanged — no canonicalisation, but no leaks
 * either.
 */
expect class EventInterner() {
    /**
     * Returns the canonical [Event] for [event]'s id. If a live
     * canonical instance already exists for this id, returns it;
     * otherwise stores [event] as the new canonical and returns it.
     *
     * Equivalence is by event id only — callers must trust the id
     * was content-derived (signed events satisfy this).
     */
    fun intern(event: Event): Event

    /** Returns the canonical [Event] for [id] if one is live, else null. */
    fun get(id: HexKey): Event?

    /** Number of map entries (including dead weak refs not yet cleaned). */
    fun size(): Int

    /** Drop every entry. Mostly useful for tests. */
    fun clear()

    companion object {
        /** Process-wide default interner used by [Event.fromJson]. */
        val Default: EventInterner
    }
}
