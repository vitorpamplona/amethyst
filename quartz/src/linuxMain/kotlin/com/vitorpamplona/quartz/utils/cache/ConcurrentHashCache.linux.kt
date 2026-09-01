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
package com.vitorpamplona.quartz.utils.cache

import com.vitorpamplona.quartz.utils.concurrent.PlatformLock
import com.vitorpamplona.quartz.utils.concurrent.withLock

/**
 * Linux/Native actual for [ConcurrentHashCache].
 *
 * Was copy-on-write, mirroring the old `LargeCache.linux`: every [put] rebuilt the
 * whole map under a CAS retry loop, so writes were O(n) and a decode burst against a
 * warm cache was O(n^2). That is a bad shape for this class in particular — its only
 * caller, `CachingEventDecoder`, writes once per event arriving from a relay.
 *
 * Now a plain [HashMap] guarded by a [PlatformLock]: O(1) writes, and no CAS retry
 * to livelock under a write burst. Same lock choice and the same residual (a spin
 * lock on this target) as `LargeCache.linux.kt` — see its docs.
 */
actual class ConcurrentHashCache<K : Any, V : Any> {
    private val lock = PlatformLock()
    private val map = HashMap<K, V>()

    actual fun get(key: K): V? = lock.withLock { map[key] }

    actual fun put(
        key: K,
        value: V,
    ) {
        lock.withLock { map[key] = value }
    }

    actual fun size(): Int = lock.withLock { map.size }

    actual fun clear() {
        lock.withLock { map.clear() }
    }
}
