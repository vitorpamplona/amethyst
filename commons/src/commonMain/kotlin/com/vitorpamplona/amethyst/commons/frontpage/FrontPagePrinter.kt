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
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Who asked for a print: the reader, or their morning schedule. */
enum class PrintTrigger { MANUAL, SCHEDULED }

/** What a scheduled run could not get past on its own. */
enum class ScheduledOutcome { PRINTED, FAILED, NO_KEY, NO_LENS, NEEDS_SIGNER, UNSUPPORTED, BUSY }

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
    private val press: (AgentKeyTag, String, PaperBoundary) -> FrontPagePress = { key, model, boundary ->
        FrontPagePress(account.client, Constants.brainstorm, boundary, { messages, onText -> http.chatComplete(key, model, messages, onText) })
    },
) {
    private val pubKey get() = account.signer.pubKey

    private val _run = MutableStateFlow<FrontPageRun>(FrontPageRun.Idle)

    /** The print in progress, or how the last one ended. Every observer sees the same run. */
    val run = _run.asStateFlow()

    private val _editions = MutableStateFlow<List<SavedEdition>>(emptyList())

    /** The kept editions, newest first. */
    val editions = _editions.asStateFlow()

    private val _watched = MutableStateFlow(0)

    /** Whether a front page screen is showing: then a finished print needs no notification. */
    val watched: StateFlow<Int> = _watched.asStateFlow()

    private var job: Job? = null
    private val running = Mutex()

    init {
        scope.launch { refresh() }
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
        _editions.value =
            try {
                store.list(pubKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the kept editions", e)
                emptyList()
            }
    }

    suspend fun html(day: String): String? = store.html(pubKey, day)

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
     * Prints in the caller's coroutine and publishes to [run] as it goes. Returns how it ended;
     * a clean page is already stored and, when nobody is watching, announced.
     */
    suspend fun printNow(
        key: AgentKeyTag,
        model: AgentModel,
        trigger: PrintTrigger = PrintTrigger.MANUAL,
    ): FrontPageRun {
        val boundary = boundary ?: return FrontPageRun.Failed(WriterFailure.UNSUPPORTED).also { _run.value = it }
        if (!running.tryLock()) return _run.value
        try {
            _run.value = FrontPageRun.Running(PrintStep.Probe)
            val result = press(key, model.id, boundary).print(pubKey, readerName(), readerLanguage()) { step -> _run.value = FrontPageRun.Running(step) }
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
            )
        try {
            store.save(pubKey, edition, printed.html)
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not keep the edition", e)
        }
        if (trigger == PrintTrigger.SCHEDULED || _watched.value == 0) notifier.paperReady(pubKey, edition)
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
            _editions.value
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

        val (key, hasLens) = awaitOwnLists(waitMs)
        if (key == null) return ScheduledOutcome.NO_KEY.also { notifier.needsYou(pubKey) }
        if (!hasLens) return ScheduledOutcome.NO_LENS.also { notifier.needsYou(pubKey) }
        val model = chooseModel(key) ?: return ScheduledOutcome.FAILED
        val ended = printNow(key, model, PrintTrigger.SCHEDULED)
        return if (ended is FrontPageRun.Done && ended.result is PrintResult.Printed) ScheduledOutcome.PRINTED else ScheduledOutcome.FAILED
    }

    fun canPrintUnattended() = account.signer.localPrivKeyOrNull() != null

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

/** The morning schedule of one account. Off by default; 7:00 is only the suggestion. */
@Immutable
data class FrontPageSchedule(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
)

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
) {
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
                val printer = made ?: FrontPagePrinter(account, http, memory, store, notifier)
                made = printer
                current + (account.signer.pubKey to printer)
            }
        }
        return made!!
    }

    /** The printer already made for [account], without making one. */
    fun existing(account: HexKey): FrontPagePrinter? = printers.value[account]
}
