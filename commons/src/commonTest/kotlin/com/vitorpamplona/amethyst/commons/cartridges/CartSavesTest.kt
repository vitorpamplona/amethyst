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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** When [CartSaves] writes, on virtual time: a save goes out at once, a stream of them once a second. */
@OptIn(ExperimentalCoroutinesApi::class)
class CartSavesTest {
    private class RecordingStorage : CartSaveStorage {
        val writes = ArrayList<Map<String, Int>>()

        override suspend fun loadAll(): Map<String, IntArray> = emptyMap()

        override suspend fun save(saves: Map<String, IntArray>) {
            writes.add(saves.mapValues { it.value[0] })
        }
    }

    private fun values(first: Int) = IntArray(CartSaveStore.VALUES).also { it[0] = first }

    @Test
    fun aSaveIsWrittenAtOnce() =
        runTest {
            val storage = RecordingStorage()
            val saves = CartSaves(storage, CoroutineScope(StandardTestDispatcher(testScheduler)))
            saves.save("game", values(1))
            runCurrent()
            assertEquals(listOf(mapOf("game" to 1)), storage.writes)
        }

    @Test
    fun savesEveryFrameAreWrittenOnceASecond() =
        runTest {
            val storage = RecordingStorage()
            val saves = CartSaves(storage, CoroutineScope(StandardTestDispatcher(testScheduler)))
            saves.save("game", values(1))
            runCurrent()
            // Thirty frames inside the next second: held back, then written as the last one.
            for (frame in 2..31) saves.save("game", values(frame))
            advanceTimeBy(500.milliseconds)
            runCurrent()
            assertEquals(1, storage.writes.size)
            advanceTimeBy(1.seconds)
            runCurrent()
            assertEquals(listOf(mapOf("game" to 1), mapOf("game" to 31)), storage.writes)
        }

    @Test
    fun flushWritesWhatIsHeldBack() =
        runTest {
            val storage = RecordingStorage()
            val saves = CartSaves(storage, CoroutineScope(StandardTestDispatcher(testScheduler)))
            saves.save("game", values(1))
            runCurrent()
            saves.save("game", values(2))
            saves.flush()
            runCurrent()
            assertEquals(listOf(mapOf("game" to 1), mapOf("game" to 2)), storage.writes)
        }
}
