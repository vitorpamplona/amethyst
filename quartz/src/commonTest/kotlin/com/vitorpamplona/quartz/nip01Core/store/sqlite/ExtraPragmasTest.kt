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
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [SQLiteEventStore.extraPragmas] must actually reach every pooled
 * connection (after the built-in defaults, so they can override), and
 * [SQLiteEventStore.optimize] must run cleanly on a live store.
 */
class ExtraPragmasTest {
    private suspend fun SQLiteEventStore.pragmaValue(name: String): Long =
        pool.useReader { db ->
            db.prepare("PRAGMA $name").use { stmt ->
                stmt.step()
                stmt.getLong(0)
            }
        }

    @Test
    fun extraPragmasApplyToConnections() =
        runTest {
            // temp_store: 0=default, 2=MEMORY.
            val store =
                SQLiteEventStore(
                    dbName = null,
                    extraPragmas = listOf("PRAGMA temp_store = MEMORY;"),
                )
            assertEquals(2L, store.pragmaValue("temp_store"))
            store.close()
        }

    @Test
    fun defaultsHaveNoExtraPragmas() =
        runTest {
            val store = SQLiteEventStore(dbName = null)
            assertEquals(0L, store.pragmaValue("temp_store"))
            store.close()
        }

    @Test
    fun optimizeRunsCleanly() =
        runTest {
            val store = SQLiteEventStore(dbName = null)
            store.optimize()
            store.close()
        }
}
