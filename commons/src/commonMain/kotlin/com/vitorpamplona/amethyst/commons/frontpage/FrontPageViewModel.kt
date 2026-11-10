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
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** A kept edition and its page, as the screen shows it. */
@Immutable
class ShownEdition(
    val edition: SavedEdition,
    val html: String,
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
    private val schedules: FrontPageScheduleStore,
    private val scheduler: FrontPageScheduler,
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
                    flow<ShownEdition?> { emit(printer.html(edition.day)?.let { ShownEdition(edition, it) }) }
                }
            }.stateIn<ShownEdition?>(viewModelScope, SharingStarted.Eagerly, null)

    /** One print's estimated cost with the picked model; null when the endpoint lists no prices. */
    val estimate: StateFlow<Double?> =
        combine(_models, printer.editions) { models, _ -> printer.estimate(models.selectedModel) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val schedule: StateFlow<FrontPageSchedule> =
        schedules.flow(account.signer.pubKey).stateIn(viewModelScope, SharingStarted.Eagerly, FrontPageSchedule())

    /** Whether a scheduled edition can print with nobody there: a key on this device can, Amber cannot. */
    val printsUnattended: Boolean get() = printer.canPrintUnattended()

    private var modelsFor: String? = null

    init {
        viewModelScope.launch { key.collect { it?.let { loadModels(it) } } }
    }

    private fun loadModels(key: AgentKeyTag) {
        if (modelsFor == key.url && !_models.value.loading) return
        modelsFor = key.url
        _models.value = FrontPageModels(loading = true)
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

    fun setSchedule(schedule: FrontPageSchedule) {
        viewModelScope.launch {
            schedules.set(account.signer.pubKey, schedule)
            scheduler.reschedule(account.signer.pubKey, schedule)
        }
    }
}
