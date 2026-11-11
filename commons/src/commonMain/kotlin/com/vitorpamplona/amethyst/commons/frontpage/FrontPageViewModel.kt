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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What stands between the reader and a paper, one thing at a time. */
enum class FrontPageGate {
    /** No AI key in kind 12000: nothing can write the page. */
    NO_KEY,

    /** No rank provider in the reader's kind 10040: the relay would rank through nobody. */
    NO_LENS,

    /** No HTML boundary on this platform yet: an unchecked page never ships. */
    UNSUPPORTED,
    READY,
}

/**
 * Both things a first paper needs, known together, for the setup checklist. [FrontPageGate]
 * names only the first one missing; this tells the reader about the second before they finish
 * the first.
 */
@Immutable
data class FrontPageSetup(
    /** The default AI key's provider, as the reader knows it ("OpenRouter"); null with no key. */
    val provider: String?,
    /** The rank provider's pubkey (kind 10040 `30382:rank`); null with no lens. */
    val lens: HexKey?,
    /** The rank provider's profile name, when this device already has it. */
    val lensName: String? = null,
) {
    val hasKey: Boolean get() = provider != null
    val hasLens: Boolean get() = lens != null

    /** Nothing left to set up: Print takes the checklist's place. */
    val complete: Boolean get() = hasKey && hasLens
}

/** The setup from the reader's default AI [key] and their [rank] provider, the facts [gateOf] reads. */
fun frontPageSetupOf(
    key: AgentKeyTag?,
    rank: ServiceProviderTag?,
    lensName: String? = null,
): FrontPageSetup =
    FrontPageSetup(
        provider = key?.let { AgentProviderPresets.displayName(it.url) },
        lens = rank?.pubkey,
        lensName = lensName?.takeIf { rank != null && it.isNotBlank() },
    )

@Immutable
sealed interface FrontPageRun {
    data object Idle : FrontPageRun

    class Running(
        val step: PrintStep,
    ) : FrontPageRun

    class Done(
        val result: PrintResult,
    ) : FrontPageRun

    class Failed(
        val failure: WriterFailure,
        val status: Int? = null,
        val providerSaid: String = "",
    ) : FrontPageRun
}

@Immutable
class FrontPageModels(
    val loading: Boolean = false,
    val ids: List<String> = emptyList(),
    val selected: String? = null,
    /** Set when the list could not be read: the reader types an id instead. */
    val failure: WriterFailure? = null,
    /** Each listed model with its prices, when the endpoint gives them. */
    val byId: Map<String, AgentModel> = emptyMap(),
) {
    val selectedModel: AgentModel? get() = selected?.let { byId[it] ?: AgentModel(it) }
}

/**
 * A kept edition and its page, as the screen shows it. [html] is null when the page can't be
 * read (gone from this device and not on any server): the screen says so and keeps the history.
 */
@Immutable
class ShownEdition(
    val edition: SavedEdition,
    val html: String?,
)

