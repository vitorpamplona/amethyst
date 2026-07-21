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
package com.vitorpamplona.quartz.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PodcastValueShareTest {
    private fun node(
        name: String,
        split: Int,
        fee: Boolean? = null,
    ) = PodcastValueRecipient(name = name, type = PodcastValue.TYPE_NODE, address = "node-$name", split = split, fee = fee)

    @Test
    fun `a hostile fee split can never pay out more than the total`() {
        // The value block comes verbatim from a kind-30054 episode event, which anyone can publish,
        // and `split` is an unvalidated Int. A fee recipient is paid `total * split / 100`, so a
        // split of 1000 would bill the user 10x what they chose — every minute, for a streaming
        // payment whose on-screen running total shows only the intended amount.
        val value =
            PodcastValue(
                recipients = listOf(node("attacker", 1000, fee = true), node("host", 100)),
            )

        val shares = value.computeShares(1_000_000L)
        val paid = shares.sumOf { it.amountMilliSats }

        assertTrue(paid <= 1_000_000L, "paid $paid millisats for a 1,000,000 millisat zap")
    }

    @Test
    fun `fee splits summing over 100 percent cannot exceed the total either`() {
        val value =
            PodcastValue(
                recipients =
                    listOf(
                        node("feeA", 60, fee = true),
                        node("feeB", 60, fee = true),
                        node("host", 100),
                    ),
            )

        val paid = value.computeShares(1_000_000L).sumOf { it.amountMilliSats }
        assertTrue(paid <= 1_000_000L, "paid $paid millisats for a 1,000,000 millisat zap")
    }

    @Test
    fun `weighted split with no fees divides by relative weight`() {
        val value =
            PodcastValue(
                recipients = listOf(node("host", 90), node("producer", 10)),
            )
        // 100k sats total in millisats.
        val shares = value.computeShares(100_000_000L).associate { it.recipient.name to it.amountMilliSats }

        assertEquals(90_000_000L, shares["host"])
        assertEquals(10_000_000L, shares["producer"])
    }

    @Test
    fun `fee recipient takes its split as a percent off the top then remainder split by weight`() {
        val value =
            PodcastValue(
                recipients =
                    listOf(
                        node("app", 5, fee = true), // 5% fee off the top
                        node("host", 80),
                        node("cohost", 20),
                    ),
            )
        val shares = value.computeShares(1_000_000L).associate { it.recipient.name to it.amountMilliSats }

        // 5% of 1,000,000 = 50,000 fee. Remainder 950,000 split 80/20.
        assertEquals(50_000L, shares["app"])
        assertEquals(760_000L, shares["host"])
        assertEquals(190_000L, shares["cohost"])
        // No more than the total is ever allocated.
        assertTrue(shares.values.sum() <= 1_000_000L)
    }

    @Test
    fun `recipients without an address or with non-positive split are ignored`() {
        val value =
            PodcastValue(
                recipients =
                    listOf(
                        node("host", 100),
                        PodcastValueRecipient(name = "noaddr", type = PodcastValue.TYPE_NODE, address = null, split = 50),
                        node("zero", 0),
                    ),
            )
        val shares = value.computeShares(10_000L)

        assertEquals(1, shares.size)
        assertEquals("host", shares.single().recipient.name)
        assertEquals(10_000L, shares.single().amountMilliSats)
    }

    @Test
    fun `non-positive total or empty recipients yields no shares`() {
        val value = PodcastValue(recipients = listOf(node("host", 100)))
        assertTrue(value.computeShares(0L).isEmpty())
        assertTrue(value.computeShares(-5L).isEmpty())
        assertTrue(PodcastValue(recipients = emptyList()).computeShares(1_000L).isEmpty())
    }
}
