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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EventListAccumulatorTest {
    private fun event(n: Int) = Event(n.toString().padStart(64, '0'), "a".repeat(64), n.toLong(), 1, emptyArray(), "e$n", "b".repeat(128))

    @Test
    fun appendsKeepArrivalOrderAndPrependsGoInFrontNewestFirst() {
        val acc = EventListAccumulator()
        (1..3).forEach { acc.append(event(it)) }
        acc.prepend(event(4))
        acc.prepend(event(5))

        assertEquals(listOf(5, 4, 1, 2, 3), acc.snapshot().map { it.createdAt.toInt() })
    }

    // The flows emit a snapshot per event; a collector holding an older one must keep seeing
    // exactly what it was sent, also across the arrays growing.
    @Test
    fun aSnapshotNeverChangesAfterwards() {
        val acc = EventListAccumulator()
        acc.append(event(0))
        val early = acc.snapshot()
        (1..100).forEach { acc.append(event(it)) }
        (101..140).forEach { acc.prepend(event(it)) }

        assertEquals(listOf(0), early.map { it.createdAt.toInt() })
        val late = acc.snapshot()
        assertEquals(141, late.size)
        assertEquals(140, late.first().createdAt.toInt())
        assertEquals(100, late.last().createdAt.toInt())
        assertFailsWith<IndexOutOfBoundsException> { early[1] }
    }

    @Test
    fun snapshotsCompareAsLists() {
        val acc = EventListAccumulator()
        val a = event(1)
        acc.append(a)
        assertEquals(listOf(a), acc.snapshot())
    }
}
