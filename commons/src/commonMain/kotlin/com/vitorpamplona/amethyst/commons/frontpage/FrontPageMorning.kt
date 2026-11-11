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
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleRelays
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSchedule
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronZone
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/** How this install names itself in a claim (NIP-FB `claim.by`): "Pixel 8", "Desktop (Linux)". */
@Immutable
data class FrontPageInstallInfo(
    val name: String,
    val app: String,
)

/**
 * A schedule in words, on this device's clock where it can be moved there without changing its
 * day, else on the schedule's own clock with its [zone] named.
 */
@Immutable
sealed interface CronWords {
    /** A time on some weekdays (ISO, null = every day): "Every weekday at 6:00 AM". */
    data class Days(
        val time: SimpleCron,
        val zone: String? = null,
    ) : CronWords

    /** Two to four times a day, every day: "3 times a day at 6:00 AM, 12:00 PM and 6:00 PM". */
    data class TimesADay(
        val times: List<Pair<Int, Int>>,
        val zone: String? = null,
    ) : CronWords

    /** "Every 6 hours, at :15". */
    data class EveryHours(
        val hours: Int,
        val minute: Int,
    ) : CronWords

    /** "Monthly on day 1 at 6:00 AM". */
    data class Monthly(
        val day: Int,
        val hour: Int,
        val minute: Int,
        val zone: String? = null,
    ) : CronWords

    /** Nothing plainer to say: the expression as written. */
    data class Raw(
        val cron: String,
    ) : CronWords
}

/** What the schedule section can show. */
enum class ScheduleState {
    /** Nothing read yet. */
    LOADING,

    /** No synced schedule: the shared switch is off and nothing is made up. */
    NOT_SET_UP,

    /** Read and understood. */
    READY,

    /** The signer needs the reader to open it ([MorningScheduleView.retryable] says whether asking again can help). */
    CANT_DECRYPT,

    /** The event isn't a schedule this app reads: its content, its cron or its zone. Shown, never run or overwritten. */
    UNREADABLE,
}

/** What the schedule section shows, from the synced schedule and this device's own choice. */
@Immutable
data class MorningScheduleView(
    val state: ScheduleState = ScheduleState.LOADING,
    /** Asking the signer again may open it. */
    val retryable: Boolean = false,
    val enabled: Boolean = false,
    /** The expression as written, and its zone, when there is one. */
    val cron: String? = null,
    val zone: String? = null,
    /** The schedule in words, when it is [ScheduleState.READY] (or its cron is at least shown). */
    val words: CronWords? = null,
    /** The time of day this device's editor shows and changes; null when it can't be changed here. */
    val editableTime: Pair<Int, Int>? = null,
    /** "Print from this device": this device takes part in the mornings. */
    val printsHere: Boolean = false,
    /** This install can print with nobody there, so it may take part. */
    val canPrintHere: Boolean = false,
    /** The reader has NIP-65 write relays: the only place a shared schedule lives. */
    val hasWriteRelays: Boolean = true,
    /** Who printed last: the claim's `by`, only when that claim is the one [lastRan] finished. */
    val lastBy: String? = null,
    val lastRan: Long? = null,
    /** How long ago [lastRan] was, rounded to what the line says (minutes, hours or days). */
    val lastRanAgo: Long? = null,
    /** When the last run was, when a due one hasn't happened (NIP-FB's warning); null when fine. */
    val missedSince: Long? = null,
)

/** Whether [task] is a morning schedule this app runs: the Observer task, a cron it reads, a zone this platform knows. */
fun runsHereAtAll(task: ScheduledTask) = task.task == ObserverSchedule.TASK && task.expression() != null && FrontPageClock.cronZone(task.tz) != null

/**
 * The schedule this device keeps, or null when there is nothing to decide on yet (no synced
 * schedule seen: then whatever is scheduled stays). A device prints only where the reader turned
 * it on ([optedIn]), only if it can print alone, and only with write relays to claim on.
 */
fun scheduleForThisDevice(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
    hasWriteRelays: Boolean = true,
): FrontPageSchedule? =
    when (synced) {
        is SyncedSchedule.Readable -> {
            val task = synced.task
            FrontPageSchedule(task.enabled && optedIn.enabled && canPrintHere && hasWriteRelays && runsHereAtAll(task), task.cron.orEmpty(), task.tz)
        }
        SyncedSchedule.Unknown -> null
        else -> FrontPageSchedule()
    }

