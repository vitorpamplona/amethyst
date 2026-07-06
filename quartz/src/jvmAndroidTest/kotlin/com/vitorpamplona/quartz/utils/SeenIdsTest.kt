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
package com.vitorpamplona.quartz.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeenIdsTest {
    // Vary the FIRST 128 bits (the keyed part): value in the high 16 hex chars, zero tail.
    private fun id(i: Int) = i.toLong().toString(16).padStart(16, '0') + "0".repeat(48)

    @Test
    fun `first sight is new, repeats are skipped`() {
        val seen = SeenIds()
        assertTrue(seen.add(id(1)), "first time is new")
        assertFalse(seen.add(id(1)), "same id is a duplicate")
        assertFalse(seen.add(id(1)), "still a duplicate")
        assertTrue(seen.add(id(2)), "a different id is new")
        assertEquals(2, seen.size())
    }

    @Test
    fun `reset forgets everything`() {
        val seen = SeenIds()
        seen.add(id(7))
        assertFalse(seen.add(id(7)))
        seen.reset()
        assertTrue(seen.add(id(7)), "after reset the id is new again")
        assertEquals(1, seen.size())
    }

    @Test
    fun `holds many distinct ids across resizes, with exact dedup`() {
        // Start tiny so it must grow several times.
        val seen = SeenIds(initialSlotsPow2 = 4)
        val n = 50_000
        repeat(n) { assertTrue(seen.add(id(it)), "id $it should be new") }
        assertEquals(n, seen.size())
        // Every one is now a duplicate.
        repeat(n) { assertFalse(seen.add(id(it)), "id $it should be a duplicate") }
        assertEquals(n, seen.size(), "duplicates don't grow the set")
    }

    @Test
    fun `only the first 128 bits key the id (differ past 32 hex chars still dedups)`() {
        // Same first 128 bits, different tail -> treated as the same (documented tradeoff, ~1e-22 in practice).
        val seen = SeenIds()
        val prefix = "%032x".format(42)
        assertTrue(seen.add(prefix + "0".repeat(32)))
        assertFalse(seen.add(prefix + "f".repeat(32)), "same 128-bit prefix collapses")
    }

    @Test
    fun `the all-zero 128-bit-prefix key (the empty sentinel) is deduped correctly`() {
        val seen = SeenIds()
        assertTrue(seen.add("0".repeat(64)), "all-zero id is new the first time")
        assertFalse(seen.add("0".repeat(64)), "and a duplicate the second")
        assertTrue(seen.add(id(5)), "a normal id still works alongside it")
        assertEquals(2, seen.size())
    }

    @Test
    fun `a malformed id is let through, not skipped`() {
        val seen = SeenIds()
        assertTrue(seen.add("not-hex"), "malformed -> flows to verify")
        assertTrue(seen.add("short"))
    }
}
