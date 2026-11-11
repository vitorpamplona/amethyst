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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ObserverScheduleState
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskClaims.Decision
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTaskEvent
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How this install names itself in a claim (NIP-FB `claim.by`): "Pixel 8", "Desktop (Linux)". */
@Immutable
data class FrontPageInstallInfo(
    val name: String,
    val app: String,
)

/** What the schedule section shows, from the synced schedule and this device's own choice. */
@Immutable
data class MorningScheduleView(
    /** False when the synced schedule exists but can't be read here: nothing to show or edit. */
    val readable: Boolean = true,
    val enabled: Boolean = false,
    /** The expression as written, and its zone. */
    val cron: String = ScheduledTask.DEFAULT_CRON,
    val zone: String? = null,
    /** The time and weekdays the editor shows; null when only the raw expression can be shown. */
    val simple: SimpleCron? = SimpleCron.parse(ScheduledTask.DEFAULT_CRON),
    /** "Print from this device": this device takes part in the mornings. */
    val printsHere: Boolean = false,
    /** This install can print with nobody there, so it may take part. */
    val canPrintHere: Boolean = false,
    /** Who printed last ([lastRan]): the claim's `by`. */
    val lastBy: String? = null,
    val lastRan: Long? = null,
    /** When the last run was, when a due one hasn't happened (NIP-FB's warning); null when fine. */
    val missedSince: Long? = null,
) {
    /** The schedule as written, for its next run on this device's clock. */
    fun schedule() = FrontPageSchedule(enabled, cron, zone)
}

/** Whether [task] is a morning schedule this app runs: the Observer task with a cron it can read. */
fun runsHereAtAll(task: ScheduledTask) = task.task == ObserverSchedule.TASK && task.expression() != null

/**
 * The schedule this device keeps, or null when there is nothing to decide on yet: no synced
 * schedule has arrived and this device had no local schedule (then whatever is scheduled stays).
 * A device prints only where the reader turned it on ([optedIn]) and only if it can print alone;
 * the old local schedule stands in until the synced one is known.
 */
fun scheduleForThisDevice(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
): FrontPageSchedule? =
    when (synced) {
        is SyncedSchedule.Readable -> {
            val task = synced.task
            FrontPageSchedule(task.enabled && optedIn.enabled && canPrintHere && runsHereAtAll(task), task.cron.orEmpty(), task.tz)
        }
        is SyncedSchedule.Unreadable -> FrontPageSchedule()
        SyncedSchedule.Unknown -> optedIn.takeIf { it.enabled && canPrintHere }
    }

fun viewOf(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
    now: Long = TimeUtils.now(),
): MorningScheduleView =
    when (synced) {
        is SyncedSchedule.Readable -> {
            val task = synced.task
            MorningScheduleView(
                enabled = task.enabled,
                cron = task.cron.orEmpty(),
                zone = task.tz,
                simple = task.simple(),
                printsHere = optedIn.enabled,
                canPrintHere = canPrintHere,
                lastBy = task.claim?.by,
                lastRan = task.ran,
                missedSince = task.ran?.takeIf { task.missedRun(FrontPageClock.cronZone(task.tz), now) != null },
            )
        }
        is SyncedSchedule.Unreadable -> MorningScheduleView(readable = false, printsHere = optedIn.enabled, canPrintHere = canPrintHere)
        SyncedSchedule.Unknown ->
            MorningScheduleView(
                enabled = optedIn.enabled,
                cron = optedIn.cron,
                simple = optedIn.simple(),
                printsHere = optedIn.enabled,
                canPrintHere = canPrintHere,
            )
    }

/**
 * The morning schedule for every account on this install: one NIP-FB task (kind 34817, `d` =
 * `observer`) shared by all of the reader's devices; any device where the reader turned on
 * "Print from this device" may print an occurrence, and the first to claim it does
 * (`quartz/plans/2026-11-11-observer-site.md`, "The morning schedule, synced"). Reads and edits
 * go through [Account.observerSchedule]. This install's claim id and its own on/off ([optIns],
 * the old per-device schedule) are kept here, in this install's settings.
 */
