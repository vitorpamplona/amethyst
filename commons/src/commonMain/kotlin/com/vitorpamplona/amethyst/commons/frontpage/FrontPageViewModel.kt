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
import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
)

/**
 * Print today's paper with the reader's own AI key. Nothing is stored or published: the
 * last page lives in this ViewModel and in whatever the reader downloads.
 */
class FrontPageViewModel(
    private val account: Account,
    private val http: IRoleBasedHttpClientBuilder,
    private val memory: FrontPageModelMemory,
    private val boundary: PaperBoundary? = paperBoundary(),
) : ViewModel() {
    val key: StateFlow<AgentKeyTag?> =
        account.agentKeys.flow
            .map { it.firstOrNull() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, account.agentKeys.preferred())

    val gate: StateFlow<FrontPageGate> =
        combine(key, account.trustProviderList.liveUserRankProvider) { key, rank ->
            when {
                boundary == null -> FrontPageGate.UNSUPPORTED
                key == null -> FrontPageGate.NO_KEY
                rank == null -> FrontPageGate.NO_LENS
                else -> FrontPageGate.READY
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FrontPageGate.NO_KEY)

    private val _models = MutableStateFlow(FrontPageModels())
    val models = _models.asStateFlow()

    private val _run = MutableStateFlow<FrontPageRun>(FrontPageRun.Idle)
    val run = _run.asStateFlow()

    private var printing: Job? = null
    private var modelsFor: String? = null

    init {
        viewModelScope.launch { key.collect { it?.let { loadModels(it) } } }
    }

    private fun loadModels(key: AgentKeyTag) {
        if (modelsFor == key.url && !_models.value.loading) return
        modelsFor = key.url
        _models.value = FrontPageModels(loading = true)
        viewModelScope.launch {
            val remembered = runCatching { memory.get(key.url) }.getOrNull()
            try {
                val ranked = rankModels(http.listAgentModels(key))
                val selected = remembered?.takeIf { it in ranked } ?: ranked.firstOrNull() ?: remembered
                _models.value = FrontPageModels(ids = ranked, selected = selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WriterException) {
                _models.value = FrontPageModels(selected = remembered, failure = e.failure)
            }
        }
    }

    fun selectModel(model: String) {
        _models.update { FrontPageModels(it.loading, it.ids, model.trim().ifEmpty { null }, it.failure) }
        val url = key.value?.url ?: return
        if (model.isNotBlank()) viewModelScope.launch { runCatching { memory.set(url, model.trim()) } }
    }

    fun print() {
        val key = key.value ?: return
        val model = _models.value.selected ?: return
        val boundary = boundary ?: return
        if (printing?.isActive == true) return
        val press =
            FrontPagePress(
                client = account.client,
                relay = Constants.brainstorm,
                boundary = boundary,
                write = { messages, onText -> http.chatComplete(key, model, messages, onText) },
            )
        printing =
            viewModelScope.launch {
                try {
                    val result = press.print(account.signer.pubKey, readerName()) { step -> _run.value = FrontPageRun.Running(step) }
                    _run.value = FrontPageRun.Done(result)
                } catch (e: CancellationException) {
                    _run.value = FrontPageRun.Idle
                    throw e
                } catch (e: WriterException) {
                    _run.value = FrontPageRun.Failed(e.failure, e.status, e.providerSaid)
                } catch (e: Exception) {
                    Log.w("FrontPage", "Printing failed", e)
                    _run.value = FrontPageRun.Failed(WriterFailure.UNREACHABLE)
                }
            }
    }

    fun cancel() {
        printing?.cancel()
        printing = null
        _run.value = FrontPageRun.Idle
    }

    /** The reader's name for the masthead line; never hex. */
    private fun readerName(): String =
        account
            .userProfile()
            .metadataOrNull()
            ?.bestName()
            ?.trim()
            ?.ifEmpty { null } ?: shortNpub(account.signer.pubKey)
}
