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
package com.vitorpamplona.quartz.nip85TrustedAssertions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssertionMetricTest {
    @Test
    fun readsEveryNumericTagInOrder() {
        // A Brainstorm production 30382, as served by wss://search.brainstorm.world.
        val tags =
            arrayOf(
                arrayOf("d", "10d564596bfda214620eb5a48e1402387e24c04238605877dc83caae9ce4cf59"),
                arrayOf("rank", "13"),
                arrayOf("followers", "17"),
                arrayOf("reporters", "0"),
                arrayOf("muters", "1"),
                arrayOf("client", "Brainstorm"),
                arrayOf("hops", "2"),
            )

        val metrics = tags.assertionMetrics()

        assertEquals(listOf("rank", "followers", "reporters", "muters", "hops"), metrics.map { it.name })
        assertEquals(listOf("13", "17", "0", "1", "2"), metrics.map { it.value })
        assertTrue(metrics.all { it.isWhole() })
    }

    @Test
    fun keepsProviderExtensionsAndDecimals() {
        val tags =
            arrayOf(
                arrayOf("d", "subject"),
                arrayOf("personalizedGrapeRank_influence", "0.53"),
                arrayOf("verifiedFollowerCount", "1234"),
            )

        val metrics = tags.assertionMetrics()

        assertEquals(2, metrics.size)
        assertEquals("0.53", metrics[0].value)
        assertEquals(0.53, metrics[0].number)
        assertFalse(metrics[0].isWhole())
        assertTrue(metrics[1].isWhole())
    }

    @Test
    fun skipsPointersTextTimestampsAndRepeats() {
        val tags =
            arrayOf(
                arrayOf("d", "123"),
                arrayOf("p", "456"),
                arrayOf("t", "789"),
                arrayOf("k", "1"),
                arrayOf("petname", "42"),
                arrayOf("first_created_at", "1700000000"),
                arrayOf("active_hours_start", "8"),
                arrayOf("rank", "not a number"),
                arrayOf("rank", "7"),
                arrayOf("rank", "9"),
                arrayOf("followers", ""),
                arrayOf("zap_amount", "NaN"),
                // The JVM's number parser reads these, but they are not plain numbers.
                arrayOf("observation_period", "5d"),
                arrayOf("quality", "1e3"),
                arrayOf("score", "0x10"),
                // Timestamps are numbers, not metrics.
                arrayOf("first_seen", "1791724609"),
                arrayOf("verified_at", "1780529222"),
                arrayOf("comment_cnt"),
            )

        val metrics = tags.assertionMetrics()

        assertEquals(listOf("rank"), metrics.map { it.name })
        assertEquals("7", metrics.single().value)
    }

    @Test
    fun negativeWholeNumbersAreWhole() {
        assertTrue(AssertionMetric("x", "-3", -3.0).isWhole())
        assertFalse(AssertionMetric("x", "1e3", 1000.0).isWhole())
    }
}
