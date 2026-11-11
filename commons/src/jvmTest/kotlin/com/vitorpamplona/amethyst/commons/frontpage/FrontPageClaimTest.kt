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
package com.vitorpamplona.amethyst.commons.frontpage

import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.QuorumRead
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** NIP-FB's claim protocol as [FrontPageMorning.claimRun] runs it, against relays kept in memory. */
class FrontPageClaimTest {
    // 2026-11-11T11:00:00Z = 06:00 in New York.
    private val six = 1_794_394_800L
    private val signer = NostrSignerInternal(KeyPair())
    private val relay = NormalizedRelayUrl("wss://write.example/")

    /** The reader's write relays, as one store: every read answers, every write lands, unless told otherwise. */
    private inner class Relays(
        task: ScheduledTask,
        private val clock: () -> Long,
    ) : ScheduleRelays {
        val events = MutableStateFlow<List<ScheduledTaskEvent>>(emptyList())

        /** Outcomes the next edits report after writing (NotAccepted: written to too few); empty: Saved. */
        val editOutcomes = ArrayDeque<ScheduleEdit>()
        var edits = 0

        suspend fun put(task: ScheduledTask): ScheduledTaskEvent {
            val previous = ScheduledTaskClaims.winner(events.value)
            val event: ScheduledTaskEvent = signer.sign(ObserverSchedule.build(task, signer, previous, clock()))
            events.value = events.value + event
            return event
        }

        suspend fun newest(): ScheduledTask? = ScheduledTaskClaims.winner(events.value)?.task(signer)

        /** Runs once, right after the next claim is published: another device acting meanwhile. */
        var afterPublish: (suspend () -> Unit)? = null

        init {
            runBlocking { put(task) }
        }

        override suspend fun readQuorum(): QuorumRead {
            val newest = ScheduledTaskClaims.winner(events.value)
            return QuorumRead(newest, newest?.let { taskOf(it) }, true, setOf(relay))
        }

        override suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? = event.task(signer)

        override suspend fun publish(
            previous: ScheduledTaskEvent?,
            task: ScheduledTask,
            relays: Set<NormalizedRelayUrl>,
        ): Pair<ScheduledTaskEvent, Boolean> {
            val published = put(task)
            afterPublish?.let {
                afterPublish = null
                it()
            }
            return published to true
        }

        override fun watch(
            relays: Set<NormalizedRelayUrl>,
            onSubscribed: (NormalizedRelayUrl) -> Unit,
        ): Flow<List<ScheduledTaskEvent>> = events.onStart { relays.forEach(onSubscribed) }

        override suspend fun edit(
            now: Long,
            resend: Boolean,
            transform: (ScheduledTask?) -> ScheduledTask?,
        ): ScheduleEdit {
            edits++
            val read = readQuorum()
            val (next, outcome) = planScheduleEdit(read.event != null, read.task, read.quorum, transform)
            if (next == null) return if (resend && outcome == ScheduleEdit.Unchanged && read.task != null && transform(read.task) == read.task) editOutcomes.removeFirstOrNull() ?: outcome else outcome
            put(next)
            return editOutcomes.removeFirstOrNull() ?: outcome
        }
    }

    private class NoStore : FrontPageScheduleStore {
        override fun flow(account: HexKey): Flow<FrontPageSchedule> = emptyFlow()

        override suspend fun set(
            account: HexKey,
            schedule: FrontPageSchedule,
        ) {}

        override suspend fun enabled(): Map<HexKey, FrontPageSchedule> = emptyMap()
    }

    private class NoInstalls : FrontPageInstallStore {
        override suspend fun id(account: HexKey) = "unused"

        override suspend fun forget(account: HexKey) {}
    }

    private val daily = ObserverSchedule.new("America/New_York", 0).withEnabled(true, 0).withCron("0 6 * * *", "America/New_York", 0)

    private fun TestScope.morning(clock: () -> Long) = FrontPageMorning(NoStore(), NoInstalls(), FrontPageInstallInfo("Desktop (Linux)", "test"), now = clock)

    /** The clock moves with the test's virtual time, from 06:00:10. */
    private fun TestScope.clock(): () -> Long = { six + 10 + testScheduler.currentTime / 1000 }