/** [seconds] rounded down to what "… ago" says: minutes under an hour, hours under a day, else days. */
fun agoBucket(seconds: Long): Long =
    when {
        seconds < 3_600L -> (seconds / 60L).coerceAtLeast(0) * 60L
        seconds < 86_400L -> seconds / 3_600L * 3_600L
        else -> seconds / 86_400L * 86_400L
    }

/**
 * [cron] in words ([CronWords]), read in [zone] (named [zoneId]) and told on [device]'s clock
 * (named [deviceId]; null where this platform can't say): a time and weekdays from one run no
 * clock change moved, a few times a day, every few hours, monthly, else as written.
 */
fun describeCron(
    cron: CronExpression,
    zoneId: String,
    zone: CronZone,
    deviceId: String?,
    device: CronZone?,
    now: Long,
): CronWords {
    val same = deviceId == zoneId
    val label = zoneId.takeUnless { same }
    SimpleCron.parse(cron.text)?.let { simple ->
        if (same) return CronWords.Days(simple)
        if (device != null) simple.seenFrom(zone, device, now)?.let { return CronWords.Days(it) }
        return CronWords.Days(simple, label)
    }
    val fields = cron.text.split(' ')
    val times = (0..23).flatMap { h -> (0..59).filter { m -> cron.runsAt(h, m) }.map { m -> h to m } }
    val minutes = times.map { it.second }.distinct()
    // How far this device's clock is ahead of the schedule's, at its next run.
    val ahead = if (device != null && !same) cron.nextAfter(now, zone)?.let { (device.offsetSecondsAt(it) - zone.offsetSecondsAt(it)) / 60 } else 0
    val everyDay = fields[2] == "*" && fields[3] == "*" && fields[4] == "*"
    val hourStep =
        when {
            fields[1] == "*" -> 1
            fields[1].startsWith("*/") -> fields[1].removePrefix("*/").toIntOrNull()
            else -> null
        }
    if (everyDay && hourStep != null && 24 % hourStep == 0 && minutes.size == 1 && ahead != null) {
        return CronWords.EveryHours(hourStep, (minutes[0] + ahead).mod(60))
    }

    fun onDevice(list: List<Pair<Int, Int>>): Pair<List<Pair<Int, Int>>, String?> {
        if (ahead == null || (!same && device == null)) return list to label
        val moved = list.map { (h, m) -> h * 60 + m + ahead }
        return if (moved.all { it in 0 until 1_440 }) moved.map { (it / 60) to (it % 60) }.sortedBy { it.first * 60 + it.second } to null else list to label
    }
    if (everyDay && times.size in 2..4) {
        val (shown, zoneShown) = onDevice(times)
        return CronWords.TimesADay(shown, zoneShown)
    }
    val day = fields[2].toIntOrNull()
    if (day != null && fields[3] == "*" && fields[4] == "*" && times.size == 1) {
        val (shown, zoneShown) = onDevice(times)
        return CronWords.Monthly(day, shown[0].first, shown[0].second, zoneShown)
    }
    return CronWords.Raw(cron.text)
}

/** What changing the time to [hour]:[minute] on this device's clock does to [task]. */
sealed interface TimeEdit {
    data class Change(
        val task: ScheduledTask,
    ) : TimeEdit

    /** The time shown, confirmed: nothing to write. */
    data object Unchanged : TimeEdit

    /** The schedule isn't a time and weekdays this editor writes, or its zone isn't known here. */
    data object NotEditable : TimeEdit

    /** The time can't be said once in the schedule's zone on that day. */
    data object NotExpressible : TimeEdit
}

/**
 * The time edit: on a device in the schedule's own zone, the fields change in place. From
 * another zone, the run the editor shows moves to the picked time on the device's calendar day,
 * and the expression follows it in the schedule's zone (weekdays moved when it crosses midnight
 * there), so the schedule keeps its zone and the clock changes it follows
 * ([SimpleCron.movedTo]). Confirming the time shown is no change: nothing is written, the time it
 * was set stays.
 */
