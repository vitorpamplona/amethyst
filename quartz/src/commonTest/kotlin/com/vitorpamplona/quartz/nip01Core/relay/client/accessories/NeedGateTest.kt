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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The one point on the download path where an id can still be declined for
 * free. Everything asserted here is a property the inline version of this code
 * could break silently: a sync with no predicate must not start allocating, a
 * fully-declined batch must not become an empty REQ, and the skip count must
 * stay separate from the reconcile's own diff so an operator can tell a
 * predicate that does nothing from one that eats everything.
 */
class NeedGateTest {
    private fun id(n: Int): HexKey = n.toString().padStart(64, '0')

    private fun batch(range: IntRange) = range.map(::id)

    private class Skips {
        var total = 0
        var calls = 0

        val sink: (Int) -> Unit = {
            total += it
            calls++
        }
    }

    @Test
    fun `with no predicate the batch passes through untouched and uncopied`() {
        // The unfiltered sync is the common case and must not pay a copy per
        // batch for a feature it is not using.
        val skips = Skips()
        val input = batch(1..500)
        val kept = NeedGate(null, skips.sink).keep(input)

        assertSame(input, kept, "an unfiltered batch must be the same list instance, not a copy")
        assertEquals(0, skips.total)
        assertEquals(0, skips.calls, "nothing was dropped so the counter should not even be touched")
    }

    @Test
    fun `a predicate keeps its subset in order`() {
        val skips = Skips()
        val wanted = setOf(id(2), id(4), id(6))
        val kept = NeedGate({ it in wanted }, skips.sink).keep(batch(1..6))

        assertEquals(listOf(id(2), id(4), id(6)), kept)
        assertEquals(3, skips.total)
    }

    @Test
    fun `a fully declined batch yields an empty list and never null`() {
        // Non-null on purpose. A nullable return invites
        // `gate?.keep(ids) ?: ids`, which reads as "no gate, keep everything"
        // and silently means "everything declined, so send everything" — a
        // fully-declining gate turning into a full download.
        val skips = Skips()
        val kept = NeedGate({ false }, skips.sink).keep(batch(1..10))

        assertTrue(kept.isEmpty(), "nothing survived, so there is nothing to send")
        assertEquals(10, skips.total)
    }

    @Test
    fun `an empty input yields empty and counts nothing`() {
        val skips = Skips()
        assertTrue(NeedGate({ true }, skips.sink).keep(emptyList()).isEmpty())
        assertEquals(0, skips.total)
    }

    @Test
    fun `a predicate that accepts everything drops nothing`() {
        val skips = Skips()
        val kept = NeedGate({ true }, skips.sink).keep(batch(1..20))

        assertEquals(20, kept.size)
        assertEquals(0, skips.total)
        assertEquals(0, skips.calls)
    }

    @Test
    fun `skips accumulate across batches`() {
        // One gate spans a whole sync, so the count has to survive more than
        // the batch it was produced in.
        val skips = Skips()
        val gate = NeedGate({ it.endsWith("1") }, skips.sink)
        gate.keep(batch(1..10))
        gate.keep(batch(11..20))

        assertEquals(18, skips.total, "only ids 1 and 11 of the twenty end in 1")
    }

    @Test
    fun `the predicate sees every id in the batch exactly once`() {
        val seen = mutableListOf<HexKey>()
        NeedGate({
            seen.add(it)
            true
        }, Skips().sink).keep(batch(1..5))

        assertEquals(batch(1..5), seen)
    }

    @Test
    fun `a sync result reports no skips unless a predicate declined something`() {
        // Back-compat: every existing caller constructs this without the new
        // field and must keep reading zero.
        val result = NegentropySyncResult(needCount = 7, haveCount = 0, downloaded = 7, windows = 1)

        assertEquals(0, result.skipped)
        assertTrue(result.needCount == 7, "the reconcile diff is unaffected by the gate")
    }
}