/**
 * The front page screen's state: the model picker, which kept edition is open, and the morning
 * schedule. The print itself belongs to the account's [FrontPagePrinter], so leaving this screen
 * never cancels it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FrontPageViewModel(
    private val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
    val printer: FrontPagePrinter,
    private val morning: FrontPageMorning,
    initialDay: String? = null,
) : ViewModel() {
    val key: StateFlow<AgentKeyTag?> =
        account.agentKeys.flow
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, account.agentKeys.preferred())

    val gate: StateFlow<FrontPageGate> =
        combine(key, account.trustProviderList.liveUserRankProvider) { key, rank -> gateOf(key, rank != null, printer.boundary) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, FrontPageGate.NO_KEY)

    /**
     * The setup checklist: the key and the lens both, from the same flows as [gate]. The lens is
     * named from the profile this device already holds; no relay is asked for it.
     */
    val setup: StateFlow<FrontPageSetup> =
        combine(key, account.trustProviderList.liveUserRankProvider.flatMapLatest(::withLensName)) { key, (rank, name) ->
            frontPageSetupOf(key, rank, name)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, frontPageSetupOf(key.value, account.trustProviderList.liveUserRankProvider.value))

    private fun withLensName(rank: ServiceProviderTag?): Flow<Pair<ServiceProviderTag?, String?>> {
        val user = rank?.let { account.cache.getUserIfExists(it.pubkey) } ?: return flowOf(rank to null)
        return user.metadata().flow.map { rank to it?.info?.bestName() }
    }

    private val _models = MutableStateFlow(FrontPageModels())
    val models = _models.asStateFlow()

    val run = printer.run
    val editions = printer.editions

    private val _selectedDay = MutableStateFlow(initialDay)

    /** The open edition's day; null opens the newest. */
    val selectedDay = _selectedDay.asStateFlow()

    val shown: StateFlow<ShownEdition?> =
        combine(_selectedDay, printer.editions) { day, kept -> kept.firstOrNull { it.day == day } ?: kept.firstOrNull() }
            .flatMapLatest { edition ->
                if (edition == null) {
                    flowOf<ShownEdition?>(null)
                } else {
                    flow<ShownEdition?> { emit(ShownEdition(edition, printer.html(edition.day))) }
                }
            }.stateIn<ShownEdition?>(viewModelScope, SharingStarted.Eagerly, null)

    /** One print's estimated cost with the picked model; null when the endpoint lists no prices. */
    val estimate: StateFlow<Double?> =
        combine(_models, printer.editions) { models, _ -> printer.estimate(models.selectedModel) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The edit on its way, if any: the switches and the time show it, and every control waits for it. */
    private val _schedulePending = MutableStateFlow<PendingSchedule?>(null)
    val schedulePending = _schedulePending.asStateFlow()

    /** A time that failed to save, kept on screen until the schedule it was picked on changes. */
    private val draft = MutableStateFlow<DraftTime?>(null)

    /**
     * The reader's morning schedule, synced across their devices, as this device sees it. Its
     * clock ticks only while the screen collects it (and five seconds after, across a rotation).
     */
    val schedule: StateFlow<MorningScheduleView> =
        combine(morning.view(account), _schedulePending, draft) { view, pending, draft ->
            var shown = view
            if (draft != null && draft.cron == view.cron) shown = shown.withTime(draft.time)
            if (pending != null) {
                shown = shown.copy(enabled = pending.enabled ?: shown.enabled, printsHere = pending.printsHere ?: shown.printsHere)
                pending.time?.let { shown = shown.withTime(it) }
            }
            shown
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MorningScheduleView())

    /** While this screen exists, a signer that needs the reader may be asked to open the schedule. */
    private val releaseSchedule = account.observerSchedule.opened()

    override fun onCleared() {
        releaseSchedule()
        super.onCleared()
    }

    /** The signer didn't open the schedule: ask it again. */
    fun retryScheduleDecrypt() = account.observerSchedule.retryDecrypt()

    private val _scheduleProblem = MutableStateFlow<ScheduleEdit?>(null)

    /** Why the last schedule change wasn't saved; null when it was, or none was tried. */
    val scheduleProblem = _scheduleProblem.asStateFlow()

    /** Publishing to the reader's public Observer site: on by default, after a one-time notice. */
    val observerPrefs: StateFlow<ObserverPrefs> =
        printer.observerPrefs().stateIn(viewModelScope, SharingStarted.Eagerly, ObserverPrefs())

    /** How the last publish went. */
    val observer: StateFlow<ObserverStatus> = printer.observer

    /** The reader's Observer site, once read or published: for Open and Share. */
    val site: StateFlow<NamedSiteEvent?> = printer.site

    /** Whether this platform publishes to an Observer site at all. */
    val canPublish: Boolean get() = printer.canPublish

    fun setPublish(on: Boolean) {
        viewModelScope.launch { printer.setObserverPrefs(observerPrefs.value.copy(publish = on)) }
    }

    /**
     * The reader answered the confirm that comes before the first publish. Publish: the site is
     * public from now on, and what's kept goes out now. Not now: nothing is uploaded, and the
     * next print asks again.
     */
    fun answerObserverNotice(publish: Boolean) {
        if (!publish) {
            printer.declinePublish()
            return
        }
        viewModelScope.launch {
            printer.setObserverPrefs(ObserverPrefs(publish = true, noticeSeen = true))
            printer.publishPending()
        }
    }

    /** Tries the publish again, after a failure the reader may have fixed (a Blossom server added). */
    fun retryPublish() {
        viewModelScope.launch { printer.publishPending() }
    }

    /** Whether a scheduled edition can print with nobody there: a key on this device can, Amber cannot. */
    val printsUnattended: Boolean get() = printer.canPrintUnattended()

    private var modelsFor: AgentKeyTag? = null
    private var modelsJob: Job? = null

    init {
        viewModelScope.launch { key.collect { it?.let { loadModels(it) } } }
    }

    /**
     * The models of [key]'s endpoint. Keyed on the whole key, not its URL: a new key at the same
     * provider may see other models. A load for a key that is no longer the reader's is
     * cancelled, so its list never lands on the next key.
     */
    private fun loadModels(key: AgentKeyTag) {
        if (modelsFor == key) return
        modelsFor = key
        modelsJob?.cancel()
        _models.value = FrontPageModels(loading = true)
        modelsJob =
            viewModelScope.launch {
                val remembered = runCatching { printer.memory.get(key.url) }.getOrNull()
                try {
                    val listed = http.listAgentModels(key)
                    val ranked = rankModels(listed.map { it.id })
                    val selected = remembered?.takeIf { it in ranked } ?: ranked.firstOrNull() ?: remembered
                    _models.value = FrontPageModels(ids = ranked, selected = selected, byId = listed.associateBy { it.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WriterException) {
                    _models.value = FrontPageModels(selected = remembered, failure = e.failure)
                } catch (e: Exception) {
                    // Never left loading: the reader can still type a model id.
                    _models.value = FrontPageModels(selected = remembered, failure = WriterFailure.UNREACHABLE)
                }
            }
    }

    fun selectModel(model: String) {
        _models.update { FrontPageModels(it.loading, it.ids, model.trim().ifEmpty { null }, it.failure, it.byId) }
        val url = key.value?.url ?: return
        if (model.isNotBlank()) viewModelScope.launch { runCatching { printer.memory.set(url, model.trim()) } }
    }

    fun selectDay(day: String?) {
        _selectedDay.value = day
    }

    fun print() {
        val key = key.value ?: return
        val model = _models.value.selectedModel ?: return
        // The new edition is today's, which is the newest: open it when it lands.
        if (printer.print(key, model)) _selectedDay.value = null
    }

    fun cancel() = printer.cancel()

    /** On or off for every device; switched on, this device prints too when it can. */
    fun setScheduleEnabled(on: Boolean) = editSchedule(PendingSchedule(enabled = on, printsHere = if (on && schedule.value.canPrintHere) true else null)) { morning.setEnabled(account, on) }

    /** A new time on this device's clock, the weekdays as they are ([FrontPageMorning.setTime]). */
    fun setScheduleTime(
        hour: Int,
        minute: Int,
    ) {
        val pickedOn = schedule.value.cron
        editSchedule(PendingSchedule(time = hour to minute), onFailure = { draft.value = DraftTime(hour to minute, pickedOn) }) { morning.setTime(account, hour, minute) }
    }

    /** "Print from this device": kept on this device (with no synced schedule yet, one is published). */
    fun setPrintsHere(on: Boolean) = editSchedule(PendingSchedule(printsHere = on)) { morning.setPrintsHere(account, on) }

    /**
     * One edit at a time: a tap while one is saving is dropped (the controls say they are busy).
     * Saved on the account's scope: leaving the screen mid-write must not lose the change.
     */
    private fun editSchedule(
        change: PendingSchedule,
        onFailure: () -> Unit = {},
        edit: suspend () -> ScheduleEdit?,
    ) {
        if (!_schedulePending.compareAndSet(null, change)) return
        _scheduleProblem.value = null
        account.scope.launch {
            try {
                val outcome =
                    try {
                        edit()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("FrontPageViewModel", "Could not save the morning schedule", e)
                        ScheduleEdit.Unreachable
                    }
                val failed = outcome != null && outcome !is ScheduleEdit.Saved && outcome != ScheduleEdit.Unchanged
                // A failed time stays on screen, with why, so the reader can try again.
                if (failed) onFailure() else draft.value = null
                _scheduleProblem.value = outcome?.takeIf { failed }
            } finally {
                _schedulePending.value = null
            }
        }
    }
}

/** What the schedule controls show while an edit is on its way. */
@Immutable
data class PendingSchedule(
    val enabled: Boolean? = null,
    val printsHere: Boolean? = null,
    val time: Pair<Int, Int>? = null,
)

/** A [time] that failed to save, picked on the schedule [cron]. */
private data class DraftTime(
    val time: Pair<Int, Int>,
    val cron: String?,
)

/** The view with [time] picked on this device's clock: the editor's time and the sentence both say it. */
private fun MorningScheduleView.withTime(time: Pair<Int, Int>): MorningScheduleView {
    val words = (words as? CronWords.Days)?.let { it.copy(time = it.time.copy(hour = time.first, minute = time.second)) } ?: words
    return copy(editableTime = time, words = words)
}
