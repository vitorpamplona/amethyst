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
package com.vitorpamplona.quartz.nip01Core.relay.client.paging

import com.vitorpamplona.geode.fixtures.SyntheticEvents
import com.vitorpamplona.geode.testing.RelayClientTest
import com.vitorpamplona.geode.testing.collectUntilEose
import com.vitorpamplona.geode.testing.preload
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins down the relay-side contract the whole [RelayLoadingCursors] / [BackwardRelayPager] design rests on,
 * against the in-process relay: a backward `until`+`limit` walk returns each event **exactly once**
 * (no re-download), in **newest-first** capped pages, and an **empty page + EOSE** is the gap-proof
 * stop. If a relay ever stopped honouring this (e.g. oldest-first, or ignoring `until`), these break —
 * which is exactly the signal the pager's correctness depends on.
 */
class UntilLimitPagingRelayTest : RelayClientTest() {
    @Test
    fun backwardUntilLimitWalkCoversEveryEventOnceAndStopsOnEmptyPage() =
        runBlocking {
            // 250 regular events, createdAt 1..250 (distinct pubkeys so none collapse).
            defaultRelay.preload(SyntheticEvents.batch(TOTAL, kind = KIND))

            val seenIds = mutableSetOf<String>()
            var totalReceived = 0
            var pages = 0
            var until: Long? = null

            while (pages < SAFETY_CAP) {
                val (events, eose) =
                    client.collectUntilEose(
                        defaultRelayUrl,
                        Filter(kinds = listOf(KIND), until = until, limit = LIMIT),
                    )
                assertTrue(eose, "every page must end with EOSE")

                if (events.isEmpty()) break // gap-proof stop: empty page = nothing older

                pages++
                assertTrue(events.size <= LIMIT, "page must respect the limit")
                // Newest-first + cursor honoured: nothing newer than the cursor leaks into a later page.
                until?.let { cursor -> assertTrue(events.all { it.createdAt <= cursor }, "page must be older than the cursor") }

                events.forEach { e: Event ->
                    seenIds.add(e.id)
                    totalReceived++
                }
                until = events.minOf { it.createdAt } - 1
            }

            // No re-download: total delivered equals the corpus, and every id is distinct.
            assertEquals(TOTAL, totalReceived, "no event should be delivered twice across pages")
            assertEquals(TOTAL, seenIds.size, "every event fetched exactly once")
            // 250 / 100 → 100 + 100 + 50, then an empty page stops the walk.
            assertEquals(3, pages)
        }

    @Test
    fun anEmptyRelayAnswersOneEmptyPageWithEose() =
        runBlocking {
            val (events, eose) =
                client.collectUntilEose(
                    defaultRelayUrl,
                    Filter(kinds = listOf(KIND), until = null, limit = LIMIT),
                )
            assertTrue(eose)
            assertEquals(0, events.size)
        }

    companion object {
        private const val KIND = 1
        private const val TOTAL = 250
        private const val LIMIT = 100
        private const val SAFETY_CAP = 10
    }
}
