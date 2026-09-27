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
package com.vitorpamplona.amethyst.commons.cordn

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The loop that keeps one coordinator's groups current.
 *
 * Every test here is about the loop misbehaving rather than working: an
 * account with no groups burning a core, a coordinator outage ending the
 * session's sync silently, a backoff that never comes back down, a group
 * joined into a subscription that was already open. The happy path is one
 * `catch_up` and one `subscribe`, and it is not what this class is for.
 */
class CordnSyncLoopTest {
    /**
     * A [CordnSyncSource] that does exactly what it is told.
     *
     * `subscribe` parks on a signal rather than returning, because a real one
     * holds the stream open — a fake that returned immediately would turn every
     * test below into a spin and hide the very bug the no-spin test is for.
     */
    private class FakeSource : CordnSyncSource {
        private val _gids = MutableStateFlow<Set<String>>(emptySet())
        override val gids: StateFlow<Set<String>> = _gids.asStateFlow()

        var catchUps = 0
        var subscribes = 0
        var failCatchUpTimes = 0

        /**
         * How many catchUps blow their RPC budget.
         *
         * A real one throws [TimeoutCancellationException] -- a
         * `CancellationException` -- which is a different failure from
         * [failCatchUpTimes]'s plain exception and was, for a while, fatal to
         * the loop. Produced with a real `withTimeout` rather than a
         * hand-constructed instance, so the test cannot drift from what the
         * coordinator client actually throws.
         */
        var timeOutCatchUpTimes = 0

        /**
         * How many subscribes spend their budget instead of parking. Bounded,
         * because a fake that always spends it turns the loop into a spin in
         * virtual time and the test never returns.
         */
        var subscribeSpendsBudgetTimes = 0

        /** Handed to onDelivery once per catchUp, to prove the wiring. */
        var deliverOnCatchUp: CordnGroupManager.Delivery? = null

        /** Completed when the test wants the open subscription to close. */
        private val closeStream = MutableStateFlow(0)

        /** The gid sets each subscribe was opened for, in order. */
        val subscribedWith = mutableListOf<Set<String>>()

        fun setGroups(vararg gid: String) {
            _gids.value = gid.toSet()
        }

        fun closeOpenStream() {
            closeStream.value++
        }

        override suspend fun catchUp(onDelivery: (CordnGroupManager.Delivery) -> Unit): Int {
            catchUps++
            if (timeOutCatchUpTimes > 0) {
                timeOutCatchUpTimes--
                withTimeout(1) { delay(1_000) }
            }
            if (failCatchUpTimes > 0) {
                failCatchUpTimes--
                throw CordnGroupException("coordinator is down")
            }
            deliverOnCatchUp?.let(onDelivery)
            return 0
        }

        override suspend fun subscribe(
            timeoutMs: Long,
            onDelivery: (CordnGroupManager.Delivery) -> Unit,
        ) {
            subscribes++
            subscribedWith += _gids.value
            if (subscribeSpendsBudgetTimes > 0) {
                subscribeSpendsBudgetTimes--
                withTimeout(1) { delay(1_000) }
            }
            val opened = closeStream.value
            closeStream.first { it != opened }
        }
    }

    private fun loopOver(
        source: FakeSource,
        onDelivery: (CordnGroupManager.Delivery) -> Unit = {},
    ) = CordnSyncLoop(source, onDelivery, minBackoffMs = 1_000, maxBackoffMs = 8_000)

    @Test
    fun `an account with no groups parks instead of spinning`() =
        runTest {
            // catchUp and subscribe are both no-ops with no groups, so a plain
            // while(true) burns a core on every fresh account — and does it
            // silently, because nothing fails.
            val source = FakeSource()
            val loop = loopOver(source)
            loop.start(this)

            advanceTimeBy(60_000)
            runCurrent()

            assertEquals(CordnSyncLoop.State.NoGroups, loop.state.value)
            assertEquals(0, source.catchUps, "it called the coordinator with nothing to sync")
            assertEquals(0, source.subscribes)
            loop.stop()
        }

    @Test
    fun `it starts syncing as soon as the first group appears`() =
        runTest {
            // The other half: parking must not mean sleeping through the join.
            val source = FakeSource()
            val loop = loopOver(source)
            loop.start(this)
            runCurrent()
            assertEquals(CordnSyncLoop.State.NoGroups, loop.state.value)

            source.setGroups("g1")
            runCurrent()

            assertEquals(1, source.catchUps)
            assertEquals(CordnSyncLoop.State.Live, loop.state.value)
            loop.stop()
        }

    @Test
    fun `catch-up runs before the subscription`() =
        runTest {
            // Order, not preference: a subscription opened first would deliver
            // from the live edge while catch-up is still walking history, and
            // the UI would show today above last week.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()

            assertEquals(1, source.catchUps)
            assertEquals(1, source.subscribes)
            loop.stop()
        }

    @Test
    fun `a coordinator outage does not end the loop`() =
        runTest {
            // The failure this class exists for. An exception that escapes
            // stops syncing for the rest of the session, and from the UI that
            // is indistinguishable from a group where nobody is talking.
            val source = FakeSource()
            source.failCatchUpTimes = 3
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()

            assertIs<CordnSyncLoop.State.Retrying>(loop.state.value)

            advanceTimeBy(30_000)
            runCurrent()

            assertEquals(CordnSyncLoop.State.Live, loop.state.value, "it never recovered")
            assertEquals(1, source.subscribes)
            loop.stop()
        }