class FrontPageMorning(
    val optIns: FrontPageScheduleStore,
    private val installs: FrontPageInstallStore,
    private val info: FrontPageInstallInfo?,
    private val now: () -> Long = { TimeUtils.now() },
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun installId(account: Account) = installs.id(account.signer.pubKey)

    /** This install can print with nobody there: it can print at all, and the key is on the device. */
    fun canPrintHere(account: Account): Boolean = info != null && unattendedBlocker(account.signer, supportedBoundary, busy = false) == null

    private val supportedBoundary by lazy { paperBoundary() }

    /** The schedule this device keeps for [account], as it changes; null while undecided. */
    fun here(account: Account): Flow<FrontPageSchedule?> = combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey)) { synced, mine -> scheduleForThisDevice(synced, mine, canPrintHere(account)) }

    /** What the screen shows; recomputed every minute too, so a screen left open overnight doesn't show yesterday. */
    fun view(account: Account): Flow<MorningScheduleView> = combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey), minutes()) { synced, mine, at -> viewOf(synced, mine, canPrintHere(account), at) }.distinctUntilChanged()

    private fun minutes(): Flow<Long> =
        flow {
            while (true) {
                emit(now())
                delay(60_000)
            }
        }

    /** The schedule this device keeps, from what is known now: for the platform's next job. */
    suspend fun scheduleHere(account: Account): FrontPageSchedule = scheduleForThisDevice(account.observerSchedule.flow.value, optIns.get(account.signer.pubKey), canPrintHere(account)) ?: FrontPageSchedule()

    /**
     * At account load: a device that had its own schedule and finds no synced one (the relays
     * answered and hold none) publishes it, in this device's zone. It stays on here.
     */
    suspend fun migrate(account: Account) {
        val mine = optIns.get(account.signer.pubKey)
        if (!mine.enabled) return
        val zone = FrontPageClock.localZone()
        val at = now()
        // Set now: the mornings this device missed before it moved aren't anyone's to catch up.
        account.observerSchedule.edit(at) { current -> if (current == null) ObserverSchedule.new(zone, at).withEnabled(true, at).withCron(mine.cron, zone, at) else null }
    }

    /** On or off for every device. Switched on, this device takes part when it can. */
    suspend fun setEnabled(
        account: Account,
        on: Boolean,
    ): ScheduleEdit {
        val at = now()
        val outcome = edit(account) { it.withEnabled(on, at) }
        if (on && outcome !is ScheduleEdit.Unreachable && outcome !is ScheduleEdit.Unreadable && canPrintHere(account)) setPrintsHere(account, true)
        return outcome
    }

    /** A new schedule written in this device's zone. */
    suspend fun setCron(
        account: Account,
        cron: String,
    ): ScheduleEdit {
        val zone = FrontPageClock.localZone()
        val at = now()
        return edit(account) { it.withCron(cron, zone, at) }
    }

    /** "Print from this device": only a device that can print alone takes part. */
    suspend fun setPrintsHere(
        account: Account,
        on: Boolean,
    ) {
        if (on && !canPrintHere(account)) return
        val pubKey = account.signer.pubKey
        optIns.set(pubKey, optIns.get(pubKey).copy(enabled = on))
    }

    /** One read-modify-write; with no synced schedule, [change] starts from a new one, off, 7:00 here. */
    private suspend fun edit(
        account: Account,
        change: (ScheduledTask) -> ScheduledTask,
    ): ScheduleEdit {
        val zone = FrontPageClock.localZone()
        val mine = optIns.get(account.signer.pubKey)
        val at = now()
        val seed = ObserverSchedule.new(zone, at).let { if (mine.enabled) it.withCron(mine.cron, zone, at) else it }
        return account.observerSchedule.edit(now()) { current -> change(current ?: seed) }
    }

    /**
     * One scheduled occurrence, by NIP-FB's claim protocol ([ScheduledTaskClaims]): a random
     * wait, a read that more than half of the write relays answer, then skip (today's paper
     * exists: [alreadyDone], which writes the claim and `ran` without printing), or claim (more
     * than half must accept it), listen [ScheduledTaskClaims.SETTLE_SECONDS] for a competing
     * claim, and [run] only if this device's claim is the newest. An edit that overwrote the claim
     * without seeing it sends this back to the read, a few times at most. While [run] runs, a
     * newer claim of this occurrence by another device stops it, before anything is published. A
     * run that printed sets `ran`.
     */
    suspend fun runOccurrence(
        account: Account,
        alreadyDone: suspend () -> Boolean,
        run: suspend () -> ScheduledOutcome,
    ): ScheduledOutcome {
        if (!optIns.get(account.signer.pubKey).enabled) return ScheduledOutcome.NOT_RUNNER
        val id = installId(account)
        val by = info?.name

        pause(RandomInstance.int(ScheduledTaskClaims.MAX_JITTER_SECONDS.toInt() + 1) * 1000L)

        val state = account.observerSchedule
        var first: Long? = null
        repeat(ScheduledTaskClaims.MAX_ROUNDS) {
            val read = state.readQuorum()
            if (!read.quorum) return ScheduledOutcome.NO_QUORUM
            // No schedule, or one this signer can't decrypt: never run, never write.
            val event = read.event ?: return ScheduledOutcome.NOT_RUNNER
            val task = read.task ?: return ScheduledOutcome.NOT_RUNNER
            val at = now()
            val occurrence = ScheduledTaskClaims.occurrence(task, FrontPageClock.cronZone(task.tz), at) ?: return ScheduledOutcome.NOT_DUE
            // Back for another round only while it is still the same occurrence.
            if (first != null && first != occurrence) return ScheduledOutcome.NOT_DUE
            first = occurrence
            when (ScheduledTaskClaims.decide(task, ObserverSchedule.TASK, occurrence, at)) {
                Decision.NOT_RUNNABLE -> return ScheduledOutcome.NOT_RUNNER
                Decision.ALREADY_CLAIMED -> return ScheduledOutcome.ALREADY_CLAIMED
                Decision.CLAIM -> {}
            }

            if (alreadyDone()) {
                // The claim and `ran` in one write: done for every device, without printing.
                state.publishClaim(event, ScheduledTaskClaims.skipped(task, occurrence, id, by, now()), read.relays)
                return ScheduledOutcome.ALREADY_PRINTED
            }

            claimAndRun(state, read.relays, event, task, occurrence, id, by, run)?.let { return it }
        }
        return ScheduledOutcome.LOST_CLAIM
    }

    /** Steps 3 to 6: the claim, the wait, the run under watch. Null when the claim was overwritten unseen: read again. */
    private suspend fun claimAndRun(
        state: ObserverScheduleState,
        relays: Set<NormalizedRelayUrl>,
        event: ScheduledTaskEvent,
        task: ScheduledTask,
        occurrence: Long,
        id: String,
        by: String?,
        run: suspend () -> ScheduledOutcome,
    ): ScheduledOutcome? =
        coroutineScope {
            val seen = MutableStateFlow<List<ScheduledTaskEvent>>(listOf(event))
            val watching = launch { state.watch(relays).collect { incoming -> seen.update { (it + incoming).distinctBy { e -> e.id } } } }
            try {
                val (mine, accepted) = state.publishClaim(event, ScheduledTaskClaims.claimed(task, occurrence, id, by, now()), relays)
                seen.update { it + mine }
                if (!accepted) return@coroutineScope ScheduledOutcome.NO_QUORUM
                pause(ScheduledTaskClaims.SETTLE_SECONDS * 1000)

                val newest = ScheduledTaskClaims.winner(seen.value)?.let { state.taskOf(it) } ?: return@coroutineScope ScheduledOutcome.LOST_CLAIM
                when (ScheduledTaskClaims.settle(newest, occurrence, id)) {
                    ScheduledTaskClaims.Settled.LOST -> return@coroutineScope ScheduledOutcome.LOST_CLAIM
                    ScheduledTaskClaims.Settled.AGAIN -> return@coroutineScope null
                    ScheduledTaskClaims.Settled.OURS -> {}
                }

                var lost = false
                val printing = async { run() }
                val guard =
                    launch {
                        seen.collect { events ->
                            val latest = ScheduledTaskClaims.winner(events)?.let { state.taskOf(it) } ?: return@collect
                            if (ScheduledTaskClaims.claimsForAnother(latest, occurrence, id)) {
                                lost = true
                                printing.cancel()
                            }
                        }
                    }
                val outcome =
                    try {
                        printing.await()
                    } catch (e: CancellationException) {
                        if (!lost) throw e
                        ScheduledOutcome.LOST_CLAIM
                    } finally {
                        guard.cancel()
                    }
                if (outcome == ScheduledOutcome.PRINTED) {
                    val done = now()
                    state.edit(done) { current -> current?.takeIf { ScheduledTaskClaims.isOurs(it, occurrence, id) }?.let { ScheduledTaskClaims.finished(it, done) } }
                }
                outcome
            } finally {
                watching.cancel()
            }
        }

    suspend fun forget(account: HexKey) {
        optIns.set(account, FrontPageSchedule())
        installs.forget(account)
    }
}

