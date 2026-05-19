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
package com.vitorpamplona.amethyst.service.okhttp

import com.vitorpamplona.amethyst.commons.privacy.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedRouteExceptionTest {
    @Test
    fun onionMessage_mentionsTorAndOnion() {
        val e = BlockedRouteException(BlockReason.ONION_REQUIRES_TOR)
        assertSame(BlockReason.ONION_REQUIRES_TOR, e.reason)
        assertTrue("message should mention Tor: ${e.message}", e.message!!.contains("Tor", ignoreCase = true))
        assertTrue("message should mention .onion: ${e.message}", e.message!!.contains(".onion"))
    }

    @Test
    fun i2pMessage_mentionsI2pAndDotI2p() {
        val e = BlockedRouteException(BlockReason.I2P_REQUIRES_I2P)
        assertSame(BlockReason.I2P_REQUIRES_I2P, e.reason)
        assertTrue("message should mention I2P: ${e.message}", e.message!!.contains("I2P"))
        assertTrue("message should mention .i2p: ${e.message}", e.message!!.contains(".i2p"))
    }

    @Test
    fun dualHttpClientManagerCompanion_returnsBlockedRouteException_withReason() {
        // Pins the factory return type so callers can pattern-match without losing the reason.
        val e: BlockedRouteException = DualHttpClientManager.blockedException(BlockReason.ONION_REQUIRES_TOR)
        assertEquals(BlockReason.ONION_REQUIRES_TOR, e.reason)
    }
}
