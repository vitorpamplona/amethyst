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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How [RateLimitRetries] releases what relays refused with a back-off, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitRetriesTest {
    private val relay = NormalizedRelayUrl("wss://busy.example/")

    private class Released(
        val key: String,
        val atMs: Long,
    ) {
        override fun toString() = "$key@$atMs"
    }

    private fun TestScope.retries(
        released: MutableList<Released>,
        scope: CoroutineScope = this,
        send: (String) -> Boolean = { true },
    ) = RateLimitRetries(scope, clock = { testScheduler.currentTime }) { _, key ->
        released.add(Released(key, testScheduler.currentTime))
        send(key)
    }

    @Test
    fun nothingGoesOutBeforeTheLatestBackOffThenOnePerSpacedSlot() =
        runTest {
            val released = mutableListOf<Released>()
            val retries = retries(released)
            retries.schedule(relay, "a", 1_000)
            retries.schedule(relay, "b", 800)
            retries.schedule(relay, "c", 1_000)
            advanceUntilIdle()

            // Spaced at the longest back-off plus a tenth, plus a millisecond: 1101 ms.
            assertEquals("[a@1000, b@2101, c@3202]", released.toString())
            assertFalse(retries.isPending(relay))
        }

    @Test
    fun aKeyThatSendsNothingDoesNotCostASlot() =
        runTest {
            val released = mutableListOf<Released>()
            val retries = retries(released) { key -> key != "gone" }
            retries.schedule(relay, "a", 1_000)
            retries.schedule(relay, "gone", 1_000)
            retries.schedule(relay, "b", 1_000)
            advanceUntilIdle()

            assertEquals("[a@1000, gone@2101, b@2101]", released.toString())
        }

    @Test
    fun aFailingReleaseDoesNotWedgeTheRelay() =
        runTest {
            val released = mutableListOf<Released>()
            val retries = retries(released) { key -> if (key == "boom") error("socket gone") else true }
            retries.schedule(relay, "boom", 1_000)
            retries.schedule(relay, "b", 1_000)
            advanceUntilIdle()

            assertEquals(listOf("boom", "b"), released.map { it.key })
            assertFalse(retries.isPending(relay), "a batch left behind would defer this relay's commands forever")
        }

    @Test
    fun aReleasedKeyRefusedAgainGoesBackToTheHead() =
        runTest {
            val released = mutableListOf<Released>()
            lateinit var retries: RateLimitRetries
            var refusedOnce = false
            retries =
                retries(released) { key ->
                    if (key == "a" && !refusedOnce) {
                        refusedOnce = true
                        retries.schedule(relay, "a", 300)
                    }
                    true
                }
            retries.schedule(relay, "a", 1_000)
            retries.schedule(relay, "b", 1_000)
            advanceUntilIdle()

            assertEquals(listOf("a", "a", "b"), released.map { it.key })
        }

    @Test
    fun anAcceptedReleaseLetsTheNextGoAtOnce() =
        runTest {
            // A sliding window (nostream) states its whole period and then takes several: each
            // accepted retry releases the next one round trip later, not a period later.
            val released = mutableListOf<Released>()
            lateinit var retries: RateLimitRetries
            retries =
                retries(released) { key ->
                    launch {
                        delay(ROUND_TRIP_MS)
                        retries.onAccepted(relay, key)
                    }
                    true
                }
            listOf("a", "b", "c").forEach { retries.schedule(relay, it, 60_000) }
            // An answer for a key the queue did not just release moves nothing.
            retries.onAccepted(relay, "b")
            advanceUntilIdle()

            assertEquals("[a@60000, b@60010, c@60020]", released.toString())
            assertFalse(retries.isPending(relay))
        }

    @Test
    fun aKeyReleasedEarlyAndRefusedWaitsOutTheSpacingAgain() =
        runTest {
            // A token bucket: the release right after an accept meets an empty bucket, is refused,
            // and the queue falls back to the spaced release.
            val released = mutableListOf<Released>()
            lateinit var retries: RateLimitRetries
            var refusedB = false
            retries =
                retries(released) { key ->
                    launch {
                        delay(ROUND_TRIP_MS)
                        if (key == "b" && !refusedB) {
                            refusedB = true
                            retries.schedule(relay, "b", 1_000)
                        } else {
                            retries.onAccepted(relay, key)
                        }
                    }
                    true
                }
            listOf("a", "b", "c").forEach { retries.schedule(relay, it, 1_000) }
            advanceUntilIdle()

            // b goes out on a's accept, is refused, and waits the 1101 ms spacing from its release.
            assertEquals("[a@1000, b@1010, b@2111, c@2121]", released.toString())
            assertFalse(retries.isPending(relay))
        }

    @Test
    fun aDeferredKeyJoinsTheQueueWithoutMovingTheDeadline() =
        runTest {
            val released = mutableListOf<Released>()
            val retries = retries(released)
            assertFalse(retries.defer(relay, "x"), "nothing pending: the caller sends now")
            retries.schedule(relay, "a", 1_000)
            assertTrue(retries.defer(relay, "x"))
            advanceUntilIdle()

            assertEquals("[a@1000, x@2101]", released.toString())
        }

    @Test
    fun backOffsOutOfRangeAreNotScheduled() =
        runTest {
            val retries = retries(mutableListOf())
            assertFalse(retries.schedule(relay, "a", 0))
            assertFalse(retries.schedule(relay, "a", RateLimitRetries.MAX_DELAY_MS + 1))
            assertFalse(retries.isPending(relay))
        }

    companion object {
        const val ROUND_TRIP_MS = 10L
    }
}