/**
 * Keeps each loaded account's platform job in step with the synced schedule and this device's
 * own choice: scheduled while the schedule is on and this device prints, cancelled otherwise,
 * moved whenever either changes. At account load it also moves an old local schedule to the
 * synced one, and runs a morning missed while the app was closed (the claim decides whether
 * there is anything left to do).
 */
class FrontPageScheduleWatcher(
    private val morning: FrontPageMorning,
    private val accounts: StateFlow<Map<HexKey, Account>>,
    private val scheduler: FrontPageScheduler,
    private val scope: CoroutineScope,
) {
    private val jobs = HashMap<HexKey, Pair<Account, Job>>()

    fun start() {
        scope.launch {
            accounts.collect { loaded ->
                jobs.keys.filter { loaded[it] !== jobs[it]?.first }.forEach { jobs.remove(it)?.second?.cancel() }
                loaded.forEach { (pubKey, account) ->
                    if (pubKey in jobs) return@forEach
                    jobs[pubKey] = account to scope.launch { watch(pubKey, account) }
                }
            }
        }
    }

    private suspend fun watch(
        pubKey: HexKey,
        account: Account,
    ) = coroutineScope {
        launch {
            try {
                morning.migrate(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not move the morning schedule", e)
            }
        }
        var first = true
        morning
            .here(account)
            .filterNotNull()
            .distinctUntilChanged()
            .collect { schedule ->
                scheduler.reschedule(pubKey, schedule)
                if (first && schedule.enabled && schedule.lastRun(TimeUtils.now()) != null) scheduler.runNow(pubKey)
                first = false
            }
    }

    companion object {
        private const val TAG = "FrontPageScheduleWatcher"
    }
}