    @Test
    fun `the backoff grows, caps, and resets after a success`() =
        runTest {
            // Growing matters so a dead coordinator is not hammered. Resetting
            // matters more: without it one bad stretch leaves the loop at its
            // maximum delay for the rest of the session, so the next outage
            // costs a full cap even though the coordinator is fine.
            val source = FakeSource()
            source.failCatchUpTimes = 5
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()

            val waits = mutableListOf<Long>()
            repeat(5) {
                val state = loop.state.value
                if (state is CordnSyncLoop.State.Retrying) {
                    waits += state.inMs
                    advanceTimeBy(state.inMs + 1)
                    runCurrent()
                }
            }

            assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 8_000L), waits, "growth or cap is wrong")
            assertEquals(CordnSyncLoop.State.Live, loop.state.value)

            // Now fail once more: if the backoff had not reset, this would be
            // the cap rather than the floor.
            source.failCatchUpTimes = 1
            source.closeOpenStream()
            runCurrent()

            val after = loop.state.value
            assertIs<CordnSyncLoop.State.Retrying>(after)
            assertEquals(1_000L, after.inMs, "the backoff did not reset on success")
            loop.stop()
        }

    @Test
    fun `a closed stream is re-subscribed, not treated as a failure`() =
        runTest {
            // subscribe() returns normally on timeout. Counting that as an
            // error would put a healthy loop into permanent backoff.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()
            assertEquals(1, source.subscribes)

            source.closeOpenStream()
            runCurrent()

            assertEquals(2, source.subscribes)
            assertEquals(CordnSyncLoop.State.Live, loop.state.value, "a timeout was treated as an outage")
            loop.stop()
        }

    @Test
    fun `an RPC that times out is retried, not the end of the loop`() =
        runTest {
            // A coordinator that does not answer inside the 20-second RPC
            // budget throws TimeoutCancellationException, which IS a
            // CancellationException. Rethrowing it ended run() -- the job
            // completed, the state stayed CatchingUp, and the account received
            // no cordn message again until the app was restarted. Observed on
            // device: one msg_fetch_many timeout, then silence.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            source.timeOutCatchUpTimes = 1
            loop.start(this)
            // The budget is virtual time, so it only expires once time moves.
            advanceTimeBy(10)
            runCurrent()

            assertIs<CordnSyncLoop.State.Retrying>(loop.state.value, "a timeout ended the loop")

            advanceTimeBy(1_100)
            runCurrent()

            assertEquals(2, source.catchUps, "it never tried again")
            assertEquals(CordnSyncLoop.State.Live, loop.state.value)
            loop.stop()
        }

    @Test
    fun `a subscription that spends its budget re-subscribes`() =
        runTest {
            // msg_sub_many is given a total-time budget and throws when it runs
            // out; the stream ending on schedule is the normal case the loop
            // exists to re-open. As a CancellationException it did neither --
            // it ended the loop.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            source.subscribeSpendsBudgetTimes = 1
            loop.start(this)
            advanceTimeBy(10)
            runCurrent()

            assertEquals(CordnSyncLoop.State.Live, loop.state.value, "a spent budget was treated as an outage")
            assertEquals(2, source.subscribes, "it did not re-subscribe")
            loop.stop()
        }

    @Test
    fun `joining a group re-opens the subscription with it`() =
        runTest {
            // A subscription is opened for a fixed set of gids, so a group
            // joined a moment later is not in it. Without this the new room
            // stays empty until the stream times out — up to a minute of a
            // chat that looks broken.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()
            assertEquals(listOf(setOf("g1")), source.subscribedWith)

            source.setGroups("g1", "g2")
            runCurrent()

            assertEquals(setOf("g1", "g2"), source.subscribedWith.last(), "the new group was not picked up")
            assertEquals(2, source.subscribes)
            loop.stop()
        }

    @Test
    fun `stopping ends the loop and leaves nothing running`() =
        runTest {
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")
            loop.start(this)
            runCurrent()

            loop.stop()
            val subscribesAtStop = source.subscribes
            source.closeOpenStream()
            advanceTimeBy(120_000)
            runCurrent()

            assertEquals(CordnSyncLoop.State.Stopped, loop.state.value)
            assertEquals(subscribesAtStop, source.subscribes, "it kept running after stop()")
        }

    @Test
    fun `starting twice does not double every message`() =
        runTest {
            // Two loops on one session would both catch up and both deliver,
            // so every message would appear twice in the room.
            val source = FakeSource()
            val loop = loopOver(source)
            source.setGroups("g1")

            loop.start(this)
            loop.start(this)
            runCurrent()

            assertEquals(1, source.catchUps)
            assertEquals(1, source.subscribes)
            loop.stop()
        }

    @Test
    fun `deliveries reach the caller`() =
        runTest {
            // The loop's whole output. A loop that syncs perfectly and drops
            // what it syncs is the same as one that does not run.
            val delivered = mutableListOf<CordnGroupManager.Delivery>()
            val source = FakeSource()
            source.deliverOnCatchUp = CordnGroupManager.Delivery.Undecryptable("g1", 7, "sample")
            val loop = loopOver(source) { delivered += it }
            source.setGroups("g1")
            loop.start(this)
            runCurrent()

            assertEquals(1, delivered.size, "nothing reached the caller")
            val only = delivered.single()
            assertIs<CordnGroupManager.Delivery.Undecryptable>(only)
            assertEquals("g1", only.gid)
            assertEquals(7L, only.cursor)
            loop.stop()
        }
}
