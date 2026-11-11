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
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.localPrivKeyOrNull
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverSite
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.CronExpression
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Who asked for a print: the reader, or their morning schedule. */
enum class PrintTrigger { MANUAL, SCHEDULED }

/** What a scheduled run could not get past on its own. */
enum class ScheduledOutcome {
    PRINTED,
    FAILED,
    NO_KEY,
    NO_LENS,
    NEEDS_SIGNER,
    UNSUPPORTED,
    BUSY,

    /** Today's edition already exists (a manual print, another device, Brainstorm): done for today. */
    ALREADY_PRINTED,

    /** This device doesn't print the mornings, the schedule is off, or it can't be run here. */
    NOT_RUNNER,

    /** No occurrence has come yet. */
    NOT_DUE,

    /** Another device (or this one, earlier) claimed this occurrence. */
    ALREADY_CLAIMED,

    /** Another device's claim won, before or during the print. */
    LOST_CLAIM,

    /** Not enough of the reader's relays answered to claim safely. */
    NO_QUORUM,
}

/** What a platform shows outside the app when a paper is ready, or needs the reader. */
interface FrontPageNotifier {
    suspend fun paperReady(
        account: HexKey,
        edition: SavedEdition,
    )

    /** A scheduled edition could not print on its own: the reader's signer, key or lens needs them. */
    suspend fun needsYou(account: HexKey)

    object None : FrontPageNotifier {
        override suspend fun paperReady(
            account: HexKey,
            edition: SavedEdition,
        ) {}

        override suspend fun needsYou(account: HexKey) {}
    }
}

/**
 * Why a scheduled print can't start, or null when it can, decided before any relay is asked.
 * Only a key held on this device decrypts the AI key with nobody there; the account's signer is
 * always wrapped (client tag, metering), so it is unwrapped before the check.
 */
fun unattendedBlocker(
    signer: NostrSigner,
    boundary: PaperBoundary?,
    busy: Boolean,
): ScheduledOutcome? =
    when {
        boundary == null -> ScheduledOutcome.UNSUPPORTED
        signer.localPrivKeyOrNull() == null -> ScheduledOutcome.NEEDS_SIGNER
        busy -> ScheduledOutcome.BUSY
        else -> null
    }

/** What stands between the reader and a paper, from the key, the lens and the platform. */
fun gateOf(
    key: AgentKeyTag?,
    hasLens: Boolean,
    boundary: PaperBoundary?,
): FrontPageGate =
    when {
        boundary == null -> FrontPageGate.UNSUPPORTED
        key == null -> FrontPageGate.NO_KEY
        !hasLens -> FrontPageGate.NO_LENS
        else -> FrontPageGate.READY
    }

/**
 * One account's front page, owned by the account rather than a screen: a print runs on the
 * account's scope, so leaving the screen (or the app, on desktop) never cancels a job that takes
 * minutes and costs the reader money. Screens, the shell's banner and the scheduler all observe
 * the same [run]. Clean pages are kept in [FrontPageEditionStore].
 */
