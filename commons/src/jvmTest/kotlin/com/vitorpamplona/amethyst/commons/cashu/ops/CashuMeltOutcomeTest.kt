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
package com.vitorpamplona.amethyst.commons.cashu.ops

import com.vitorpamplona.amethyst.commons.notices.CashuMeltNotice
import com.vitorpamplona.amethyst.commons.notices.CashuMeltOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The rule every payment screen relies on: an invoice the mint settled is never "not paid". */
class CashuMeltOutcomeTest {
    @Test
    fun `a melt that paid the invoice is reported as paid, recorded or not`() {
        val failure = CashuMeltNotice.PaidUnrecorded(paidSats = 21)

        assertEquals(CashuMeltOutcome.PaidUnrecorded(failure), MeltRecordAbortedException(failure, cause = null).toCashuMeltOutcome())
    }

    @Test
    fun `every other melt failure is not paid, with its notice`() {
        val refused = CashuMeltNotice.Refused("route failed")

        assertEquals(CashuMeltOutcome.NotPaid(refused), MeltNotPaidException(refused).toCashuMeltOutcome())
        assertEquals(CashuMeltOutcome.NotPaid(CashuMeltNotice.Failed("boom")), IllegalStateException("boom").toCashuMeltOutcome())
    }
}