fun planTimeEdit(
    task: ScheduledTask,
    hour: Int,
    minute: Int,
    deviceId: String,
    device: CronZone,
    now: Long,
): TimeEdit {
    val simple = task.simple() ?: return TimeEdit.NotEditable
    val tz = task.tz ?: return TimeEdit.NotEditable
    val zone = FrontPageClock.cronZone(tz) ?: return TimeEdit.NotEditable
    val next =
        if (tz == deviceId) {
            SimpleCron(hour, minute, simple.weekdays)
        } else {
            val seen = simple.seenFrom(zone, device, now) ?: return TimeEdit.NotEditable
            if (seen.hour == hour && seen.minute == minute) return TimeEdit.Unchanged
            simple.movedTo(hour, minute, zone, device, now) ?: return TimeEdit.NotExpressible
        }
    if (next == simple) return TimeEdit.Unchanged
    return TimeEdit.Change(task.withCron(next.toCron(), tz, now))
}

/** The time this device's editor shows for [task]: its own fields in its own zone, else one unmoved run on this clock. */
fun editableTime(
    task: ScheduledTask,
    deviceId: String?,
    device: CronZone?,
    now: Long,
): Pair<Int, Int>? {
    if (deviceId == null || device == null) return null
    val simple = task.simple() ?: return null
    val tz = task.tz ?: return null
    if (tz == deviceId) return simple.hour to simple.minute
    val zone = FrontPageClock.cronZone(tz) ?: return null
    return simple.seenFrom(zone, device, now)?.let { it.hour to it.minute }
}

