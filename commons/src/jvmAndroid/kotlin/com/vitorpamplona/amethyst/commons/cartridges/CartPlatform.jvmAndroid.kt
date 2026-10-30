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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.kiko.runtime.FantasyConsole
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@Volatile
private var lastCollection = 0L

@Volatile
private var shortAfterCollection = false

actual fun isCartMemoryLow(): Boolean {
    val runtime = Runtime.getRuntime()

    fun used() = runtime.totalMemory() - runtime.freeMemory()
    if (used() < runtime.maxMemory() / 10 * 9) return false
    // Much of that may be garbage: only a heap still full after a collection is short. At most
    // one collection every few seconds, so an app that simply runs near its limit is not slowed.
    val now = System.nanoTime()
    if (now - lastCollection > COLLECTION_INTERVAL_NS) {
        lastCollection = now
        System.gc()
        shortAfterCollection = used() > runtime.maxMemory() / 10 * 8
    }
    return shortAfterCollection
}

private const val COLLECTION_INTERVAL_NS = 5_000_000_000L

actual fun newCartDispatcher(): CloseableCoroutineDispatcher =
    Executors
        .newSingleThreadExecutor { runnable ->
            Thread(null, runnable, "cart", FantasyConsole.RECOMMENDED_STACK_BYTES).apply { isDaemon = true }
        }.asCoroutineDispatcher()
