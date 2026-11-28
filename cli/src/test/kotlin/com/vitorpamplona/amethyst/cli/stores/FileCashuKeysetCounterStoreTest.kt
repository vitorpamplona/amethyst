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
package com.vitorpamplona.amethyst.cli.stores

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The settled flag lives with the counters in `cashu.json`, so it has to survive a run the same
 * way they do: a data dir used on a second machine must not ask the mint about a keyset again.
 */
class FileCashuKeysetCounterStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "cashu.json")

    @Test
    fun aKeysetIsUnsettledUntilMarked() =
        runBlocking {
            val store = FileCashuKeysetCounterStore(file())

            assertFalse(store.isSettled("keyset1"))
            store.markSettled("keyset1")

            assertTrue(store.isSettled("keyset1"))
            assertFalse(store.isSettled("keyset2"))
        }

    @Test
    fun aSettledKeysetSurvivesTheNextRun() =
        runBlocking {
            val file = file()
            val firstRun = FileCashuKeysetCounterStore(file)
            firstRun.reserve("keyset1", 26)
            firstRun.markSettled("keyset1")

            val nextRun = FileCashuKeysetCounterStore(file)

            assertTrue(nextRun.isSettled("keyset1"))
            assertEquals(26L, nextRun.peek("keyset1"))
        }

    @Test
    fun markingKeepsTheCounters() =
        runBlocking {
            val store = FileCashuKeysetCounterStore(file())
            store.reserve("keyset1", 3)

            store.markSettled("keyset1")
            store.markSettled("keyset1")

            assertEquals(3L, store.peek("keyset1"))
            assertTrue(store.isSettled("keyset1"))
        }

    /** A `cashu.json` from before keysets were settled has no `settled_keysets`: it still loads, every keyset unsettled. */
    @Test
    fun aFileFromAnOlderAmyLoads() =
        runBlocking {
            val file = file()
            file.writeText("""{"keyset_counters":{"keyset1":26}}""")

            val store = FileCashuKeysetCounterStore(file)
            assertEquals(26L, store.peek("keyset1"))
            assertFalse(store.isSettled("keyset1"))

            store.markSettled("keyset1")

            assertTrue(store.isSettled("keyset1"))
            assertEquals(26L, store.peek("keyset1"))
        }
}
