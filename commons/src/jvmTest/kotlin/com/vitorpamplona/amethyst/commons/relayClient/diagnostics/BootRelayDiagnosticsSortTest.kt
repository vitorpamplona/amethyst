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
package com.vitorpamplona.amethyst.commons.relayClient.diagnostics

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The boot census sorts relays by live counters that relay threads keep bumping. A key that
 * changes between comparisons crashed the app (TimSort "Comparison method violates its general
 * contract") from a diagnostics-only coroutine.
 */
class BootRelayDiagnosticsSortTest {
    // A counter that another thread moves while the sort runs: a new value on every read.
    private class Live(
        val id: Int,
    ) {
        private val random = Random(id)

        fun load(): Int = random.nextInt(1000)
    }

    private val relays = (0 until 2000).map { Live(it) }

    @Test
    fun aKeyReadPerComparisonBreaksTheSort() {
        assertFailsWith<IllegalArgumentException> {
            repeat(20) { relays.sortedByDescending { it.load() } }
        }
    }

    @Test
    fun frozenKeysSortWithoutThrowing() {
        repeat(20) {
            val sorted = relays.sortedByFrozenDesc { it.load() }
            assertEquals(relays.size, sorted.size)
        }
    }

    @Test
    fun frozenKeysSortHighestFirst() {
        val counts = listOf(3, 9, 1, 7)
        assertEquals(listOf(9, 7, 3, 1), counts.sortedByFrozenDesc { it })
    }
}
