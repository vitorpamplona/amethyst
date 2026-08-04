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
package com.vitorpamplona.quartz.nip01Core.store

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A bulk write fails as a unit, so without isolation one event the store cannot
 * take costs its whole batch — 999 good events per bad one at the default size,
 * dropped silently and counted as a multiple of the batch rather than as a number
 * of bad events. These pin the isolation that stops that.
 */
class BisectingInsertTest {
    private fun event(n: Int) =
        Event(
            id = n.toString().padStart(64, '0'),
            pubKey = "a1".repeat(32),
            createdAt = 1_700_000_000L + n,
            kind = 1,
            tags = emptyArray(),
            content = "e$n",
            sig = "b2".repeat(32),
        )

    /** Accepts everything except the named ids, which make the whole write throw. */
    private class Writer(
        private val poison: Set<String>,
    ) {
        val calls = mutableListOf<Int>()
        var eventsWritten = 0

        suspend fun write(batch: List<Event>): List<IEventStore.InsertOutcome> {
            calls.add(batch.size)
            batch.firstOrNull { it.id in poison }?.let {
                throw IndexOutOfBoundsException("Index: 1 Size: 1")
            }
            eventsWritten += batch.size
            return batch.map { IEventStore.InsertOutcome.Accepted }
        }
    }

    private val gaveUp = mutableListOf<Int>()

    private suspend fun run(
        events: List<Event>,
        poison: Set<String>,
    ): Triple<Writer, Int, List<Pair<Event, Throwable>>> {
        val writer = Writer(poison)
        var accepted = 0
        val poisoned = mutableListOf<Pair<Event, Throwable>>()
        gaveUp.clear()
        insertBisecting(
            events = events,
            write = { writer.write(it) },
            onOutcomes = { accepted += it.size },
            onPoison = { e, t -> poisoned.add(e to t) },
            onGaveUp = { batch, _ -> gaveUp.add(batch.size) },
        )
        return Triple(writer, accepted, poisoned)
    }

    @Test
    fun `a healthy batch is written once and costs nothing extra`() =
        runTest {
            val events = (1..64).map(::event)
            val (writer, accepted, poisoned) = run(events, emptySet())

            assertEquals(listOf(64), writer.calls, "no bisection on a batch that works")
            assertEquals(64, accepted)
            assertTrue(poisoned.isEmpty())
        }

    @Test
    fun `one poison event costs only itself and not the batch`() =
        runTest {
            val events = (1..64).map(::event)
            val bad = events[37].id
            val (_, accepted, poisoned) = run(events, setOf(bad))

            // This is the whole point: 63 of 64 still land.
            assertEquals(63, accepted, "every event except the poison one must still be written")
            assertEquals(1, poisoned.size)
            assertEquals(bad, poisoned.single().first.id, "the isolated event is the one that throws")
            assertTrue(poisoned.single().second is IndexOutOfBoundsException)
        }

    @Test
    fun `isolating stays logarithmic instead of falling back to one-by-one`() =
        runTest {
            val events = (1..1024).map(::event)
            val (writer, accepted, _) = run(events, setOf(events[500].id))

            assertEquals(1023, accepted)
            // ~2*log2(n) writes, nowhere near the 1024 a per-event fallback would cost.
            assertTrue(writer.calls.size < 32, "expected a logarithmic split, got ${writer.calls.size} writes")
        }

    @Test
    fun `several poison events are each isolated`() =
        runTest {
            val events = (1..64).map(::event)
            val bad = setOf(events[0].id, events[31].id, events[63].id)
            val (_, accepted, poisoned) = run(events, bad)

            assertEquals(61, accepted)
            assertEquals(bad, poisoned.map { it.first.id }.toSet())
        }

    @Test
    fun `a store-wide failure gives up instead of splitting all the way down`() =
        runTest {
            // Everything fails — a full disk, a dead engine. Splitting to singletons
            // would cost ~2n writes at the worst possible moment.
            val events = (1..1024).map(::event)
            val (writer, accepted, poisoned) = run(events, events.map { it.id }.toSet())

            assertEquals(0, accepted)
            assertTrue(
                writer.calls.size < 100,
                "a store-wide failure must not cost ~2n writes; spent ${writer.calls.size}",
            )
            // Nothing is silently lost: whatever isolation could not name is still
            // handed back, so the caller can count it.
            assertEquals(1024, poisoned.size + gaveUp.sum(), "every event must be accounted for")
            assertTrue(gaveUp.isNotEmpty(), "the remainder should be reported as unisolated")
        }

    @Test
    fun `the budget is spent isolating rather than hoarded`() =
        runTest {
            // One bad event in 1024 must still be found — the guard bounds the
            // pathological case without breaking the case it was built for.
            val events = (1..1024).map(::event)
            val (_, accepted, poisoned) = run(events, setOf(events[900].id))

            assertEquals(1023, accepted)
            assertEquals(events[900].id, poisoned.single().first.id)
            assertTrue(gaveUp.isEmpty(), "a single bad event fits well inside the budget")
        }

    @Test
    fun `a batch of nothing but poison loses nothing else`() =
        runTest {
            val events = (1..4).map(::event)
            val (_, accepted, poisoned) = run(events, events.map { it.id }.toSet())

            assertEquals(0, accepted)
            assertEquals(4, poisoned.size, "each one named, rather than one count of four")
        }

    @Test
    fun `cancellation propagates instead of being mistaken for a poison event`() =
        runTest {
            // Shutdown cancels the ingest scope mid-write. Treating that as "this
            // event is bad" would drop good events and keep bisecting while the
            // caller is trying to stop.
            val events = (1..16).map(::event)
            assertFailsWith<CancellationException> {
                insertBisecting(
                    events = events,
                    write = { throw CancellationException("shutting down") },
                    onOutcomes = { },
                    onPoison = { _, _ -> error("cancellation must not be reported as poison") },
                )
            }
        }

    @Test
    fun `an empty batch is a no-op`() =
        runTest {
            val (writer, accepted, poisoned) = run(emptyList(), emptySet())
            assertTrue(writer.calls.isEmpty())
            assertEquals(0, accepted)
            assertTrue(poisoned.isEmpty())
        }
}
