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
package com.vitorpamplona.amethyst.commons.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** A stored last-read marker carried into the future by a future-dated event comes back to now. */
class LastReadSkewTest {
    private val now = 1_800_000_000L

    @Test
    fun aMarkerInThePastIsKept() = assertEquals(now - 60, trustedLastRead(now - 60, now))

    @Test
    fun aMarkerMinutesAheadIsKept() = assertEquals(now + 3 * 60, trustedLastRead(now + 3 * 60, now))

    @Test
    fun aMarkerYearsAheadReadsAsNow() = assertEquals(now, trustedLastRead(now + 5 * 365 * 24 * 60 * 60L, now))
}
