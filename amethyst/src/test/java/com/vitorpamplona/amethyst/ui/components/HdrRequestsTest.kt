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
package com.vitorpamplona.amethyst.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HdrRequestsTest {
    @Test
    fun noRequestsMeansSdr() {
        val requests = HdrRequests()
        assertFalse(requests.wantsHdr)
    }

    @Test
    fun hdrStaysOnUntilTheLastRequestLeaves() {
        val requests = HdrRequests()
        val first = requests.add(2f)
        val second = requests.add(2f)

        requests.remove(first)
        assertTrue(requests.wantsHdr)

        requests.remove(second)
        assertFalse(requests.wantsHdr)
    }

    @Test
    fun theLargestCappedHeadroomWins() {
        val requests = HdrRequests()
        requests.add(2f)
        requests.add(3f)
        assertEquals(3f, requests.headroom)
    }

    @Test
    fun anUncappedRequestLiftsTheCap() {
        val requests = HdrRequests()
        requests.add(2f)
        val fullscreen = requests.add(HdrRequests.UNCAPPED)
        assertEquals(HdrRequests.UNCAPPED, requests.headroom)

        requests.remove(fullscreen)
        assertEquals(2f, requests.headroom)
    }

    @Test
    fun equalHeadroomsAreStillSeparateRequests() {
        val requests = HdrRequests()
        val first = requests.add(2f)
        requests.add(2f)

        requests.remove(first)
        requests.remove(first)
        assertTrue("removing one token twice must not release the other", requests.wantsHdr)
    }
}
