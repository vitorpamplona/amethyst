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
 * Canonicalises [Event] instances by id so every consumer sees the
 * same object reference for the same event id.
 *
 * Backed by weak references — entries vanish when no live consumer
 * holds the event. First-seen wins; equivalence is by event id, so
 * callers must trust ids are content-derived (signed events satisfy
 * this). Sized for ~5000 hot entries; resizes if usage exceeds that.
 *
 * Platforms without weak references fall back to a passthrough that
 * returns [event] unchanged — no canonicalisation, no leaks.
 */
expect class EventInterner() {
    /**
     * Returns the canonical [Event] for [event]'s id, storing
     * [event] as canonical if no live entry exists.
     */
    fun intern(event: Event): Event

    /** Returns the canonical [Event] for [id] if one is live, else null. */
    fun get(id: HexKey): Event?

    /** Number of map entries (including dead weak refs not yet cleaned). */
    fun size(): Int

    /** Drop every entry. Mostly useful for tests. */
    fun clear()

    companion object {
        /** Process-wide shared instance. */
        val Default: EventInterner
    }
}
