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
package com.vitorpamplona.quartz.nip01Core.relay.client.single.basic

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.OkMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CloseCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** How a relay's [RateLimitGate] holds and releases what the relay refused with a back-off, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class RateLimitGateTest {
    private class Sent(
        val what: String,
        val atMs: Long,
    ) {
        override fun toString() = "$what@$atMs"
    }

    private fun Command.what(): String =
        when (this) {
            is ReqCmd -> "$subId${if (filters.first().limit != null) ":${filters.first().limit}" else ""}"
            is EventCmd -> event.id
            is CountCmd -> queryId
            else -> label()
        }

    private fun TestScope.gate(
        sent: MutableList<Sent>,
        refresh: (Command) -> Command? = { it },
        onHold: (Command) -> Unit = {},
        onSend: (Command) -> Boolean = { true },
    ) = RateLimitGate(this, clock = { testScheduler.currentTime }, refresh = refresh, onHold = onHold, send = { cmd ->
        sent.add(Sent(cmd.what(), testScheduler.currentTime))
        onSend(cmd)
    })

    private fun req(
        subId: String,
        limit: Int? = null,
    ) = ReqCmd(subId, listOf(Filter(kinds = listOf(1), limit = limit)))

    private fun event(id: String) = EventCmd(Event(id, "", 0, 1, emptyArray(), "", ""))

    /** The relay refuses [subId]'s REQ with [backOffMs], and the pool hands the REQ straight back. */
    private fun RateLimitGate.refuseReq(
        subId: String,
        backOffMs: Long,
    ) {
        onIncoming(ClosedMessage.rateLimited(subId, "slow down", backOffMs))
        assertFalse(admit(req(subId)), "a refused REQ handed back is held")
    }

    /** Has the relay answer [msg] one round trip after now. */
    private fun TestScope.answerLater(
        gate: RateLimitGate,
        msg: Message,
    ) {
        launch {
            delay(ROUND_TRIP_MS)
            gate.onIncoming(msg)
        }
    }

    @Test
    fun nothingGoesOutBeforeTheLatestBackOffThenOnePerSpacedSlot() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent)
            gate.refuseReq("a", 1_000)
            gate.refuseReq("b", 800)
            gate.refuseReq("c", 1_000)
            advanceUntilIdle()

            // Nobody answers: spaced at the longest back-off plus a tenth, plus a millisecond.
            assertEquals("[a@1000, b@2101, c@3202]", sent.toString())
            assertFalse(gate.isHolding(ReqCmd.LABEL))
        }

    @Test
    fun aCommandThatFindsNoSocketDoesNotCostASlot() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent) { cmd -> cmd.what() != "gone" }
            gate.refuseReq("a", 1_000)
            gate.refuseReq("gone", 1_000)
            gate.refuseReq("b", 1_000)
            advanceUntilIdle()

            assertEquals("[a@1000, gone@2101, b@2101]", sent.toString())
        }

    @Test
    fun aFailingSendDoesNotWedgeTheGate() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent) { cmd -> if (cmd.what() == "boom") error("socket gone") else true }
            gate.refuseReq("boom", 1_000)
            gate.refuseReq("b", 1_000)
            advanceUntilIdle()

            assertEquals(listOf("boom", "b"), sent.map { it.what })
            assertFalse(gate.isHolding(ReqCmd.LABEL), "a lane left behind would hold this relay's REQs forever")
            assertTrue(gate.admit(req("c")))
        }

    @Test
    fun aReleasedCommandRefusedAgainGoesBackToTheHead() =
        runTest {
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            var refusedOnce = false
            gate =
                gate(sent) { cmd ->
                    if (cmd.what() == "a" && !refusedOnce) {
                        refusedOnce = true
                        launch {
                            delay(ROUND_TRIP_MS)
                            gate.refuseReq("a", 300)
                        }
                    }
                    true
                }
            gate.refuseReq("a", 1_000)
            gate.refuseReq("b", 1_000)
            advanceUntilIdle()

            assertEquals(listOf("a", "a", "b"), sent.map { it.what })
        }

    @Test
    fun aNewCommandWaitsBehindTheRefusedOnes() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent)
            assertTrue(gate.admit(req("x")), "nothing refused: it goes now")
            gate.refuseReq("a", 1_000)
            assertFalse(gate.admit(req("x")))
            advanceUntilIdle()

            assertEquals("[a@1000, x@2101]", sent.toString())
        }

    @Test
    fun aBackOffOutOfRangeHoldsNothing() =
        runTest {
            val gate = gate(mutableListOf())
            gate.onIncoming(ClosedMessage.rateLimited("a", "slow down", RateLimitGate.MAX_DELAY_MS + 1))
            assertFalse(gate.isHolding(ReqCmd.LABEL))
            assertTrue(gate.admit(req("b")))
            assertFalse(RateLimitGate.honors(RateLimitGate.MAX_DELAY_MS + 1))
            assertTrue(RateLimitGate.honors(1))
        }

    @Test
    fun anAnsweredReleaseLetsTheNextGoAtOnce() =
        runTest {
            // A sliding window (nostream) states its whole period and then takes several: each
            // answered command releases the next one round trip later, not a period later.
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            gate =
                gate(sent) { cmd ->
                    answerLater(gate, EoseMessage((cmd as ReqCmd).subId))
                    true
                }
            listOf("a", "b", "c").forEach { gate.refuseReq(it, 60_000) }
            // An answer for a command the gate did not just release moves nothing.
            gate.onIncoming(EoseMessage("b"))
            advanceUntilIdle()

            assertEquals("[a@60000, b@60010, c@60020]", sent.toString())
            assertFalse(gate.isHolding(ReqCmd.LABEL))
        }

    @Test
    fun aCommandReleasedEarlyAndRefusedWaitsOutTheSpacingAgain() =
        runTest {
            // A token bucket: the release right after an answer meets an empty bucket, is
            // refused, and the gate falls back to the spaced release.
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            var refusedB = false
            gate =
                gate(sent) { cmd ->
                    val subId = (cmd as ReqCmd).subId
                    launch {
                        delay(ROUND_TRIP_MS)
                        if (subId == "b" && !refusedB) {
                            refusedB = true
                            gate.refuseReq("b", 1_000)
                        } else {
                            gate.onIncoming(EoseMessage(subId))
                        }
                    }
                    true
                }
            listOf("a", "b", "c").forEach { gate.refuseReq(it, 1_000) }
            advanceUntilIdle()

            // b goes out on a's EOSE, is refused, and waits the 1101 ms spacing from its release.
            assertEquals("[a@1000, b@1010, b@2111, c@2121]", sent.toString())
        }

    @Test
    fun aNewerReqReplacesTheHeldOneAndACloseDropsIt() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent)
            gate.refuseReq("a", 1_000)
            assertFalse(gate.admit(req("b")))
            assertFalse(gate.admit(req("a", limit = 5)), "the newer filters replace the held ones")
            assertTrue(gate.admit(CloseCmd("b")), "a CLOSE is never held")
            advanceUntilIdle()

            assertEquals("[a:5@1000]", sent.toString())
        }

    @Test
    fun aHeldReqGoesOutWithItsSubsCurrentFilters() =
        runTest {
            // The sub's filters changed while its REQ waited: the release sends the new ones, once.
            val sent = mutableListOf<Sent>()
            var current = 5
            val gate = gate(sent, refresh = { cmd -> if (cmd is ReqCmd) req(cmd.subId, limit = current) else cmd })
            gate.refuseReq("a", 1_000)
            current = 9
            advanceUntilIdle()

            assertEquals("[a:9@1000]", sent.toString())
        }

    @Test
    fun aHeldCommandItsOwnerNoLongerWantsIsNotSentAndCostsNoSlot() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent, refresh = { cmd -> if (cmd is ReqCmd && cmd.subId == "stale") null else cmd })
            gate.refuseReq("stale", 1_000)
            gate.refuseReq("b", 1_000)
            advanceUntilIdle()

            assertEquals("[b@1000]", sent.toString())
        }

    @Test
    fun theOwnerIsToldOfEveryHeldCommand() =
        runTest {
            val held = mutableListOf<String>()
            val gate = gate(mutableListOf(), onHold = { held.add(it.what()) })
            assertTrue(gate.admit(req("x")))
            gate.refuseReq("a", 1_000)
            assertFalse(gate.admit(req("b")))
            assertFalse(gate.admit(req("b", limit = 3)))

            assertEquals(listOf("a", "b", "b:3"), held)
        }

    @Test
    fun aFailingRefreshSendsTheHeldCommand() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent, refresh = { error("pool gone") })
            gate.refuseReq("a", 1_000)
            advanceUntilIdle()

            assertEquals("[a@1000]", sent.toString())
        }

    @Test
    fun aCommandTakenForASocketThatDroppedIsNotWrittenToTheNextOne() =
        runTest {
            // The socket drops (and comes back) while a released REQ is between the queue and
            // the write: the reconnect replays the sub, so writing this copy too would put two
            // REQs for one sub on the new socket.
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            gate =
                gate(sent, refresh = { cmd ->
                    gate.onDisconnected()
                    cmd
                })
            gate.refuseReq("a", 1_000)
            advanceUntilIdle()

            assertEquals("[]", sent.toString())
            assertTrue(gate.isIdle())
        }

    @Test
    fun aCloseThatOvertakesAReleasedReqClosesItAgain() =
        runTest {
            // The unsubscribe lands while the gate is writing the REQ, outside its lock: the
            // CLOSE may reach the relay first, so the gate closes the sub again after the REQ.
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            gate =
                gate(sent) { cmd ->
                    if (cmd is ReqCmd) assertTrue(gate.admit(CloseCmd(cmd.subId)))
                    true
                }
            gate.refuseReq("a", 1_000)
            advanceUntilIdle()

            assertEquals("[a@1000, CLOSE@1000]", sent.toString())
        }

    @Test
    fun aNewConnectionBoundToTheGateGetsTheReleases() =
        runTest {
            val old = mutableListOf<Sent>()
            val gate = gate(old)
            gate.refuseReq("a", 1_000)
            val new = mutableListOf<Sent>()
            gate.bind { cmd ->
                new.add(Sent(cmd.what(), testScheduler.currentTime))
                true
            }
            advanceUntilIdle()

            assertEquals("[]", old.toString())
            assertEquals("[a@1000]", new.toString())
            assertTrue(gate.isIdle())
        }

    @Test
    fun aDisconnectDropsWhatIsHeldButKeepsTheBackOffForTheReplay() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent)
            gate.refuseReq("a", 1_000)
            gate.refuseReq("b", 1_000)
            advanceTimeBy(300)
            gate.onDisconnected()
            // The reconnect replays only what is still wanted, and it waits out the back-off.
            assertFalse(gate.admit(req("a")))
            advanceUntilIdle()

            assertEquals("[a@1000]", sent.toString())
        }

    @Test
    fun aCountsBackOffHoldsCountsNotReqs() =
        runTest {
            val sent = mutableListOf<Sent>()
            val gate = gate(sent)
            assertTrue(gate.admit(CountCmd("q", listOf(Filter(kinds = listOf(1))))))
            gate.onIncoming(ClosedMessage.rateLimited("q", "slow down", 1_000))

            assertTrue(gate.isHolding(CountCmd.LABEL))
            assertFalse(gate.isHolding(ReqCmd.LABEL))
            assertTrue(gate.admit(req("s")))
            assertFalse(gate.admit(CountCmd("q2", listOf(Filter(kinds = listOf(1))))))
            advanceUntilIdle()

            // Only what the gate held shows here: what it admitted, the caller sent itself.
            assertEquals("[q2@1000]", sent.toString())
        }

    @Test
    fun eventsAreHeldByTheirOk() =
        runTest {
            val sent = mutableListOf<Sent>()
            lateinit var gate: RateLimitGate
            gate =
                gate(sent) { cmd ->
                    answerLater(gate, OkMessage((cmd as EventCmd).event.id, true, ""))
                    true
                }
            assertTrue(gate.admit(event("e")))
            gate.onIncoming(OkMessage.rateLimited("e", "slow down", 1_000))
            assertFalse(gate.admit(event("e")))
            assertFalse(gate.admit(event("f")))
            assertTrue(gate.admit(req("s")), "an EVENT back-off does not hold REQs")
            advanceUntilIdle()

            assertEquals("[e@1000, f@1010]", sent.toString())
        }

    companion object {
        const val ROUND_TRIP_MS = 10L
    }
}