class FrontPagePrinter(
    val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
    val memory: FrontPageModelMemory,
    private val store: FrontPageEditionStore,
    private val notifier: FrontPageNotifier,
    val boundary: PaperBoundary? = paperBoundary(),
    private val scope: CoroutineScope = account.scope,
    private val now: () -> Long = { TimeUtils.now() },
    private val observerPrefs: ObserverPrefsStore = ObserverPrefsStore.None,
    /** The synced morning schedule; null runs every scheduled call it gets (tests). */
    val morning: FrontPageMorning? = null,
    observerIO: ObserverSiteIO? = observerSiteIO(account, http),
    private val press: (AgentKeyTag, String, PaperBoundary) -> FrontPagePress = { key, model, boundary ->
        FrontPagePress(account.client, Constants.brainstorm, boundary, { messages, onText -> http.chatComplete(key, model, messages, onText) })
    },
) {
    private val pubKey get() = account.signer.pubKey

    private val _run = MutableStateFlow<FrontPageRun>(FrontPageRun.Idle)

    /** The print in progress, or how the last one ended. Every observer sees the same run. */
    val run = _run.asStateFlow()

    private val localEditions = MutableStateFlow<List<SavedEdition>>(emptyList())

    /** The reader's own Observer site, as last read or published; null until read, or when there is none. */
    private val siteView = MutableStateFlow<ObserverSiteView?>(null)

    /**
     * The history: editions kept on this device plus the ones on the reader's Observer site this
     * device doesn't have (printed in another app or on another device), newest first.
     */
    val editions: StateFlow<List<SavedEdition>> =
        combine(localEditions, siteView) { local, site -> mergeHistory(local, site?.editions.orEmpty()) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The reader's Observer site event, for Open and Share. */
    val site: StateFlow<NamedSiteEvent?> =
        siteView
            .map { it?.site }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val publisher = observerIO?.let { ObserverPublisher(it, account.signer.pubKey, ::readerName, now) }

    /** Whether this platform can publish to an Observer site at all. */
    val canPublish: Boolean get() = publisher != null

    private val _observer = MutableStateFlow<ObserverStatus>(ObserverStatus.Idle)

    /** The last publish to the Observer site. */
    val observer = _observer.asStateFlow()

    fun observerPrefs(): Flow<ObserverPrefs> = observerPrefs.flow(pubKey)

    suspend fun setObserverPrefs(prefs: ObserverPrefs) = observerPrefs.set(pubKey, prefs)

    private val publishing = Mutex()

    private val _watched = MutableStateFlow(0)

    /** Whether a front page screen is showing: then a finished print needs no notification. */
    val watched: StateFlow<Int> = _watched.asStateFlow()

    private var job: Job? = null
    private val running = Mutex()

    init {
        scope.launch {
            refresh()
            refreshSite()
        }
    }

    /** A screen showing the paper calls this, and the returned function when it goes. */
    fun watch(): () -> Unit {
        _watched.update { it + 1 }
        var released = false
        return {
            if (!released) {
                released = true
                _watched.update { (it - 1).coerceAtLeast(0) }
            }
        }
    }

    suspend fun refresh() {
        localEditions.value =
            try {
                // Editions kept before the Observer site existed carry no hash; it is what tells a
                // site edition apart from one this device already has.
                store.list(pubKey).map { kept -> if (kept.sha256 != null) kept else store.html(pubKey, kept.day)?.let { kept.copy(sha256 = publishedSha256(it)) } ?: kept }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the kept editions", e)
                emptyList()
            }
    }

    /** Reads the reader's Observer site, so its editions join the history. */
    suspend fun refreshSite() {
        val publisher = publisher ?: return
        try {
            publisher.read()?.let { siteView.value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the Observer site", e)
        }
    }

    /**
     * [day]'s page: from this device, else from the reader's Observer site by its hash (verified),
     * then kept here so it opens offline next time.
     */
    suspend fun html(day: String): String? {
        store.html(pubKey, day)?.let { return it }
        val view = siteView.value ?: return null
        val edition = view.editions.firstOrNull { it.day == day } ?: return null
        val page = publisher?.page(edition, view.servers) ?: return null
        mergeHistory(emptyList(), listOf(edition)).firstOrNull()?.let { kept ->
            try {
                store.save(pubKey, kept, page)
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not keep the site's edition of $day", e)
            }
        }
        return page
    }

    /** Starts a print on the account's scope; false when one is already running. */
    fun print(
        key: AgentKeyTag,
        model: AgentModel,
        trigger: PrintTrigger = PrintTrigger.MANUAL,
    ): Boolean {
        if (job?.isActive == true) return false
        job = scope.launch { printNow(key, model, trigger) }
        return true
    }

    fun cancel() {
        job?.cancel()
        job = null
        _run.value = FrontPageRun.Idle
    }

    /**
     * Prints in the caller's coroutine and publishes to [run] as it goes. Returns how it ended,
     * or null when another print holds the press (nothing was started); a clean page is already
     * stored and, when nobody is watching, announced.
     */
    suspend fun printNow(
        key: AgentKeyTag,
        model: AgentModel,
        trigger: PrintTrigger = PrintTrigger.MANUAL,
    ): FrontPageRun? {
        val boundary = boundary ?: return FrontPageRun.Failed(WriterFailure.UNSUPPORTED).also { _run.value = it }
        if (!running.tryLock()) return null
        try {
            _run.value = FrontPageRun.Running(PrintStep.Probe)
            var shown: PrintStep = PrintStep.Probe
            val result =
                press(key, model.id, boundary).print(pubKey, readerName(), readerLanguage()) { step ->
                    // The stream reports every chunk; every observer recomposing per chunk is the cost.
                    if (progressWorthShowing(shown, step)) {
                        shown = step
                        _run.value = FrontPageRun.Running(step)
                    }
                }
            if (result is PrintResult.Printed) keep(result, model, trigger)
            return FrontPageRun.Done(result).also { _run.value = it }
        } catch (e: CancellationException) {
            _run.value = FrontPageRun.Idle
            throw e
        } catch (e: WriterException) {
            return FrontPageRun.Failed(e.failure, e.status, e.providerSaid).also { _run.value = it }
        } catch (e: Exception) {
            Log.w(TAG, "Printing failed", e)
            return FrontPageRun.Failed(WriterFailure.UNREACHABLE).also { _run.value = it }
        } finally {
            running.unlock()
        }
    }

    private suspend fun keep(
        printed: PrintResult.Printed,
        model: AgentModel,
        trigger: PrintTrigger,
    ) {
        val at = now()
        // The page as it leaves the app (the Download, the Observer site): with the paper's CSP.
        val html = withPaperCsp(printed.html)
        val edition =
            SavedEdition(
                day = FrontPageClock.dayKey(at),
                code = printed.corpus.code,
                printedAt = at,
                model = model.id,
                tokens = printed.usage?.total,
                costUsd = actualCost(model, printed.usage),
                quotes = printed.quotes,
                posts = printed.corpus.events.size,
                headline = printed.headline,
                removed = printed.changes.filter { it.noteworthy }.map { it.detail },
                digestChars = printed.digestChars,
                rounds = printed.rounds,
                sha256 = publishedSha256(html),
            )
        try {
            store.save(pubKey, edition, html)
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the edition", e)
        }
        if (trigger == PrintTrigger.SCHEDULED || _watched.value == 0) notifier.paperReady(pubKey, edition)
        // A scheduled run publishes before it returns: the platform may end its job right after.
        if (trigger == PrintTrigger.SCHEDULED) publishPending(trigger) else scope.launch { publishPending(trigger) }
    }

    /**
     * Puts every kept edition the site doesn't have yet on the reader's Observer site: today's,
     * and any an earlier publish failed to send. Only when publishing is on and the reader has
     * said yes, once, to a public site; until then a manual print asks ([ObserverStatus.AwaitingConsent])
     * and nothing is uploaded. A scheduled print publishes only with a signer that signs on its
     * own. Never anything that wasn't printed (and checked) here.
     */
    suspend fun publishPending(trigger: PrintTrigger = PrintTrigger.MANUAL) {
        val publisher = publisher ?: return
        // Read now, not when the print started: turning it off mid-print keeps that paper private.
        val prefs = observerPrefs.get(pubKey)
        if (!prefs.publish) return
        if (!prefs.noticeSeen) {
            if (trigger == PrintTrigger.MANUAL) _observer.value = ObserverStatus.AwaitingConsent
            return
        }
        if (trigger == PrintTrigger.SCHEDULED && !canPrintUnattended()) return
        // One at a time: a second request waits, then publishes whatever the first left pending.
        publishing.lock()
        try {
            val pending =
                localEditions.value
                    .filter { !it.published && it.sha256 != null }
                    .mapNotNull { kept -> store.html(pubKey, kept.day)?.let { kept to withPaperCsp(it) } }
                    .filter { (kept, html) -> ObserverPublisher.sha256Hex(html.encodeToByteArray()).equals(kept.sha256, ignoreCase = true) }
            if (pending.isEmpty()) return
            _observer.value = ObserverStatus.Publishing
            val result = publisher.publish(pending.map { (kept, html) -> ObserverPage(ObserverPublisher.entryFor(kept, kept.sha256!!.lowercase()), html) })
            _observer.value =
                when (result) {
                    is ObserverPublishResult.Published -> {
                        pending.forEach { (kept, html) -> store.save(pubKey, kept.copy(published = true), html) }
                        siteView.value = ObserverSiteView(result.site, result.kept, result.site.servers())
                        refresh()
                        ObserverStatus.Published(now())
                    }
                    is ObserverPublishResult.Failed -> ObserverStatus.Failed(result.reason)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Publishing to the Observer site failed", e)
            _observer.value = ObserverStatus.Failed(ObserverFailure.PUBLISH)
        } finally {
            publishing.unlock()
        }
    }

    /** The reader said "Not now" to the public site: nothing goes out, and the next print asks again. */
    fun declinePublish() {
        if (_observer.value == ObserverStatus.AwaitingConsent) _observer.value = ObserverStatus.Idle
    }

    /** The model the reader picked for this endpoint, else the best-ranked one it lists. */
    suspend fun chooseModel(key: AgentKeyTag): AgentModel? {
        val listed =
            try {
                http.listAgentModels(key)
            } catch (e: WriterException) {
                emptyList()
            }
        val remembered = runCatching { memory.get(key.url) }.getOrNull()
        val byId = listed.associateBy { it.id }
        val pick = remembered ?: rankModels(listed.map { it.id }).firstOrNull() ?: return null
        return byId[pick] ?: AgentModel(pick)
    }

    /** The cost estimate for one print with [model]: the last edition's digest size, or the budget. */
    fun estimate(model: AgentModel?): Double? =
        estimateCost(
            model,
            localEditions.value
                .firstOrNull()
                ?.digestChars
                ?.takeIf { it > 0 } ?: DEFAULT_DIGEST_BUDGET,
        )

    /**
     * The morning edition, with nobody there: only a signer that can decrypt on its own (a key
     * on this device) can read the AI key. Amber, other external signers and remote bunkers need
     * the reader, and nothing here stores the AI key anywhere else to work around that.
     */
    suspend fun printScheduled(waitMs: Long = 60_000): ScheduledOutcome {
        unattendedBlocker(account.signer, boundary, busy = job?.isActive == true || running.isLocked)?.let { blocked ->
            if (blocked == ScheduledOutcome.NEEDS_SIGNER) notifier.needsYou(pubKey)
            return blocked
        }

        val morning = morning ?: return printOccurrence(waitMs)
        // NIP-FB: only a device the reader turned on, and only the one whose claim wins; a paper
        // that already exists for today is claimed as done without printing.
        return morning.runOccurrence(account, alreadyDone = { alreadyPrintedToday() }) { printOccurrence(waitMs) }
    }

    /** The print itself, once the occurrence is this device's. */
    private suspend fun printOccurrence(waitMs: Long): ScheduledOutcome {
        val (key, hasLens) = awaitOwnLists(waitMs)
        if (key == null) return ScheduledOutcome.NO_KEY.also { notifier.needsYou(pubKey) }
        if (!hasLens) return ScheduledOutcome.NO_LENS.also { notifier.needsYou(pubKey) }
        val model = chooseModel(key) ?: return ScheduledOutcome.FAILED
        val ended = printNow(key, model, PrintTrigger.SCHEDULED) ?: return ScheduledOutcome.BUSY
        return if (ended is FrontPageRun.Done && ended.result is PrintResult.Printed) ScheduledOutcome.PRINTED else ScheduledOutcome.FAILED
    }

    fun canPrintUnattended() = account.signer.localPrivKeyOrNull() != null

    /**
     * Whether today's edition already exists, so a scheduled print skips: one kept here or listed
     * on the reader's Observer site, printed in the last 20 hours, or a site entry with no print
     * time dated today in UTC ([ObserverSite.alreadyPrintedToday]). A site that can't be read
     * leaves the answer to this device's own editions. Manual prints never ask.
     */
    suspend fun alreadyPrintedToday(): Boolean {
        val at = now()
        val kept =
            try {
                store.list(pubKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the kept editions", e)
                emptyList()
            }
        if (kept.any { ObserverSite.printedToday(it.printedAt, null, at) }) return true
        val site =
            try {
                publisher?.read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the Observer site before a scheduled print", e)
                null
            } ?: return false
        siteView.value = site
        return ObserverSite.alreadyPrintedToday(site.editions, at)
    }

    /**
     * The reader's AI key (kind 12000) and rank provider (kind 10040). A process started cold by
     * the scheduler has neither in its cache, so it asks the reader's own outbox relays (and the
     * search relay, which mirrors replaceables) for both, then waits for them to decrypt.
     */
    private suspend fun awaitOwnLists(waitMs: Long): Pair<AgentKeyTag?, Boolean> {
        val ready = combine(account.agentKeys.flow, account.trustProviderList.liveUserRankProvider) { keys, rank -> keys.firstOrNull() to (rank != null) }
        val now = ready.first()
        if (now.first != null && now.second) return now
        fetchOwnLists()
        return withTimeoutOrNull(waitMs) { ready.first { it.first != null && it.second } } ?: ready.first()
    }

    private suspend fun fetchOwnLists() =
        coroutineScope {
            val kinds = listOf(AgentKeyEvent.KIND, TrustProviderListEvent.KIND)
            val authors = listOf(pubKey)
            val relays = (account.outboxRelays.flow.first() + Constants.brainstorm).distinct()
            relays
                .map { relay ->
                    async {
                        // The search relay closes tokenless queries; everyone else ignores the token.
                        val asked = Filter(kinds = kinds, authors = authors, search = if (relay == Constants.brainstorm) INCLUDE_SPAM else null)
                        try {
                            account.client.fetchAll(relay, asked, 10_000).forEach { account.cache.justConsume(it, relay, wasVerified = false) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not read the reader's lists from ${relay.url}", e)
                        }
                    }
                }.awaitAll()
        }

    /** The reader's name for the masthead line; never hex. */
    private fun readerName(): String =
        account
            .userProfile()
            .metadataOrNull()
            ?.bestName()
            ?.trim()
            ?.ifEmpty { null } ?: shortNpub(pubKey)

    /** The language the reader reads Amethyst's translations in. */
    private fun readerLanguage(): String? =
        account.settings.syncedSettings.languages.translateTo.value
            .trim()
            .ifEmpty { null }

    companion object {
        private const val TAG = "FrontPagePrinter"
    }
}

/** How the last publish to the reader's Observer site went. */
sealed interface ObserverStatus {
    data object Idle : ObserverStatus

    /** A clean print would publish, but the reader hasn't said yes to a public site yet. */
    data object AwaitingConsent : ObserverStatus

    data object Publishing : ObserverStatus

    data class Published(
        val at: Long,
    ) : ObserverStatus

    data class Failed(
        val reason: ObserverFailure,
    ) : ObserverStatus
}

/** The hash an edition is published under: its bytes once the paper's CSP is in ([withPaperCsp]). */
fun publishedSha256(html: String): String = ObserverPublisher.sha256Hex(withPaperCsp(html).encodeToByteArray())

/**
 * Whether [next] is worth a new [FrontPageRun.Running]: every step change is, but the writer's
 * running character count only every [WRITE_PROGRESS_CHARS], since the stream reports each chunk.
 */
fun progressWorthShowing(
    shown: PrintStep,
    next: PrintStep,
): Boolean {
    if (next !is PrintStep.Write || shown !is PrintStep.Write || next.round != shown.round) return true
    return next.chars == 0 || next.chars - shown.chars >= WRITE_PROGRESS_CHARS
}

const val WRITE_PROGRESS_CHARS = 400

/**
 * When this device prints one account's morning edition: [cron] (NIP-FB's five fields) on the
 * wall clock of [zone] (this device's when null). Off by default; 7:00 every day is only the
 * suggestion. [enabled] already says whether *this device* prints: see [scheduleForThisDevice].
 */
@Immutable
data class FrontPageSchedule(
    val enabled: Boolean = false,
    val cron: String = ScheduledTask.DEFAULT_CRON,
    val zone: String? = null,
) {
    /** The schedule, or null when [cron] is outside NIP-FB's subset: then it never runs here. */
    fun expression(): CronExpression? = CronExpression.parse(cron)

    /** The time and weekdays a simple editor shows, or null when it can only show [cron] as written. */
    fun simple(): SimpleCron? = SimpleCron.parse(cron)

    /** The next run strictly after [now]; null when there is none. */
    fun nextRun(now: Long): Long? = expression()?.nextAfter(now, FrontPageClock.cronZone(zone))

    /** Whether [cron] is read on this device's own clock. */
    fun isLocal(): Boolean = zone == null || zone == FrontPageClock.localZone()

    /**
     * The time a simple schedule shows on this device's clock: its own fields when it is read in
     * this device's zone (never a time a clock change moved), else the next run's local time.
     */
    fun localTime(now: Long): Pair<Int, Int>? {
        val simple = simple()
        if (simple != null && isLocal()) return simple.hour to simple.minute
        return nextRun(now)?.let(FrontPageClock::localHourMinute)
    }

    /**
     * A run to show: the next one, or, read in this device's zone, the next one that isn't on a
     * clock-change day, so the time shown is the time the reader set.
     */
    fun displayRun(now: Long): Long? {
        val first = nextRun(now) ?: return null
        val simple = simple()?.takeIf { isLocal() } ?: return first
        var at: Long = first
        repeat(8) {
            if (FrontPageClock.localHourMinute(at) == (simple.hour to simple.minute)) return at
            at = nextRun(at) ?: return first
        }
        return first
    }

    /** The last occurrence at or before [now]: what a run woken now is for. Null when none. */
    fun lastRun(now: Long): Long? = expression()?.lastAtOrBefore(now, FrontPageClock.cronZone(zone))

    companion object {
        /** Every day at [hour]:[minute] on this device's clock: the old per-device schedule. */
        fun daily(
            enabled: Boolean,
            hour: Int,
            minute: Int,
        ) = FrontPageSchedule(enabled, SimpleCron(hour, minute).toCron())
    }
}

/**
 * One [FrontPagePrinter] per account for the whole app, so the screen, the shell's banner and
 * the scheduler share one print. A reloaded account (same key, new object) gets a new printer.
 */
class FrontPagePrinters(
    private val http: IRoleBasedHttpClientBuilder,
    val memory: FrontPageModelMemory,
    val store: FrontPageEditionStore,
    val schedules: FrontPageScheduleStore,
    private val notifier: FrontPageNotifier,
    val observerPrefs: ObserverPrefsStore = ObserverPrefsStore.None,
    installs: FrontPageInstallStore,
    /** How this install names itself as a runner; null where it can't print (iOS). */
    installInfo: FrontPageInstallInfo?,
) {
    /** The synced morning schedule of every account, and this install's place in it. */
    val morning = FrontPageMorning(schedules, installs, installInfo)

    private val printers = MutableStateFlow<Map<HexKey, FrontPagePrinter>>(emptyMap())

    fun of(account: Account): FrontPagePrinter {
        printers.value[account.signer.pubKey]?.takeIf { it.account === account }?.let { return it }
        var made: FrontPagePrinter? = null
        printers.update { current ->
            val existing = current[account.signer.pubKey]?.takeIf { it.account === account }
            if (existing != null) {
                made = existing
                current
            } else {
                val printer = made ?: FrontPagePrinter(account, http, memory, store, notifier, observerPrefs = observerPrefs, morning = morning)
                made = printer
                current + (account.signer.pubKey to printer)
            }
        }
        return made!!
    }

    /** The printer already made for [account], without making one. */
    fun existing(account: HexKey): FrontPagePrinter? = printers.value[account]

    /**
     * A removed account leaves nothing behind on this install: its print stops, and its kept
     * papers, local schedule, runner id and Observer choice go. The synced schedule stays: it is
     * the reader's, on their relays. The platform cancels its own scheduled job ([FrontPageScheduler]).
     */
    suspend fun forget(account: HexKey) {
        printers.value[account]?.cancel()
        printers.update { it - account }
        store.removeAccount(account)
        morning.forget(account)
        observerPrefs.set(account, ObserverPrefs())
    }
}
