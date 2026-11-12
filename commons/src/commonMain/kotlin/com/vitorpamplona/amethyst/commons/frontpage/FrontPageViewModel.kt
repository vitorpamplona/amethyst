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
import com.vitorpamplona.amethyst.commons.model.nipFBScheduledTasks.SyncedSchedule
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.observer.ObserverParams
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.nipFBScheduledTasks.ScheduledTask
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** What [FrontPagePrinter.printNow] returns for a print the reader cancelled; [FrontPagePrinter.run] shows [Idle]. */
    data object Cancelled : FrontPageRun

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
data class ShownEdition(
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
    /** The reader's schedule, when this device has read it: the Observer's settings live there. */
    private val task: StateFlow<ScheduledTask?> =
        account.observerSchedule.flow
            .map { (it as? SyncedSchedule.Readable)?.task }
            .stateIn(viewModelScope, SharingStarted.Eagerly, (account.observerSchedule.flow.value as? SyncedSchedule.Readable)?.task)

    /** The key to print with: the schedule's `key` param, else NIP-FA's default (the first usable key). */
    val key: StateFlow<AgentKeyTag?> =
        combine(account.agentKeys.flow, task) { keys, task -> scheduledKey(task, keys) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, scheduledKey(task.value, account.agentKeys.flow.value))

    /** The model the schedule names (its `model` param), shared by every app; null leaves the app's default. */
    private val namedModel: StateFlow<String?> =
        task
            .map { it?.let(ObserverParams::model) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, task.value?.let(ObserverParams::model))

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

    /**
     * The lens's name as its profile arrives: a provider this device has never seen gets a cache
     * entry now, so the name shows once the screen's subscription loads it ([lensPubKey]).
     */
    private fun withLensName(rank: ServiceProviderTag?): Flow<Pair<ServiceProviderTag?, String?>> {
        val user = rank?.let { account.cache.checkGetOrCreateUser(it.pubkey) } ?: return flowOf(rank to null)
        return user.metadata().flow.map { rank to it?.info?.bestName() }
    }

    private val _models = MutableStateFlow(FrontPageModels())
    val models = _models.asStateFlow()

    val run = printer.run
    val editions = printer.editions

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
        combine(morning.view(account, printer.sites), _schedulePending, draft) { view, pending, draft ->
            var shown = view
            if (draft != null && draft.cron == view.cron) shown = shown.withTime(draft.time)
            if (pending != null) {
                shown = shown.copy(enabled = pending.enabled ?: shown.enabled, printsHere = pending.printsHere ?: shown.printsHere)
                pending.time?.let { shown = shown.withTime(it) }
                pending.runner?.let { shown = shown.copy(runner = it.key, runners = listOfNotNull(it.key)) }
            }
            shown
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MorningScheduleView())

    /**
     * Today's paper, or none, and the kept editions before it ([observerToday]): on the calendar
     * the "already printed today" check reads (the schedule's zone, else this device's), decided
     * again each minute so midnight turns the page while the screen is open.
     */
    val today: StateFlow<ObserverToday> =
        combine(printer.editions, morning.observerNow(account)) { kept, clock -> observerToday(kept, clock.now, clock.calendar) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), observerToday(printer.editions.value, TimeUtils.now(), ObserverCalendar.DEVICE))

    private val _pastDay = MutableStateFlow(initialDay)

    /**
     * The past edition picked (from Past editions, a notification or a link), by its day; null
     * shows today. A day that is today's, or that isn't kept, shows today too ([viewing]).
     */
    val pastDay = _pastDay.asStateFlow()

    /** Shows the kept edition of [day] in place of today's, until [showToday]. */
    fun showDay(day: String) {
        _pastDay.value = day
    }

    /** Back to today's paper, or to the view that prints it. */
    fun showToday() {
        _pastDay.value = null
    }

    /** The edition on screen: the past one picked, else today's paper; null for the print view. */
    val viewing: StateFlow<SavedEdition?> =
        combine(today, _pastDay) { today, day -> today.pastEdition(day, FrontPageClock.dayKey(TimeUtils.now()))?.edition ?: today.paper }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The edition on screen and its page; null while there is none (or it is still being read). */
    val shown: StateFlow<ShownEdition?> =
        viewing
            // The page is read again only when it is another page: a history refresh isn't one.
            .distinctUntilChanged { a, b -> a?.day == b?.day && a?.code == b?.code && a?.sha256 == b?.sha256 && a?.published == b?.published }
            .flatMapLatest { edition ->
                if (edition == null) {
                    flowOf<ShownEdition?>(null)
                } else {
                    flow<ShownEdition?> { emit(ShownEdition(edition, printer.html(edition.day))) }
                }
            }.stateIn<ShownEdition?>(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** One print's estimated cost with the picked model; null when the endpoint lists no prices. */
    val estimate: StateFlow<Double?> =
        combine(_models, printer.editions) { models, _ -> printer.estimate(models.selectedModel) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The screen is started (on screen, not just in the back stack): a signer that needs the
     * reader may be asked to open the schedule until the returned call says it stopped.
     */
    fun scheduleOpened(): () -> Unit = account.observerSchedule.opened()

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

    /** The pending write of the model and key into the schedule: a typed model id writes once the reader stops typing. */
    private var settingsJob: Job? = null

    init {
        viewModelScope.launch { key.collect { it?.let { loadModels(it) } } }
        // Another app or device picked a model: the schedule's choice shows here too.
        viewModelScope.launch {
            namedModel.collect { named ->
                if (named != null && settingsJob?.isActive != true) _models.update { FrontPageModels(it.loading, it.ids, named, it.failure, it.byId) }
            }
        }
    }

    /**
     * Who prints on schedule: the reader's Assistant (the key their kind 10040 names as the
     * `30382:rank` provider, the same lens the setup checklist shows) when they have one, and
     * the runner the schedule names, with their names as this device knows them.
     */
    val whoPrints: StateFlow<WhoPrints> =
        combine(setup, schedule) { setup, schedule -> WhoPrints(setup.lens, setup.lensName, schedule.runner, schedule.runners) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WhoPrints())

    /** Who prints: [runner] (the reader's Assistant), or the reader's own devices when null. */
    fun setRunner(runner: HexKey?) = editSchedule(PendingSchedule(runner = Runner(runner))) { morning.setRunner(account, runner) }

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
                // The schedule's `model` first (every app's shared choice, kept even when the endpoint
                // doesn't list it: the reader chose that price), else this install's old choice.
                val named = namedModel.value
                val remembered = runCatching { printer.memory.get(key.url) }.getOrNull()
                try {
                    val listed = http.listAgentModels(key)
                    val ranked = rankModels(listed.map { it.id })
                    val selected = named ?: remembered?.takeIf { it in ranked } ?: ranked.firstOrNull() ?: remembered
                    _models.value = FrontPageModels(ids = ranked, selected = selected, byId = listed.associateBy { it.id })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: WriterException) {
                    _models.value = FrontPageModels(selected = named ?: remembered, failure = e.failure)
                } catch (e: Exception) {
                    // Never left loading: the reader can still type a model id.
                    _models.value = FrontPageModels(selected = named ?: remembered, failure = WriterFailure.UNREACHABLE)
                }
            }
    }

    /**
     * The model picked in the print view: shown now, remembered on this install as the fallback,
     * and written into the schedule as its `model` param, with the key it prints with as `key`
     * ([FrontPageMorning.setSettings]), so every app and the Assistant print with it. A typed id
     * is written once the reader pauses ([SETTINGS_PAUSE_MS]); the write runs on the account's
     * scope, so leaving the screen doesn't lose it.
     */
    fun selectModel(model: String) {
        val picked = model.trim()
        _models.update { FrontPageModels(it.loading, it.ids, picked.ifEmpty { null }, it.failure, it.byId) }
        val key = key.value ?: return
        if (picked.isEmpty()) return
        viewModelScope.launch { runCatching { printer.memory.set(key.url, picked) } }
        if (picked == namedModel.value && task.value?.let { ObserverParams.keyChoice(it)?.matches(key) } == true) return
        settingsJob?.cancel()
        settingsJob =
            account.scope.launch {
                delay(SETTINGS_PAUSE_MS)
                val outcome =
                    withContext(NonCancellable) {
                        try {
                            morning.setSettings(account, picked, key)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w("FrontPageViewModel", "Could not save the Observer's settings", e)
                            ScheduleEdit.Unreachable
                        }
                    }
                if (outcome !is ScheduleEdit.Saved && outcome != ScheduleEdit.Unchanged) _scheduleProblem.value = outcome
            }
    }

    private val _printRefused = MutableStateFlow(false)

    /** A Print tap found another print running (the morning's, on this device): the screen says so, then [printRefusedShown]. */
    val printRefused = _printRefused.asStateFlow()

    fun printRefusedShown() {
        _printRefused.value = false
    }

    fun print() {
        val key = key.value ?: return
        val model = _models.value.selectedModel ?: return
        // The new edition is today's: it takes the print view's place when it lands.
        if (printer.print(key, model)) _pastDay.value = null else _printRefused.value = true
    }

    /** Stops the print running now, the reader's or the morning's (the screen confirms first: it may have cost money). */
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
    val runner: Runner? = null,
)

/** A who-prints choice on its way: [key] the runner, or null for the reader's own devices. */
@Immutable
data class Runner(
    val key: HexKey?,
)

/**
 * The "Who prints" choice: the reader's Brainstorm [assistant] (their kind 10040 `30382:rank`
 * provider) and its [assistantName], and the runner the schedule names ([runner], or several in
 * [runners]: a conflict, nobody prints). Shown only when there is an Assistant, or a runner to clear.
 */
@Immutable
data class WhoPrints(
    val assistant: HexKey? = null,
    val assistantName: String? = null,
    val runner: HexKey? = null,
    val runners: List<HexKey> = emptyList(),
) {
    val shown: Boolean get() = assistant != null || runners.isNotEmpty()

    val conflict: Boolean get() = runners.size > 1

    /** The Assistant prints on schedule. */
    val byAssistant: Boolean get() = runner != null && runner == assistant

    /** The schedule names a runner that isn't the reader's Assistant (an old one, or another app's). */
    val byOther: Boolean get() = runner != null && runner != assistant
}

/** How long a typed model id waits for the reader to stop typing before it is written into the schedule. */
const val SETTINGS_PAUSE_MS = 1_500L

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
