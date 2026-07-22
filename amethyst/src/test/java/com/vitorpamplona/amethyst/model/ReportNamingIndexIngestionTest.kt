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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `LocalCache` is a process-wide object (`LocalCache.kt:353`) and JUnit 4's default method order is
 * hash-based, not source order. So each test method uses its **own** reporter, target and event ids —
 * sharing them across methods would let one test's consumed report inflate another's count depending
 * on which ran first.
 */
class ReportNamingIndexIngestionTest {
    private fun reportNamingBothNoteAndAuthor(
        id: String,
        reporter: String,
        target: String,
        reportedNoteId: String,
    ) = ReportEvent(
        id = id,
        pubKey = reporter,
        createdAt = 1_700_000_000L,
        tags =
            arrayOf(
                arrayOf("e", reportedNoteId, ReportType.IMPERSONATION.code),
                arrayOf("p", target, ReportType.IMPERSONATION.code),
            ),
        content = "",
        sig = "sig",
    )

    @Test
    fun aReportFiledFromANoteStillReachesTheUserNamingIndex() {
        val reporter = "c1".repeat(32)
        val target = "c2".repeat(32)

        LocalCache.consume(
            reportNamingBothNoteAndAuthor("c3".repeat(32), reporter, target, "c4".repeat(32)),
            null,
            true,
        )

        val reported = LocalCache.getUserIfExists(target)
        assertTrue("the reported author must exist in the cache", reported != null)

        assertEquals(1, reported!!.reports().reportsNaming(setOf(reporter)).size)
    }

    @Test
    fun theHideThresholdCountIsUnaffectedByNoteFiledReports() {
        val reporter = "d1".repeat(32)
        val target = "d2".repeat(32)

        LocalCache.consume(
            reportNamingBothNoteAndAuthor("d3".repeat(32), reporter, target, "d4".repeat(32)),
            null,
            true,
        )

        val reported = LocalCache.getUserIfExists(target)!!

        assertEquals(1, reported.reports().reportsNaming(setOf(reporter)).size)
        assertEquals(0, reported.reports().countReportAuthorsBy(setOf(reporter)))
    }
}
