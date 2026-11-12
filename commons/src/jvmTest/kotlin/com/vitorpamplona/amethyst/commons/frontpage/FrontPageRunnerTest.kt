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

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.Decrypted
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ObserverScheduleState
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.QuorumRead
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.planScheduleEdit
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverEdition
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The reader's Brainstorm Assistant as the schedule's runner (NIP-FB, Runner): choosing it
 * rewrites the event for it, the reader's devices hold no job and never claim while it is named,
 * and the status line and missed-run warning read both Observer sites.
 */
class FrontPageRunnerTest {
    // 2026-11-11T11:00:00Z = 06:00 in New York.
    private val six = 1_794_394_800L
    private val reader = NostrSignerInternal(KeyPair())
    private val assistant = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())
    private val relay = NormalizedRelayUrl("wss://write.example/")
    private val ny = "America/New_York"

    private val task = ObserverSchedule.new(ny, 0).withEnabled(true, 0).withCron("0 6 * * *", ny, 0)
    private val on = FrontPageSchedule.daily(true, 7, 30)

    /** The reader's write relays, in memory: every read answers, every write lands. */
    private inner class Relays(
        initial: ScheduledTask?,
    ) : ScheduleRelays {
        val events = MutableStateFlow<List<ScheduledTaskEvent>>(emptyList())

        fun newestEvent() = ScheduledTaskClaims.winner(events.value)

        suspend fun put(task: ScheduledTask): ScheduledTaskEvent {
            val event: ScheduledTaskEvent = reader.sign(ObserverSchedule.build(task, reader, newestEvent(), six))
            events.value = events.value + event
            return event
        }

        init {
            initial?.let { runBlocking { put(it) } }
        }

        override suspend fun readQuorum(): QuorumRead {
            val newest = newestEvent()
            return QuorumRead(newest, newest?.let { taskOf(it) }, true, setOf(relay))
        }

        override suspend fun taskOf(event: ScheduledTaskEvent): ScheduledTask? = event.task(reader)

        override suspend fun publish(
            previous: ScheduledTaskEvent?,
            task: ScheduledTask,
            relays: Set<NormalizedRelayUrl>,
        ): Pair<ScheduledTaskEvent, Boolean> = put(task) to true

        override fun watch(
            relays: Set<NormalizedRelayUrl>,
            onSubscribed: (NormalizedRelayUrl) -> Unit,
        ): Flow<List<ScheduledTaskEvent>> = events.onStart { relays.forEach(onSubscribed) }

        override suspend fun edit(
            now: Long,
            resend: Boolean,
            transform: (ScheduledTask?) -> ScheduledTask?,
        ): ScheduleEdit {
            val read = readQuorum()
            val (next, outcome) = planScheduleEdit(read.event != null, read.task, read.quorum, transform)
            if (next != null) put(next)
            return outcome
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
        override suspend fun id(account: HexKey) = "here"

        override suspend fun forget(account: HexKey) {}
    }

    /** What the platform's scheduler was told, in order. */
    private class Recorder : FrontPageScheduler {
        val calls = mutableListOf<FrontPageSchedule>()
        var runs = 0

        override fun reschedule(
            account: HexKey,
            schedule: FrontPageSchedule,
        ) {
            calls += schedule
        }

        override fun runNow(account: HexKey) {
            runs++
        }
    }

    private suspend fun readable(task: ScheduledTask): SyncedSchedule.Readable {
        val event: ScheduledTaskEvent = reader.sign(ObserverSchedule.build(task, reader, previous = null, now = six))
        return SyncedSchedule.Readable(event, task)
    }

    private fun edition(
        printedAt: Long,
        sha: Char = 'a',
    ) = ObserverEdition(day = ObserverSite.utcDay(printedAt), path = ObserverSite.dayPath(ObserverSite.utcDay(printedAt)), sha256 = sha.toString().repeat(64), printedAt = printedAt)

    @Test
    fun choosingTheAssistantRewritesTheScheduleForItAndDevicesTakeItBack() =
        runTest {
            val relays = Relays(task.withParam("model", "anthropic/claude-sonnet-4.5"))
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, six, null) { it?.withRunner(assistant.pubKey) })
            val named = relays.newestEvent()!!
            // The public `p` names it, and the private tags are now NIP-44 between the reader and it: both read them.
            assertEquals(listOf(assistant.pubKey), named.runners())
            assertEquals("0 6 * * *", named.task(assistant)?.cron)
            assertEquals("anthropic/claude-sonnet-4.5", named.task(assistant)?.param("model")?.value())
            val asRead = assertNotNull(named.task(reader))
            assertEquals(assistant.pubKey, asRead.runner)
            // Nobody else reads them.
            assertNull(named.task(stranger))

            // Back to the reader's devices: no `p`, encrypted to the reader alone again, everything else kept.
            assertIs<ScheduleEdit.Saved>(editMigrating(relays, six + 1, null) { it?.withRunner(null) })
            val cleared = relays.newestEvent()!!
            assertTrue(cleared.runners().isEmpty())
            assertNull(cleared.task(assistant))
            assertEquals("anthropic/claude-sonnet-4.5", cleared.task(reader)?.param("model")?.value())
            // Choosing what is already chosen writes nothing.
            assertEquals(ScheduleEdit.Unchanged, editMigrating(relays, six + 2, null) { it?.withRunner(null) })
        }

    @Test
    fun theReadersDeviceOpensAScheduleEncryptedToTheAssistant() =
        runTest {
            val state = ObserverScheduleState(reader, LocalCache, backgroundScope, EmptyNostrClient(), MutableStateFlow(emptySet()))
            val named = readable(task.withRunner(assistant.pubKey)).event
            val opened = assertIs<Decrypted.Task>(state.decrypt(named))
            assertEquals(assistant.pubKey, opened.task.runner)
            assertEquals("0 6 * * *", opened.task.cron)
        }

    @Test
    fun theJobGoesWhileARunnerIsNamedAndComesBackWhenItIsCleared() =
        runTest {
            val synced = MutableStateFlow<SyncedSchedule>(readable(task))
            val scheduler = Recorder()
            backgroundScope.launch {
                FrontPageScheduleWatcher.follow(reader.pubKey, synced.map { scheduleForThisDevice(it, on, canPrintHere = true) }, scheduler) { false }
            }
            runCurrent()
            assertTrue(scheduler.calls.last().enabled)

            // The Assistant prints: this device's timer or WorkManager job is cancelled.
            synced.value = readable(task.withRunner(assistant.pubKey))
            runCurrent()
            assertFalse(scheduler.calls.last().enabled)

            // Two runners named (a conflict): nobody prints, this device neither.
            synced.value = readable(ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags))
            runCurrent()
            assertFalse(scheduler.calls.last().enabled)

            // Cleared: the job is back, at the schedule's time.
            synced.value = readable(task.withRunner(null))
            runCurrent()
            assertEquals(FrontPageSchedule(true, "0 6 * * *", ny), scheduler.calls.last())
            assertEquals(listOf(true, false, true), scheduler.calls.map { it.enabled })
        }

    @Test
    fun aDeviceNeverClaimsWhileARunnerIsNamedOrAParamIsUnknown() =
        runTest {
            val morning = FrontPageMorning(NoStore(), NoInstalls(), FrontPageInstallInfo("Desktop (Linux)", "test"), now = { six + 30 }, pause = {})
            for (blocked in listOf(task.withRunner(assistant.pubKey), task.withParam("size", "large"))) {
                val relays = Relays(blocked)
                val outcome =
                    morning.claimRun(relays, "here", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, _ -> fail("printed on schedule") }
                assertEquals(ScheduledOutcome.NOT_RUNNER, outcome)
                // No claim was written.
                assertEquals(1, relays.events.value.size)
                assertFalse(scheduleForThisDevice(readable(blocked), on, canPrintHere = true)!!.enabled)
            }
            // The same schedule without them is claimed and printed here.
            val relays = Relays(task)
            assertEquals(ScheduledOutcome.PRINTED, morning.claimRun(relays, "here", "Desktop (Linux)", alreadyDone = { _, _ -> false }) { _, _ -> ScheduledOutcome.PRINTED })
        }

    @Test
    fun theStatusLineSaysTheAssistantPrintedFromItsSite() =
        runTest {
            val named = readable(task.withRunner(assistant.pubKey))
            val sites = ObserverSites(reader = listOf(edition(six - 86_400, 'b')), runner = listOf(edition(six + 120)))
            val view = viewOf(named, on, canPrintHere = true, now = six + 3_600, deviceId = ny, sites = sites)
            assertEquals(ScheduleState.READY, view.state)
            assertEquals(assistant.pubKey, view.runner)
            assertTrue(view.namesRunner)
            assertFalse(view.runnerConflict)
            assertTrue(view.lastByRunner)
            assertNull(view.lastBy)
            assertEquals(six + 120, view.lastRan)
            assertEquals(3_480L / 60 * 60, view.lastRanAgo)
            assertNull(view.missedSince)

            // A manual print by the reader that morning is newer: the line names no Assistant.
            val manual = viewOf(named, on, true, now = six + 3_600, deviceId = ny, sites = sites.copy(reader = listOf(edition(six + 600, 'c'))))
            assertFalse(manual.lastByRunner)
            assertEquals(six + 600, manual.lastRan)
        }

    @Test
    fun theMissedRunWarningReadsBothSitesWithARunner() =
        runTest {
            val named = readable(task.withRunner(assistant.pubKey))
            val later = six + 4 * 3_600
            // Yesterday's paper by the Assistant, nothing today, more than three hours after six: missed.
            val yesterday = ObserverSites(runner = listOf(edition(six - 86_400 + 60)))
            val missed = viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday)
            assertEquals(six - 86_400 + 60, missed.missedSince)
            assertTrue(missed.lastByRunner)
            // Today's paper on either site: fine.
            assertNull(viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday.copy(runner = yesterday.runner + edition(six + 60, 'd'))).missedSince)
            assertNull(viewOf(named, on, true, now = later, deviceId = ny, sites = yesterday.copy(reader = listOf(edition(six + 300, 'e')))).missedSince)
            // Within the three hours: not yet.
            assertNull(viewOf(named, on, true, now = six + 3_600, deviceId = ny, sites = yesterday).missedSince)
            // Never printed by anyone: the warning has no day to name, so it says the Assistant hasn't printed.
            val never = viewOf(named, on, true, now = later, deviceId = ny)
            assertNull(never.missedSince)
            assertNotNull(never.missedDue)
            // Before the Assistant's site was read, nothing is called missed.
            val unread = viewOf(named, on, true, now = later, deviceId = ny, sites = ObserverSites(runnerRead = false))
            assertNull(unread.missedSince)
            assertNull(unread.missedDue)
            // Without a runner the device warning stays NIP-FB's, from `ran` alone.
            assertNull(viewOf(readable(task), on, true, now = later, deviceId = ny, sites = yesterday).missedDue)
        }

    @Test
    fun aConflictShowsEveryRunnerAndNoneAsTheOne() =
        runTest {
            val both = ScheduledTask(task.publicTags + arrayOf(PTag.assemble(assistant.pubKey, null), PTag.assemble(stranger.pubKey, null)), task.privateTags)
            val view = viewOf(readable(both), on, true, now = six, deviceId = ny)
            assertTrue(view.runnerConflict)
            assertNull(view.runner)
            assertEquals(listOf(assistant.pubKey, stranger.pubKey), view.runners)
            val who = WhoPrints(assistant.pubKey, "Brainstorm", view.runner, view.runners)
            assertTrue(who.conflict)
            assertFalse(who.byAssistant)
            assertFalse(who.byOther)
            // The fix keeps one: withRunner leaves exactly that `p`.
            assertEquals(listOf(assistant.pubKey), both.withRunner(assistant.pubKey).runners)
        }
}
