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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM/Android actual: a [ConcurrentHashMap] of weak references, swept through a [ReferenceQueue].
 *
 * The queue matters here more than for event ids: relay urls arrive from other people's lists, so
 * the app sees many urls exactly once. Sweeping a dead entry only when its own url is looked up
 * again would leave every one-off url's key in the map forever.
 */
actual class RelayUrlInterner {
    private val cache = ConcurrentHashMap<String, Ref>(INITIAL_CAPACITY)
    private val collected = ReferenceQueue<NormalizedRelayUrl>()

    /** Remembers its key, so the sweep can remove exactly this entry and not a newer one. */
    private class Ref(
        val url: String,
        relay: NormalizedRelayUrl,
        queue: ReferenceQueue<NormalizedRelayUrl>,
    ) : WeakReference<NormalizedRelayUrl>(relay, queue)

    internal actual fun intern(relay: NormalizedRelayUrl): NormalizedRelayUrl {
        sweep()

        cache[relay.url]?.get()?.let { return it }

        // putIfAbsent lets one writer win a race; a slot whose relay was collected is replaced.
        while (true) {
            val existing = cache.putIfAbsent(relay.url, Ref(relay.url, relay, collected)) ?: return relay
            existing.get()?.let { return it }
            cache.remove(relay.url, existing)
        }
    }

    actual fun get(url: String): NormalizedRelayUrl? = cache[url]?.get()

    actual fun size(): Int = cache.size

    internal actual fun clear() {
        cache.clear()
        while (collected.poll() != null) Unit
    }

    /** Removes the entries whose relay was collected. Two-argument remove spares a slot re-filled since. */
    private fun sweep() {
        while (true) {
            val ref = collected.poll() as? Ref ?: return
            cache.remove(ref.url, ref)
        }
    }

    actual companion object {
        actual val Default: RelayUrlInterner = RelayUrlInterner()

        private const val INITIAL_CAPACITY = 5_000
    }
}