    @Test
    fun aSkipClaimsFirstSoItCannotTakeAMorningAnotherDeviceIsPrinting() =
        runTest {
            val clock = clock()
            // The phone claimed this morning a minute ago and is printing it.
            val relays = Relays(ScheduledTaskClaims.claimed(daily, six, "phone", "Pixel 8", six + 5), clock)
            var printed = false
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> true }) { _, _ -> ScheduledOutcome.PRINTED.also { printed = true } }
            assertEquals(ScheduledOutcome.ALREADY_CLAIMED, outcome)
            assertFalse(printed)
            // Nothing written over the phone's claim: no skip with the desktop's name on it.
            assertEquals(1, relays.events.value.size)
            assertEquals("phone", relays.newest()!!.claim!!.id)
        }

    @Test
    fun aSkipLosesToAClaimMadeWhileItSettles() =
        runTest {
            val clock = clock()
            val relays = Relays(daily, clock)
            // The phone read before this desktop's claim, and claims a moment after it: the newer claim wins.
            relays.afterPublish = { relays.put(ScheduledTaskClaims.claimed(relays.newest()!!, six, "phone", "Pixel 8", clock())) }
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> true }) { _, _ -> ScheduledOutcome.PRINTED }
            assertEquals(ScheduledOutcome.LOST_CLAIM, outcome)
            // No `ran` and no skip over the phone's claim: its print goes on.
            val newest = relays.newest()!!
            assertEquals("phone", newest.claim!!.id)
            assertNull(newest.ran)
        }

    @Test
    fun aSkipThatWinsItsClaimWritesOnlyRan() =
        runTest {
            val clock = clock()
            val relays = Relays(daily, clock)
            var printed = false
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> true }) { _, _ -> ScheduledOutcome.PRINTED.also { printed = true } }
            assertEquals(ScheduledOutcome.ALREADY_PRINTED, outcome)
            assertFalse(printed)
            val newest = relays.newest()!!
            assertEquals("desk", newest.claim!!.id)
            assertTrue(ScheduledTaskClaims.isFinished(newest))
            // The claim, then `ran`: two writes.
            assertEquals(3, relays.events.value.size)
        }

    @Test
    fun ranIsTriedAgainWhenTooFewRelaysTakeIt() =
        runTest {
            val clock = clock()
            val relays = Relays(daily, clock)
            relays.editOutcomes += ScheduleEdit.NotAccepted
            relays.editOutcomes += ScheduleEdit.Unreachable
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, keep -> if (keep()) ScheduledOutcome.PRINTED else ScheduledOutcome.LOST_CLAIM }
            assertEquals(ScheduledOutcome.PRINTED, outcome)
            assertEquals(3, relays.edits)
            assertTrue(ScheduledTaskClaims.isFinished(relays.newest()!!))
        }

    @Test
    fun anInstallTakesUpItsOwnUnfinishedClaimAfterDying() =
        runTest {
            val clock = clock()
            // This desktop claimed this morning, then the process died mid-print.
            val relays = Relays(ScheduledTaskClaims.claimed(daily, six, "desk", "Desktop (Linux)", six + 5), clock)
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, keep -> if (keep()) ScheduledOutcome.PRINTED else ScheduledOutcome.LOST_CLAIM }
            assertEquals(ScheduledOutcome.PRINTED, outcome)
            assertTrue(ScheduledTaskClaims.isFinished(relays.newest()!!))
        }

    @Test
    fun aPrintStoppedByAnotherClaimKeepsNothing() =
        runTest {
            val clock = clock()
            val relays = Relays(daily, clock)
            var kept: Boolean? = null
            val outcome =
                morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, keep ->
                    // Another device claims this morning while the paper is written (an edit made after a stale read).
                    relays.put(ScheduledTaskClaims.claimed(relays.newest()!!, six, "phone", "Pixel 8", clock()))
                    kept = keep()
                    if (kept == true) ScheduledOutcome.PRINTED else ScheduledOutcome.LOST_CLAIM
                }
            assertEquals(ScheduledOutcome.LOST_CLAIM, outcome)
            // Never kept (so never announced or published), and no `ran` for a run that lost.
            assertTrue(kept != true)
            assertNull(relays.newest()!!.ran)
        }

    @Test
    fun aLateRunLeavesTheMorningUnclaimed() =
        runTest {
            val clock = clock()
            val relays = Relays(daily, clock)
            // Two minutes left before the platform stops the run: not enough to claim, print and say so.
            val outcome = morning(clock).claimRun(relays, "desk", "Desktop (Linux)", alreadyDone = { _, _ -> false }, deadline = six + 180) { _, _ -> ScheduledOutcome.PRINTED }
            assertEquals(ScheduledOutcome.FAILED, outcome)
            assertNull(relays.newest()!!.claim)
        }
}
