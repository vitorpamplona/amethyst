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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The saves of one running game, between the game and [store]. A save is written as soon as the
 * game makes it, so a game that saves at a checkpoint keeps it even if the app is killed right
 * after. A game that saves every frame is written at most once per [minInterval]; [flush] writes
 * what is left when the player leaves.
 *
 * [load] and [save] are called on the game's thread; the writes run on [scope].
 */
class CartSaves(
    private val store: CartSaveStorage?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val minInterval: Duration = 1.seconds,
) {
    private var saved: Map<String, IntArray> = emptyMap()
    private val unsaved = MutableStateFlow<Map<String, IntArray>>(emptyMap())
    private val writing = Mutex()
    private var lastWrite: TimeMark? = null
    private var pending: Job? = null
    private var newIds = 0

    /** Reads every save once, before the game starts. */
    suspend fun load() {
        saved = store?.loadAll() ?: emptyMap()
    }

    fun get(id: String): IntArray? = saved[id]

    fun save(
        id: String,
        values: IntArray,
    ) {
        // A game uses one save id, a few for a series; a cart that cycles `cartdata` + `run()`
        // through endless ids would otherwise grow the app's store without bound.
        if (id !in saved && newIds++ >= MAX_NEW_IDS) return
        saved = saved + (id to values)
        unsaved.update { it + (id to values) }
        if (pending?.isActive == true) return
        val wait = lastWrite?.let { minInterval - it.elapsedNow() } ?: Duration.ZERO
        lastWrite = TimeSource.Monotonic.markNow() + wait.coerceAtLeast(Duration.ZERO)
        pending =
            scope.launch {
                if (wait.isPositive()) delay(wait)
                write()
            }
    }

    /** Writes what has not been written yet. Safe from any thread. */
    fun flush(): Job = scope.launch { write() }

    private suspend fun write() {
        writing.withLock {
            val batch = unsaved.getAndUpdate { emptyMap() }
            if (batch.isNotEmpty()) store?.save(batch)
        }
    }

    private companion object {
        const val MAX_NEW_IDS = 8
    }
}
