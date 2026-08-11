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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The list-backed index is what the `localEntries` overloads become, so its
 * slicing has to keep NIP-01's inclusive `since`/`until` exactly: a window that
 * dropped its boundary second would leave events neither side ever compares,
 * and the two sides of a reconcile would disagree about what the window holds.
 */
class NegentropyLocalIndexTest {
    private fun idAt(second: Long) = IdAndTime(second, second.toString().padStart(64, '0'))

    private val index = NegentropyLocalIndex.of((1000L..1009L).map { idAt(it) })

    private fun window(
        since: Long?,
        until: Long?,
    ) = Filter(kinds = listOf(1), since = since, until = until)

    @Test
    fun bothBoundsAreInclusive() =
        runTest {
            assertEquals(3, index.count(window(1002, 1004)))
            assertEquals(listOf(1002L, 1003L, 1004L), index.entriesFor(window(1002, 1004)).map { it.createdAt })
        }

    @Test
    fun anUnboundedSideReachesTheEnd() =
        runTest {
            assertEquals(5, index.count(window(1005, null)))
            assertEquals(6, index.count(window(null, 1005)))
            assertEquals(10, index.count(window(null, null)))
        }

    @Test
    fun aWindowOutsideEverythingIsEmpty() =
        runTest {
            assertEquals(0, index.count(window(2000, 3000)))
            assertTrue(index.entriesFor(window(2000, 3000)).isEmpty())
        }

    @Test
    fun aSingleSecondWindowHoldsThatSecond() =
        runTest {
            assertEquals(1, index.count(window(1007, 1007)))
            assertEquals(listOf(1007L), index.entriesFor(window(1007, 1007)).map { it.createdAt })
        }

    @Test
    fun entriesNeedNotArriveSorted() =
        runTest {
            val shuffled = NegentropyLocalIndex.of(listOf(idAt(1005), idAt(1001), idAt(1009), idAt(1003)))
            assertEquals(2, shuffled.count(window(1001, 1003)))
            assertEquals(listOf(1001L, 1003L), shuffled.entriesFor(window(1001, 1003)).map { it.createdAt })
        }

    @Test
    fun theEmptyIndexAnswersZeroForEveryWindow() =
        runTest {
            assertEquals(0, NegentropyLocalIndex.Empty.count(window(1000, 2000)))
            assertTrue(NegentropyLocalIndex.Empty.entriesFor(window(1000, 2000)).isEmpty())
            assertEquals(0, NegentropyLocalIndex.of(emptyList()).count(window(null, null)))
        }
}
