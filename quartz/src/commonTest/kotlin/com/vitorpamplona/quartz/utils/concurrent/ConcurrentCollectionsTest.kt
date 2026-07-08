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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcurrentCollectionsTest {
    @Test
    fun mapGetSet() {
        val m = ConcurrentMap<String, Int>()
        assertNull(m["a"])
        m["a"] = 1
        assertEquals(1, m["a"])
        m["a"] = 2
        assertEquals(2, m["a"])
        assertEquals(1, m.size())
    }

    @Test
    fun mapGetOrPutComputesOnce() {
        val m = ConcurrentMap<String, Int>()
        var calls = 0
        assertEquals(
            7,
            m.getOrPut("k") {
                calls++
                7
            },
        )
        // Present now: the default must NOT be recomputed.
        assertEquals(
            7,
            m.getOrPut("k") {
                calls++
                99
            },
        )
        assertEquals(1, calls)
        assertEquals(7, m["k"])
    }

    @Test
    fun mapMergeInsertsThenCombines() {
        val m = ConcurrentMap<String, Int>()
        // Absent -> inserts the value verbatim, remap not applied.
        assertEquals(1, m.merge("k", 1) { a, b -> a + b })
        // Present -> remap(existing, value).
        assertEquals(4, m.merge("k", 3) { a, b -> a + b })
        assertEquals(4, m["k"])
    }

    @Test
    fun mapSnapshotIsDetached() {
        val m = ConcurrentMap<String, Int>()
        m["a"] = 1
        m["b"] = 2
        val snap = m.snapshot()
        assertEquals(mapOf("a" to 1, "b" to 2), snap)
        // Mutating the map after the snapshot must not change the snapshot.
        m["c"] = 3
        assertEquals(2, snap.size)
        assertEquals(3, m.size())
    }

    @Test
    fun setAddContainsSize() {
        val s = ConcurrentSet<String>()
        assertFalse("x" in s)
        assertTrue(s.add("x"))
        // Re-adding is a no-op and reports it.
        assertFalse(s.add("x"))
        assertTrue("x" in s)
        assertTrue(s.add("y"))
        assertEquals(2, s.size())
    }

    @Test
    fun setSnapshotIsDetached() {
        val s = ConcurrentSet<String>()
        s.add("a")
        val snap = s.snapshot()
        s.add("b")
        assertEquals(setOf("a"), snap)
        assertEquals(2, s.size())
    }
}