fun viewOf(
    synced: SyncedSchedule,
    optedIn: FrontPageSchedule,
    canPrintHere: Boolean,
    hasWriteRelays: Boolean = true,
    now: Long = TimeUtils.now(),
    deviceId: String? = FrontPageClock.localZone(),
): MorningScheduleView {
    val base = MorningScheduleView(printsHere = optedIn.enabled, canPrintHere = canPrintHere, hasWriteRelays = hasWriteRelays)
    return when (synced) {
        SyncedSchedule.Unknown -> base.copy(state = ScheduleState.NOT_SET_UP)
        is SyncedSchedule.Locked -> base.copy(state = ScheduleState.CANT_DECRYPT, retryable = true)
        is SyncedSchedule.CantDecrypt -> base.copy(state = ScheduleState.CANT_DECRYPT, retryable = synced.retryable)
        is SyncedSchedule.Unreadable -> base.copy(state = ScheduleState.UNREADABLE)
        is SyncedSchedule.Readable -> {
            val task = synced.task
            val zone = FrontPageClock.cronZone(task.tz)
            val expression = task.expression()
            val device = FrontPageClock.cronZone(deviceId)
            // NIP-FB: the claim's name only for the claim `ran` finished; an older `ran` has no name here.
            val lastBy = task.claim?.by?.takeIf { ScheduledTaskClaims.isFinished(task) }
            val shown =
                base.copy(
                    enabled = task.enabled,
                    cron = task.cron,
                    zone = task.tz,
                    lastBy = lastBy,
                    lastRan = task.ran,
                    lastRanAgo = task.ran?.let { agoBucket(now - it) },
                )
            if (task.task != ObserverSchedule.TASK || expression == null || zone == null || task.tz == null) {
                shown.copy(state = ScheduleState.UNREADABLE, words = task.cron?.let { CronWords.Raw(it) })
            } else {
                shown.copy(
                    state = ScheduleState.READY,
                    words = describeCron(expression, task.tz!!, zone, deviceId, device, now),
                    editableTime = editableTime(task, deviceId, device, now),
                    missedSince = task.ran?.takeIf { task.missedRun(zone, now) != null },
                )
            }
        }
    }
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

    private fun hasWriteRelays(relays: Set<NormalizedRelayUrl>) = relays.isNotEmpty()

    /** The schedule this device keeps for [account], as it changes; null while undecided. */
    fun here(account: Account): Flow<FrontPageSchedule?> =
        combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey), account.observerSchedule.writeRelays) { synced, mine, relays ->
            scheduleForThisDevice(synced, mine, canPrintHere(account), hasWriteRelays(relays))
        }

    /**
     * What the screen shows. A clock ticks each minute only while someone collects it, and a
     * new view goes out only when what it says changed ("2 hours ago", a missed morning, the
     * next run's time on this clock).
     */
    fun view(account: Account): Flow<MorningScheduleView> =
        combine(account.observerSchedule.flow, optIns.flow(account.signer.pubKey), account.observerSchedule.writeRelays, minutes()) { synced, mine, relays, at ->
            viewOf(synced, mine, canPrintHere(account), hasWriteRelays(relays), at)
        }.distinctUntilChanged()

    private fun minutes(): Flow<Long> =
        flow {
            while (true) {
                emit(now())
                delay(60_000)
            }
        }

    /** The schedule this device keeps, from what is known now: for the platform's next job. */
    suspend fun scheduleHere(account: Account): FrontPageSchedule = scheduleForThisDevice(account.observerSchedule.flow.value, optIns.get(account.signer.pubKey), canPrintHere(account), hasWriteRelays(account.observerSchedule.writeRelays.value)) ?: FrontPageSchedule()

    /**
     * Whether a launch should try the last due morning: this device prints, and the schedule it
     * holds shows that occurrence unclaimed, claimed long ago and unfinished, or claimed by this
     * install and unfinished (the app died mid-print). The run itself reads the relays again.
     */
    suspend fun catchUpWanted(account: Account): Boolean {
        val task = (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task ?: return false
        if (!optIns.get(account.signer.pubKey).enabled || !canPrintHere(account)) return false
        val zone = FrontPageClock.cronZone(task.tz) ?: return false
        val at = now()
        val occurrence = ScheduledTaskClaims.occurrence(task, zone, at) ?: return false
        return ScheduledTaskClaims.decide(task, ObserverSchedule.TASK, occurrence, at, installId(account)) == Decision.CLAIM
    }

    /** One edit at a time, this device's settings read inside it: two quick taps land in order. */
    private val editQueue = Mutex()

    /**
     * Once per account and install: a device that had its own schedule and finds no synced one
     * (the relays answered and hold none) publishes it, in this device's zone. Skipped when a
     * readable synced schedule is already here, or it was done before. A device that can't print
     * alone loses its old on/off instead: it could never print.
     */
    suspend fun migrate(account: Account) {
        val pubKey = account.signer.pubKey
        val mine = optIns.get(pubKey)
        if (!mine.enabled) return
        if (!canPrintHere(account)) {
            optIns.set(pubKey, mine.copy(enabled = false))
            return
        }
        if (optIns.isMigrated(pubKey)) return
        if (account.observerSchedule.flow.value is SyncedSchedule.Readable) {
            optIns.setMigrated(pubKey)
            return
        }
        val zone = FrontPageClock.localZone() ?: return
        val outcome =
            editQueue.withLock {
                val at = now()
                // Set now: the mornings this device missed before it moved aren't anyone's to catch up.
                account.observerSchedule.edit(at) { current -> if (current == null) ObserverSchedule.new(zone, at).withEnabled(true, at).withCron(mine.cron, zone, at) else null }
            }
        if (outcome is ScheduleEdit.Saved || outcome is ScheduleEdit.Unchanged || outcome is ScheduleEdit.Unreadable) optIns.setMigrated(pubKey)
    }

    /** A new schedule for this account, on, at the old local time when there was one, set [at]; null when this device can't name its zone. */
    private suspend fun seed(
        account: Account,
        at: Long,
    ): ScheduledTask? {
        val zone = FrontPageClock.localZone() ?: return null
        val mine = optIns.get(account.signer.pubKey)
        val fresh = ObserverSchedule.new(zone, at).withEnabled(true, at)
        return if (mine.enabled && mine.simple() != null) fresh.withCron(mine.cron, zone, at) else fresh
    }

    /** On or off for every device; turned on with none yet, one is published now. Switched on, this device takes part when it can. */
    suspend fun setEnabled(
        account: Account,
        on: Boolean,
    ): ScheduleEdit =
        editQueue.withLock {
            val at = now()
            val seed = if (on) seed(account, at) else null
            if (on && seed == null && account.observerSchedule.flow.value !is SyncedSchedule.Readable) return@withLock ScheduleEdit.NoZone
            val outcome = account.observerSchedule.edit(at) { current -> current?.withEnabled(on, at) ?: seed }
            if (on && (outcome is ScheduleEdit.Saved || outcome is ScheduleEdit.Unchanged) && canPrintHere(account)) setOptIn(account, true)
            outcome
        }

    /** The time on this device's clock ([planTimeEdit]); confirming the time shown writes nothing. */
    suspend fun setTime(
        account: Account,
        hour: Int,
        minute: Int,
    ): ScheduleEdit =
        editQueue.withLock {
            val deviceId = FrontPageClock.localZone() ?: return@withLock ScheduleEdit.NoZone
            val device = FrontPageClock.cronZone(deviceId) ?: return@withLock ScheduleEdit.NoZone
            val at = now()
            var refused: ScheduleEdit? = null
            val outcome =
                account.observerSchedule.edit(at) { current ->
                    when (val plan = current?.let { planTimeEdit(it, hour, minute, deviceId, device, at) }) {
                        is TimeEdit.Change -> plan.task
                        TimeEdit.NotExpressible -> null.also { refused = ScheduleEdit.NotExpressible }
                        else -> null
                    }
                }
            refused ?: outcome
        }

    /**
     * "Print from this device": only a device that can print alone takes part. Turned on with no
     * synced schedule, one is published now, on (set now), instead of at the next launch.
     */
    suspend fun setPrintsHere(
        account: Account,
        on: Boolean,
    ): ScheduleEdit? =
        editQueue.withLock {
            if (on && !canPrintHere(account)) return@withLock null
            setOptIn(account, on)
            if (!on || account.observerSchedule.flow.value != SyncedSchedule.Unknown) return@withLock null
            val at = now()
            val seed = seed(account, at) ?: return@withLock ScheduleEdit.NoZone
            account.observerSchedule.edit(at) { current -> if (current == null) seed else null }
        }

    private suspend fun setOptIn(
        account: Account,
        on: Boolean,
    ) {
        val pubKey = account.signer.pubKey
        val mine = optIns.get(pubKey)
        if (mine.enabled != on) optIns.set(pubKey, mine.copy(enabled = on))
    }

    /** Accounts with an occurrence running on this install: a second run (the timer and a catch-up) waits for nothing, it stops. */
    private val running = MutableStateFlow<Set<HexKey>>(emptySet())

    /**
     * One scheduled occurrence of [account]'s morning, by NIP-FB's claim protocol ([claimRun]).
     * [print] gets the seconds it may take (null: no limit) and the check to make before
     * keeping a paper. [deadline] (unix seconds) is when the platform stops this run, if it does.
     */
    suspend fun runOccurrence(
        account: Account,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long? = null,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome {
        val pubKey = account.signer.pubKey
        if (!optIns.get(pubKey).enabled) return ScheduledOutcome.NOT_RUNNER
        while (true) {
            val now = running.value
            if (pubKey in now) return ScheduledOutcome.BUSY
            if (running.compareAndSet(now, now + pubKey)) break
        }
        try {
            return claimRun(account.observerSchedule, installId(account), info?.name, alreadyDone, deadline, print)
        } finally {
            running.update { it - pubKey }
        }
    }

    /**
     * NIP-FB's claim protocol ([ScheduledTaskClaims]) on [state]: a random wait; a read that more
     * than half of the write relays answer; stop unless the occurrence is unclaimed, stale, or
     * this install's own unfinished claim; claim (watching first, more than half must accept),
     * listen [ScheduledTaskClaims.SETTLE_SECONDS] and go on only if this install's claim is the
     * newest (an edit that overwrote it unseen sends this back to the read, a few times at most).
     * Then a paper that already exists for the occurrence ([alreadyDone]) only sets `ran`; else
     * [print] runs under watch: a newer claim by another device stops it before it keeps
     * anything, and its paper is dropped. A print sets `ran`, retried a few times.
     */
    suspend fun claimRun(
        state: ScheduleRelays,
        id: String,
        by: String?,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long? = null,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome {
        pause(RandomInstance.int(ScheduledTaskClaims.MAX_JITTER_SECONDS.toInt() + 1) * 1000L)

        var first: Long? = null
        repeat(ScheduledTaskClaims.MAX_ROUNDS) {
            // Too little time left to claim, print and say so: leave it to a later run, unclaimed.
            if (deadline != null && deadline - now() < CLAIM_SECONDS + MIN_PRINT_SECONDS + RAN_RESERVE_SECONDS) return ScheduledOutcome.FAILED
            val read = state.readQuorum()
            if (!read.quorum) return ScheduledOutcome.NO_QUORUM
            // No schedule, or one this signer can't decrypt: never run, never write.
            val event = read.event ?: return ScheduledOutcome.NOT_RUNNER
            val task = read.task ?: return ScheduledOutcome.NOT_RUNNER
            val zone = FrontPageClock.cronZone(task.tz) ?: return ScheduledOutcome.NOT_RUNNER
            val at = now()
            val occurrence = ScheduledTaskClaims.occurrence(task, zone, at) ?: return ScheduledOutcome.NOT_DUE
            // Back for another round only while it is still the same occurrence.
            if (first != null && first != occurrence) return ScheduledOutcome.NOT_DUE
            first = occurrence
            when (ScheduledTaskClaims.decide(task, ObserverSchedule.TASK, occurrence, at, id)) {
                Decision.NOT_RUNNABLE -> return ScheduledOutcome.NOT_RUNNER
                Decision.ALREADY_CLAIMED -> return ScheduledOutcome.ALREADY_CLAIMED
                Decision.CLAIM -> {}
            }
            claimAndRun(state, read.relays, read.floor ?: event, task, occurrence, zone, id, by, alreadyDone, deadline, print)?.let { return it }
        }
        return ScheduledOutcome.LOST_CLAIM
    }

    private enum class Phase { PRINTING, KEEPING, LOST }

    /** Steps 3 to 6: the claim, the wait, the run under watch. Null when the claim was overwritten unseen: read again. */
    private suspend fun claimAndRun(
        state: ScheduleRelays,
        relays: Set<NormalizedRelayUrl>,
        previous: ScheduledTaskEvent,
        task: ScheduledTask,
        occurrence: Long,
        zone: CronZone,
        id: String,
        by: String?,
        alreadyDone: suspend (occurrence: Long, zone: CronZone) -> Boolean,
        deadline: Long?,
        print: suspend (budgetSeconds: Long?, keep: suspend () -> Boolean) -> ScheduledOutcome,
    ): ScheduledOutcome? =
        coroutineScope {
            val seen = MutableStateFlow<List<ScheduledTaskEvent>>(listOf(previous))
            val subscribed = MutableStateFlow<Set<NormalizedRelayUrl>>(emptySet())
            val watching =
                launch {
                    state.watch(relays) { relay -> subscribed.update { it + relay } }.collect { incoming ->
                        seen.update { (it + incoming).distinctBy { e -> e.id } }
                    }
                }
            try {
                // Watch before claiming, so a competing claim published meanwhile is seen.
                withTimeoutOrNull(SUBSCRIBE_WAIT_MS) { subscribed.first { it.containsAll(relays) } }
                val (mine, accepted) = state.publish(previous, ScheduledTaskClaims.claimed(task, occurrence, id, by, now()), relays)
                seen.update { it + mine }
                if (!accepted) return@coroutineScope ScheduledOutcome.NO_QUORUM
                pause(ScheduledTaskClaims.SETTLE_SECONDS * 1000)

                val newest = ScheduledTaskClaims.winner(seen.value)?.let { state.taskOf(it) } ?: return@coroutineScope ScheduledOutcome.LOST_CLAIM
                when (ScheduledTaskClaims.settle(newest, occurrence, id)) {
                    ScheduledTaskClaims.Settled.LOST -> return@coroutineScope ScheduledOutcome.LOST_CLAIM
                    ScheduledTaskClaims.Settled.AGAIN -> return@coroutineScope null
                    ScheduledTaskClaims.Settled.OURS -> {}
                }

                // The occurrence's paper exists (a manual print, another app): done, without printing.
                // Claimed first, so a skip never takes a morning from a device printing it.
                val done =
                    try {
                        alreadyDone(occurrence, zone)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not tell whether the paper exists", e)
                        false
                    }
                if (done) {
                    markRan(state, occurrence, id)
                    return@coroutineScope ScheduledOutcome.ALREADY_PRINTED
                }

                suspend fun lostTo(events: List<ScheduledTaskEvent>): Boolean {
                    val latest = ScheduledTaskClaims.winner(events)?.let { state.taskOf(it) } ?: return false
                    return ScheduledTaskClaims.claimsForAnother(latest, occurrence, id)
                }
                val phase = MutableStateFlow(Phase.PRINTING)
                val budget = deadline?.let { it - now() - RAN_RESERVE_SECONDS }
                val printing =
                    async {
                        print(budget) {
                            // The last word before the paper is kept: a claim seen by now stops it.
                            yield()
                            if (lostTo(seen.value)) {
                                phase.compareAndSet(Phase.PRINTING, Phase.LOST)
                                false
                            } else {
                                phase.compareAndSet(Phase.PRINTING, Phase.KEEPING)
                            }
                        }
                    }
                val guard =
                    launch {
                        seen.collect { events ->
                            if (lostTo(events) && phase.compareAndSet(Phase.PRINTING, Phase.LOST)) printing.cancel()
                        }
                    }
                val outcome =
                    try {
                        printing.await()
                    } catch (e: CancellationException) {
                        if (phase.value != Phase.LOST) throw e
                        ScheduledOutcome.LOST_CLAIM
                    } finally {
                        guard.cancel()
                    }
                if (phase.value == Phase.LOST) return@coroutineScope ScheduledOutcome.LOST_CLAIM
                if (outcome == ScheduledOutcome.PRINTED) markRan(state, occurrence, id)
                outcome
            } finally {
                watching.cancel()
            }
        }

    /**
     * `ran` for this install's claim on [occurrence]: a fresh read-modify-write that changes only
     * `ran`, tried again with a growing pause when too few relays answer or take it.
     */
    private suspend fun markRan(
        state: ScheduleRelays,
        occurrence: Long,
        id: String,
    ) {
        val done = now()
        for (attempt in 0 until RAN_TRIES) {
            val outcome =
                try {
                    state.edit(done, resend = attempt > 0) { current ->
                        current?.takeIf { ScheduledTaskClaims.isOurs(it, occurrence, id) }?.let { if (it.ran == done) it else ScheduledTaskClaims.finished(it, done) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not mark the morning done", e)
                    ScheduleEdit.Unreachable
                }
            if (outcome != ScheduleEdit.Unreachable && outcome != ScheduleEdit.NotAccepted) return
            if (attempt < RAN_TRIES - 1) pause(RAN_BACKOFF_MS[attempt])
        }
        Log.w(TAG) { "The morning of $occurrence printed, but too few relays took its `ran`" }
    }

    suspend fun forget(account: HexKey) {
        optIns.set(account, FrontPageSchedule())
        optIns.forgetMigrated(account)
        installs.forget(account)
    }

    companion object {
        private const val TAG = "FrontPageMorning"

        /** How long the claim waits for each relay's subscription before publishing anyway. */
        const val SUBSCRIBE_WAIT_MS = 8_000L

        /** `ran` is tried this many times, with these pauses between. */
        const val RAN_TRIES = 4
        val RAN_BACKOFF_MS = longArrayOf(5_000L, 15_000L, 40_000L)

        /** One round of the claim, at its slowest: the read, the watch, the publish and the settle. */
        const val CLAIM_SECONDS = 8L + 8L + 10L + ScheduledTaskClaims.SETTLE_SECONDS

        /** What `ran` takes at its slowest: every try's read and publish, and the pauses. */
        const val RAN_RESERVE_SECONDS = RAN_TRIES * (8L + 10L) + 60L

        /** Less than this left for the print: don't start one. */
        const val MIN_PRINT_SECONDS = 120L

        /**
         * The whole of one scheduled run at its slowest, without the print: the random wait, three
         * claim rounds and `ran`. A platform that stops runs adds the print's time to this.
         */
        const val OVERHEAD_SECONDS = ScheduledTaskClaims.MAX_JITTER_SECONDS + ScheduledTaskClaims.MAX_ROUNDS * CLAIM_SECONDS + RAN_RESERVE_SECONDS
    }
}

/**
 * Keeps each loaded account's platform job in step with the synced schedule and this device's
 * own choice: scheduled while the schedule is on and this device prints, cancelled otherwise,
 * moved whenever either changes (never stopping a print that is running: that is the
 * platform's to keep apart). At account load it also moves an old local schedule to the synced
 * one, once, and runs a morning missed while the app was closed when the schedule shows it open.
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
                if (first && schedule.enabled && morning.catchUpWanted(account)) scheduler.runNow(pubKey)
                first = false
            }
    }

    companion object {
        private const val TAG = "FrontPageScheduleWatcher"
    }
}
