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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NIP-01 semantics of the inline small-REQ fast path (a REQ whose
 * filters all carry a small `limit` runs its replay on the receive
 * coroutine — see [RelaySession.INLINE_REPLAY_MAX_ROWS]). The wire
 * behavior must be indistinguishable from the launched path: stored
 * replay, EOSE, live tail, CLOSE handling, and same-subId replacement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InlineReqFastPathTest {
    private fun hexId(n: Int): String = n.toString().padStart(64, '0')

    private val pubkey = "46fcbe3065eaf1ae7811465924e48923363ff3f526bd6f73d7c184b16bd8ce4d"
    private val sig = "0".repeat(128)

    private fun testEvent(
        idSeed: Int,
        kind: Int = 1,
        createdAt: Long = idSeed.toLong(),
    ) = Event(hexId(idSeed), pubkey, createdAt, kind, emptyArray(), "note $idSeed", sig)

    private suspend fun serverWith(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        vararg events: Event,
    ): NostrServer {
        val store = EventStore(null)
        events.forEach { store.insert(it) }
        return NostrServer(store = store, policyBuilder = { EmptyPolicy }, parentContext = dispatcher)
    }

    @Test
    fun boundedReqRepliesStoredThenEoseThenLiveTail() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = serverWith(dispatcher, testEvent(1), testEvent(2, kind = 7))

            val frames = mutableListOf<String>()
            val session = server.connect { frames.add(it) }

            // limit=10 → inline path.
            session.receive("""["REQ","small",{"kinds":[1],"limit":10}]""")

            assertEquals(1, frames.count { it.startsWith("[\"EVENT\",\"small\"") })
            assertTrue(frames.first { it.startsWith("[\"EVENT\"") }.contains(hexId(1)))
            assertTrue(frames.last().startsWith("[\"EOSE\",\"small\""))

            // Live tail: a matching publish after EOSE must reach the sub.
            session.receive("""["EVENT",${testEvent(3).toJson()}]""")
            assertTrue(frames.any { it.startsWith("[\"EVENT\",\"small\"") && it.contains(hexId(3)) })
            // Non-matching kind stays out.
            session.receive("""["EVENT",${testEvent(4, kind = 7).toJson()}]""")
            assertTrue(frames.none { it.startsWith("[\"EVENT\",\"small\"") && it.contains(hexId(4)) })

            server.close()
        }

    @Test
    fun closeDetachesTheInlineLiveTail() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = serverWith(dispatcher, testEvent(1))

            val frames = mutableListOf<String>()
            val session = server.connect { frames.add(it) }

            session.receive("""["REQ","small",{"kinds":[1],"limit":10}]""")
            session.receive("""["CLOSE","small"]""")

            session.receive("""["EVENT",${testEvent(2).toJson()}]""")
            assertTrue(frames.none { it.startsWith("[\"EVENT\",\"small\"") && it.contains(hexId(2)) })

            server.close()
        }

    @Test
    fun sameSubIdReplacesInlineSubscription() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = serverWith(dispatcher, testEvent(1), testEvent(2, kind = 7))

            val frames = mutableListOf<String>()
            val session = server.connect { frames.add(it) }

            session.receive("""["REQ","sub",{"kinds":[1],"limit":10}]""")
            // Replace with a kind-7 filter (still inline). The old live
            // tail must be gone: a new kind-1 publish stays silent, a
            // kind-7 one is delivered.
            session.receive("""["REQ","sub",{"kinds":[7],"limit":10}]""")

            session.receive("""["EVENT",${testEvent(3, kind = 1).toJson()}]""")
            assertTrue(frames.none { it.startsWith("[\"EVENT\",\"sub\"") && it.contains(hexId(3)) })
            session.receive("""["EVENT",${testEvent(4, kind = 7).toJson()}]""")
            assertTrue(frames.any { it.startsWith("[\"EVENT\",\"sub\"") && it.contains(hexId(4)) })

            server.close()
        }

    @Test
    fun unboundedReqStillWorksViaLaunchedPath() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = serverWith(dispatcher, testEvent(1))

            val frames = mutableListOf<String>()
            val session = server.connect { frames.add(it) }

            // No limit → not inline-eligible; must behave identically.
            session.receive("""["REQ","big",{"kinds":[1]}]""")

            assertTrue(frames.any { it.startsWith("[\"EVENT\",\"big\"") && it.contains(hexId(1)) })
            assertTrue(frames.any { it.startsWith("[\"EOSE\",\"big\"") })

            session.receive("""["EVENT",${testEvent(2).toJson()}]""")
            assertTrue(frames.any { it.startsWith("[\"EVENT\",\"big\"") && it.contains(hexId(2)) })

            server.close()
        }

    @Test
    fun oversizedLimitSumIsNotInlineEligible() =
        runTest {
            // Behavior parity either way — this documents the boundary:
            // summed limits over the cap route to the launched path and
            // still answer correctly.
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = serverWith(dispatcher, testEvent(1))

            val frames = mutableListOf<String>()
            val session = server.connect { frames.add(it) }

            session.receive("""["REQ","big",{"kinds":[1],"limit":${RelaySession.INLINE_REPLAY_MAX_ROWS + 1}}]""")

            assertTrue(frames.any { it.startsWith("[\"EVENT\",\"big\"") && it.contains(hexId(1)) })
            assertTrue(frames.any { it.startsWith("[\"EOSE\",\"big\"") })

            server.close()
        }
}
