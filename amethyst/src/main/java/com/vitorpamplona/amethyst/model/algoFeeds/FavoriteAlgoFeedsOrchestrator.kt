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
package com.vitorpamplona.amethyst.model.algoFeeds

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val RESPONSE_TIMEOUT_MS = 20_000L

/**
 * Immutable snapshot of a favorite algo feed's current request/response state.
 *
 * - [requestId] is the id of the most recently published kind-5300 request.
 * - [responseRelays] is the relay set the kind-5300 was sent to — the same set on
 *   which the DVM will publish its 6300/7000 responses, so the home subscription
 *   manager must listen there (not on the user's own outbox).
 * - [ids] and [addresses] are the note references returned by the latest kind-6300 response.
 * - [latestStatus] is the latest kind-7000 status event (processing, payment-required, error, …).
 * - [errorMessage] captures any client-side failure while publishing the request.
 */
data class FavoriteAlgoFeedsSnapshot(
    val requestId: HexKey? = null,
    val responseRelays: Set<NormalizedRelayUrl> = emptySet(),
    val ids: Set<HexKey> = emptySet(),
    val addresses: Set<String> = emptySet(),
    val latestStatus: NIP90StatusEvent? = null,
    val errorMessage: String? = null,
)

/**
 * Manages the NIP-90 content-discovery RPC cycle for each favorite algo feed the user
 * pins to the top-nav.
 *
 * The orchestrator is lazy: it starts a request/response cycle the first time any
 * consumer calls [observe] for a given DVM address, and keeps emitting updated
 * snapshots until [stop] (or account tear-down). Call [refresh] to re-issue the
 * kind-5300 request (e.g. pull-to-refresh).
 *
 * This class does not own the relay subscriptions that fetch DVM responses and
 * matching notes. Those are issued by `HomeOutboxEventsEoseManager` while the
 * user has a `TopFilter.FavoriteAlgoFeed` selected. The orchestrator merely observes
 * what the relays deliver into `LocalCache`.
 */
class FavoriteAlgoFeedsOrchestrator(
    val account: Account,
    val scope: CoroutineScope,
) {
    private val flows = mutableMapOf<Address, MutableStateFlow<FavoriteAlgoFeedsSnapshot>>()
    private val jobs = mutableMapOf<Address, Job>()
    private val mutex = Mutex()

    fun observe(feedAddress: Address): StateFlow<FavoriteAlgoFeedsSnapshot> {
        flows[feedAddress]?.let { return it.asStateFlow() }

        val seed = MutableStateFlow(FavoriteAlgoFeedsSnapshot())
        flows[feedAddress] = seed
        scope.launch { startFor(feedAddress, seed) }
        return seed.asStateFlow()
    }

    fun refresh(feedAddress: Address) {
        val seed = flows[feedAddress] ?: return
        scope.launch {
            mutex.withLock {
                jobs.remove(feedAddress)?.cancel()
            }
            startFor(feedAddress, seed)
        }
    }

    fun stop(feedAddress: Address) {
        scope.launch {
            mutex.withLock {
                jobs.remove(feedAddress)?.cancel()
                flows.remove(feedAddress)
            }
        }
    }

    private suspend fun startFor(
        feedAddress: Address,
        seed: MutableStateFlow<FavoriteAlgoFeedsSnapshot>,
    ) {
        val user = account.cache.checkGetOrCreateUser(feedAddress.pubKeyHex) ?: return
        val job =
            scope.launch(Dispatchers.IO) {
                try {
                    account.requestDVMContentDiscovery(user) { request, relays ->
                        seed.update {
                            it.copy(
                                requestId = request.id,
                                responseRelays = relays,
                                ids = emptySet(),
                                addresses = emptySet(),
                                latestStatus = null,
                                errorMessage = null,
                            )
                        }
                    }

                    val requestId = seed.value.requestId ?: return@launch

                    launch {
                        account.cache
                            .observeLatestEvent<NIP90ContentDiscoveryResponseEvent>(
                                Filter(
                                    kinds = listOf(NIP90ContentDiscoveryResponseEvent.KIND),
                                    tags = mapOf("e" to listOf(requestId)),
                                    limit = 1,
                                ),
                            ).collectLatest { response ->
                                if (response == null) return@collectLatest
                                val (eventIds, addresses) = splitInnerTags(response.innerTags())
                                seed.update {
                                    it.copy(
                                        ids = eventIds,
                                        addresses = addresses,
                                    )
                                }
                            }
                    }

                    launch {
                        account.cache
                            .observeLatestEvent<NIP90StatusEvent>(
                                Filter(
                                    kinds = listOf(NIP90StatusEvent.KIND),
                                    tags = mapOf("e" to listOf(requestId)),
                                    limit = 1,
                                ),
                            ).collectLatest { status ->
                                seed.update { it.copy(latestStatus = status) }
                            }
                    }

                    // If nothing arrives within RESPONSE_TIMEOUT_MS (neither a 6300
                    // response nor any 7000 status), surface an error so the banner
                    // can show Retry instead of spinning forever.
                    launch {
                        delay(RESPONSE_TIMEOUT_MS)
                        val current = seed.value
                        val stillWaiting =
                            current.requestId == requestId &&
                                current.ids.isEmpty() &&
                                current.addresses.isEmpty() &&
                                current.latestStatus == null &&
                                current.errorMessage == null
                        if (stillWaiting) {
                            seed.update { it.copy(errorMessage = "timeout") }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("FavoriteAlgoFeedsOrchestrator", "Failed to start DVM request: ${e.message}", e)
                    seed.update { it.copy(errorMessage = e.message ?: "Unknown error") }
                }
            }

        mutex.withLock { jobs[feedAddress] = job }
    }

    private fun splitInnerTags(innerTags: List<HexKey>): Pair<Set<HexKey>, Set<String>> {
        val ids = mutableSetOf<HexKey>()
        val addresses = mutableSetOf<String>()
        innerTags.forEach { value ->
            if (value.contains(':')) {
                addresses.add(value)
            } else if (value.length == 64) {
                ids.add(value)
            }
        }
        return ids to addresses
    }
}
