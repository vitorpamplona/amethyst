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
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.ScheduleEdit
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.SimpleCron
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

    /** A time the reader picked that isn't saved yet (or failed to save): shown until it is. */
    private val draftCron = MutableStateFlow<String?>(null)

    /** The reader's morning schedule, synced across their devices, as this device sees it. */
    val schedule: StateFlow<MorningScheduleView> =
        combine(morning.view(account), draftCron) { view, draft ->
            if (draft == null || draft == view.cron) view else view.copy(cron = draft, zone = FrontPageClock.localZone(), simple = SimpleCron.parse(draft))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MorningScheduleView())

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
    fun setScheduleEnabled(on: Boolean) = editSchedule { morning.setEnabled(account, on) }

    /** A new time on this device's clock, keeping the weekdays: written with this device's zone. */
    fun setScheduleTime(
        hour: Int,
        minute: Int,
    ) {
        val weekdays = schedule.value.simple?.weekdays
        val cron = SimpleCron(hour, minute, weekdays).toCron()
        draftCron.value = cron
        editSchedule(onSaved = { if (draftCron.value == cron) draftCron.value = null }) { morning.setCron(account, cron) }
    }

    /** "Print from this device": kept on this device only. */
    fun setPrintsHere(on: Boolean) {
        account.scope.launch { morning.setPrintsHere(account, on) }
    }

    /** Saved on the account's scope: leaving the screen mid-write must not lose the change. */
    private fun editSchedule(
        onSaved: () -> Unit = {},
        edit: suspend () -> ScheduleEdit?,
    ) {
        _scheduleProblem.value = null
        account.scope.launch {
            val outcome =
                try {
                    edit()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScheduleEdit.Unreachable
                }
            val failed = outcome is ScheduleEdit.Unreachable || outcome is ScheduleEdit.Unreadable || outcome is ScheduleEdit.CannotSign || outcome is ScheduleEdit.NotAccepted
            // A failed save keeps what the reader picked on screen, with why, so they can try again.
            if (!failed) onSaved()
            _scheduleProblem.value = outcome?.takeIf { failed }
        }
    }
}
