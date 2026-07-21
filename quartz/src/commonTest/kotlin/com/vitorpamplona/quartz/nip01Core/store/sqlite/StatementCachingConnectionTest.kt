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

import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class StatementCachingConnectionTest {
    private lateinit var conn: StatementCachingConnection

    private val sql = "SELECT ? AS v"

    @BeforeTest
    fun setup() {
        conn = StatementCachingConnection(BundledSQLiteDriver().open(":memory:"))
    }

    @AfterTest
    fun tearDown() {
        conn.close()
    }

    private fun readOne(stmt: SQLiteStatement): Long {
        stmt.step()
        return stmt.getLong(0)
    }

    @Test
    fun sequentialSameSqlReusesTheSameHandle() {
        var first: SQLiteStatement? = null
        conn.prepare(sql).use { stmt ->
            stmt.bindLong(1, 7)
            assertEquals(7, readOne(stmt))
            first = stmt
        }
        // Closed (returned to pool) — the next prepare of the same SQL must
        // hand back the very same cached handle, not a fresh prepare.
        conn.prepare(sql).use { stmt ->
            assertSame(first, stmt)
            stmt.bindLong(1, 9)
            assertEquals(9, readOne(stmt))
        }
    }

    @Test
    fun concurrentSameSqlHandlesAreDistinctAndIndependent() {
        // The k-way-merge shape: many identical-SQL cursors live at once.
        val a = conn.prepare(sql)
        val b = conn.prepare(sql)
        val c = conn.prepare(sql)
        assertNotSame(a, b)
        assertNotSame(b, c)
        assertNotSame(a, c)

        a.bindLong(1, 1)
        b.bindLong(1, 2)
        c.bindLong(1, 3)
        // Each cursor keeps its own bindings/position even while the others
        // are open — no aliasing of one native handle.
        assertEquals(1, readOne(a))
        assertEquals(2, readOne(b))
        assertEquals(3, readOne(c))
        a.close()
        b.close()
        c.close()

        // After release, a fresh concurrent burst reuses the pooled handles.
        val reused = conn.prepare(sql)
        assertSame(a, reused, "pool should hand back a freed handle before preparing anew")
        reused.close()
    }

    @Test
    fun overflowingTheGlobalCapFallsBackToUncached() {
        val small = StatementCachingConnection(BundledSQLiteDriver().open(":memory:"), maxCachedStatements = 2)
        try {
            val live = (0 until 5).map { small.prepare(sql) }
            // All five must be usable even though only two can be cached; the
            // extra three are plain uncached statements.
            live.forEachIndexed { i, stmt ->
                stmt.bindLong(1, i.toLong())
                assertEquals(i.toLong(), readOne(stmt))
            }
            live.forEach { it.close() }
        } finally {
            small.close()
        }
    }
}
