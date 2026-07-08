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
 * A thread-safe hash set for the crawl's cross-coroutine membership tracking
 * (dead relays struck by drain workers while the router reads them, relay hints
 * written by the ingest consumer while the producer reads them).
 *
 * commonMain has no `java.util.concurrent.ConcurrentHashMap.newKeySet()`, so this
 * is expect/actual with the same JVM-vs-native split as [ConcurrentMap]:
 *  - JVM / Android → `ConcurrentHashMap.newKeySet()`.
 *  - Native → copy-on-write over an atomic reference (compile-only, never the hot path).
 */
expect class ConcurrentSet<E : Any>() {
    /** Add [element]; returns true if it was not already present. */
    fun add(element: E): Boolean

    operator fun contains(element: E): Boolean

    fun size(): Int

    /** A point-in-time copy — safe to iterate or diff against without a lock. */
    fun snapshot(): Set<E>
}
