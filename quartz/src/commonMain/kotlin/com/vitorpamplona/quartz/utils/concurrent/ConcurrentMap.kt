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
package com.vitorpamplona.quartz.utils.concurrent

/**
 * A thread-safe hash map whose compound operations — [getOrPut] and [merge] —
 * apply their update **atomically**, not merely one-lock-per-primitive-op. This
 * is the contract a concurrent producer/consumer pipeline needs: two coroutines
 * racing `getOrPut` on the same key must agree on a single value, and racing
 * `merge` must not lose an increment.
 *
 * commonMain has no `java.util.concurrent.ConcurrentHashMap`, so this is
 * expect/actual, matching the split already used by [com.vitorpamplona.quartz.utils.cache.ConcurrentHashCache]:
 *  - JVM / Android → `ConcurrentHashMap` (lock-free, true atomic `computeIfAbsent` / `merge`).
 *  - Native (Apple + Linux) → copy-on-write over an atomic reference, with a
 *    CAS retry loop giving the same atomicity. Correct but O(n)-per-write; the
 *    native targets never run the heavy crawl this backs, they only compile it.
 *
 * Only the operations the crawl actually uses are exposed — no full [MutableMap]
 * surface — so the native copy-on-write actual stays small and obviously correct.
 */
expect class ConcurrentMap<K : Any, V : Any>() {
    operator fun get(key: K): V?

    operator fun set(
        key: K,
        value: V,
    )

    /** Atomically return the value for [key], computing and inserting [defaultValue] once if absent. */
    fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V

    /**
     * Atomically insert [value] if [key] is absent, else replace the existing
     * value with `remap(existing, value)`. Returns the value now stored.
     */
    fun merge(
        key: K,
        value: V,
        remap: (old: V, new: V) -> V,
    ): V

    fun size(): Int

    /** A point-in-time copy of the entries — safe to iterate without holding a lock. */
    fun snapshot(): Map<K, V>
}
