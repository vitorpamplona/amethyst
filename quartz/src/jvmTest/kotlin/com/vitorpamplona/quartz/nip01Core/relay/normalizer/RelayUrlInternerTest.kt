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
package com.vitorpamplona.quartz.nip01Core.relay.normalizer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RelayUrlInternerTest {
    @Test
    fun theFirstInstanceWinsAndEqualOnesResolveToIt() {
        val interner = RelayUrlInterner()
        val first = NormalizedRelayUrl("wss://intern-first.example.com/")
        val second = NormalizedRelayUrl("wss://intern-first.example.com/")

        assertSame(first, interner.intern(first))
        assertSame(first, interner.intern(second))
        assertSame(first, interner.get("wss://intern-first.example.com/"))
    }

    @Test
    fun getIsNullForAnUnknownUrl() {
        assertNull(RelayUrlInterner().get("wss://intern-unknown.example.com/"))
    }

    @Test
    fun aRelayNobodyHoldsIsCollectedAndItsEntrySwept() {
        val interner = RelayUrlInterner()
        internAndDiscard(interner, 1_000)

        for (i in 0..40) {
            System.gc()
            Thread.sleep(20)
            // Any intern sweeps the queue of collected relays.
            interner.intern(NormalizedRelayUrl("wss://intern-sweeper.example.com/"))
            if (interner.size() <= 1) break
        }

        assertTrue(interner.size() <= 5, "expected the discarded relays to be swept, got ${interner.size()}")
        assertNull(interner.get("wss://intern-discarded-0.example.com/"))
    }

    @Test
    fun aHeldRelayIsNeverCollected() {
        val interner = RelayUrlInterner()
        val held = interner.intern(NormalizedRelayUrl("wss://intern-held.example.com/"))

        repeat(5) {
            System.gc()
            Thread.sleep(20)
        }

        assertSame(held, interner.get("wss://intern-held.example.com/"))
        assertEquals(1, interner.size())
    }

    @Test
    fun aSweepNeverRemovesTheLiveReplacementOfACollectedEntry() {
        val interner = RelayUrlInterner()
        internAndDiscard(interner, 1)

        var replacement: NormalizedRelayUrl? = null
        for (i in 0..40) {
            System.gc()
            Thread.sleep(20)
            if (interner.get("wss://intern-discarded-0.example.com/") == null) {
                replacement = interner.intern(NormalizedRelayUrl("wss://intern-discarded-0.example.com/"))
                break
            }
        }

        // A later sweep must not drop the new entry even though the old one shared its url.
        interner.intern(NormalizedRelayUrl("wss://intern-sweeper.example.com/"))
        if (replacement != null) {
            assertSame(replacement, interner.get("wss://intern-discarded-0.example.com/"))
        }
    }

    /** Interns [count] relays from a helper frame, so the interner's weak references are all that hold them. */
    private fun internAndDiscard(
        interner: RelayUrlInterner,
        count: Int,
    ) {
        for (i in 0 until count) interner.intern(NormalizedRelayUrl("wss://intern-discarded-$i.example.com/"))
    }
}
