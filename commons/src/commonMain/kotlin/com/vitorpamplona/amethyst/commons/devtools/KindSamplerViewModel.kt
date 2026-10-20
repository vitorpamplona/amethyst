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
package com.vitorpamplona.amethyst.commons.devtools

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

enum class KindSamplerPhase {
    IDLE,
    DISCOVERING,
    SAMPLING,
    DONE,
}

@Immutable
data class KindSamplerState(
    val phase: KindSamplerPhase = KindSamplerPhase.IDLE,
    val relaysTotal: Int = 0,
    val relaysAnswered: Int = 0,
    val relaysWithEvents: Int = 0,
    val events: Int = 0,
    val byClient: ImmutableList<KindVariant> = persistentListOf(),
    val byShape: ImmutableList<KindVariant> = persistentListOf(),
)

/**
 * Kind Lookup's engine: samples recent events of one kind from many relays and folds them into
 * the ways the kind is written ([KindVariantCollector]). Breadth over depth, since different
 * clients publish to different relays: a few dozen recent events from each of hundreds of relays
 * surface more encodings than thousands from a few big ones.
 *
 * Runs on a throwaway crawl client (events stay out of LocalCache) and skips relays the socket
 * builder will not dial yet (Tor-routed while Tor is down).
 */
class KindSamplerViewModel(
    private val kind: Int,
    private val seedRelays: Set<NormalizedRelayUrl>,
    private val clientBuilder: () -> INostrClient,
    private val canDial: (NormalizedRelayUrl) -> Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(KindSamplerState())
    val state: StateFlow<KindSamplerState> = _state

    private var job: Job? = null

    fun start(includeDiscovered: Boolean) {
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                val client = clientBuilder()
                try {
                    run(client, includeDiscovered)
                } finally {
                    client.close()
                }
            }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(phase = KindSamplerPhase.DONE)
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private suspend fun run(
        client: INostrClient,
        includeDiscovered: Boolean,
    ) {
        _state.value = KindSamplerState(phase = KindSamplerPhase.DISCOVERING)
        val discovered = if (includeDiscovered) RelayProbeViewModel.discoverRelays(client) else emptySet()
        // Seeds first: the big relays answer fast and give the screen something to show early.
        val relays = (seedRelays + discovered).filter(canDial)

        val collector = KindVariantCollector()
        val lock = Mutex()
        var answered = 0
        var withEvents = 0
        _state.value = KindSamplerState(phase = KindSamplerPhase.SAMPLING, relaysTotal = relays.size)

        val filter = Filter(kinds = listOf(kind), limit = PER_RELAY)
        val permits = Semaphore(RelayProbeViewModel.PARALLEL_RELAYS)
        coroutineScope {
            relays.forEach { relay ->
                launch {
                    permits.withPermit {
                        if (lock.withLock { collector.total } >= MAX_EVENTS) return@withPermit
                        var got = 0
                        try {
                            withTimeoutOrNull(RelayProbeViewModel.RELAY_DEADLINE_MS) {
                                client.fetchAllWithHooks(mapOf(relay to listOf(filter)), idleTimeoutMs = RelayProbeViewModel.IDLE_MS) { _, event ->
                                    if (event.kind == kind) {
                                        lock.withLock { collector.add(event) }
                                        got++
                                    }
                                    true
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // An unreachable relay is just one fewer sample.
                        }
                        lock.withLock {
                            answered++
                            if (got > 0) withEvents++
                            publish(collector, answered, withEvents, relays.size, KindSamplerPhase.SAMPLING)
                        }
                    }
                }
            }
        }

        lock.withLock { publish(collector, answered, withEvents, relays.size, KindSamplerPhase.DONE) }
    }

    private fun publish(
        collector: KindVariantCollector,
        answered: Int,
        withEvents: Int,
        total: Int,
        phase: KindSamplerPhase,
    ) {
        _state.value =
            KindSamplerState(
                phase = phase,
                relaysTotal = total,
                relaysAnswered = answered,
                relaysWithEvents = withEvents,
                events = collector.total,
                byClient = collector.variants(KindVariantGrouping.CLIENT),
                byShape = collector.variants(KindVariantGrouping.SHAPE),
            )
    }

    companion object {
        /** Recent events asked of each relay. */
        const val PER_RELAY = 50

        /** Enough to see every common encoding; past this the screen stops asking new relays. */
        const val MAX_EVENTS = 5_000
    }
}
