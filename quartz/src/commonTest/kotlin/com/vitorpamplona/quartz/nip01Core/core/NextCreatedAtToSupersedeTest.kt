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
package com.vitorpamplona.quartz.nip01Core.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NextCreatedAtToSupersedeTest {
    @Test
    fun theWallClockWinsOnceItHasMovedPastTheNewestVersion() {
        // The common case — versions minutes apart carry the real time, not a drifting counter.
        assertEquals(1_000L, nextCreatedAtToSupersede(newestKnown = 900L, now = 1_000L))
    }

    @Test
    fun aBurstInsideOneSecondStepsOneSecondPerVersion() {
        assertEquals(1_001L, nextCreatedAtToSupersede(newestKnown = 1_000L, now = 1_000L))
        assertEquals(1_002L, nextCreatedAtToSupersede(newestKnown = 1_001L, now = 1_000L))
    }

    @Test
    fun aVersionStampedInTheFutureIsStillSuperseded() {
        // Clock skew on another device (or this client's own burst) can leave the newest known
        // version ahead of this device's clock. Falling back to `now` there would publish a version
        // every store drops.
        assertEquals(9_001L, nextCreatedAtToSupersede(newestKnown = 9_000L, now = 1_000L))
    }
}
