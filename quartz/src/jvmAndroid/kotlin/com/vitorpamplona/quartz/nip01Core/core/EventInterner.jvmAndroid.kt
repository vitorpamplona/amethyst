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
package com.vitorpamplona.quartz.nip01Core.core

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM/Android actual: ConcurrentHashMap of WeakReference, pre-sized
 * for the expected hot working set. Dead entries are reclaimed
 * lazily on access (see [intern] and [get]) — when a weak ref's
 * referent has been GC'd, the map slot is removed atomically and
 * replaced if a fresh event takes its place.
 */
actual class EventInterner {
    private val cache = ConcurrentHashMap<HexKey, WeakReference<Event>>(INITIAL_CAPACITY, LOAD_FACTOR)

    actual fun intern(event: Event): Event {
        // Fast path: existing canonical is still alive.
        cache[event.id]?.get()?.let { return it }

        // Race-safe install: putIfAbsent ensures only one writer
        // wins. If another thread beat us, return whatever they put
        // (resolving the rare case where their ref was already GC'd
        // by retrying through the slow path).
        while (true) {
            val ref = WeakReference(event)
            val existing = cache.putIfAbsent(event.id, ref)
            if (existing == null) return event
            val canonical = existing.get()
            if (canonical != null) return canonical
            // Existing entry's referent was GC'd; drop it and retry.
            cache.remove(event.id, existing)
        }
    }

    actual fun get(id: HexKey): Event? {
        val ref = cache[id] ?: return null
        val event = ref.get()
        if (event != null) return event
        // Self-cleaning: drop the dead entry so the map doesn't bloat.
        cache.remove(id, ref)
        return null
    }

    actual fun size(): Int = cache.size

    actual fun clear() {
        cache.clear()
    }

    actual companion object {
        actual val Default: EventInterner = EventInterner()

        private const val INITIAL_CAPACITY = 5_000
        private const val LOAD_FACTOR = 0.75f
    }
}
