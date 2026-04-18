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
package com.vitorpamplona.amethyst.model.dvms

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip90Dvms.contentDiscoveryResponse.NIP90ContentDiscoveryResponseEvent
import com.vitorpamplona.quartz.nip90Dvms.status.NIP90StatusEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Immutable snapshot of a favourite DVM's current request/response state.
 *
 * - [requestId] is the id of the most recently published kind-5300 request.
 * - [ids] and [addresses] are the note references returned by the latest kind-6300 response.
 * - [latestStatus] is the latest kind-7000 status event (processing, payment-required, error, …).
 * - [errorMessage] captures any client-side failure while publishing the request.
 */
data class FavoriteDvmSnapshot(
    val requestId: HexKey? = null,
    val ids: Set<HexKey> = emptySet(),
    val addresses: Set<String> = emptySet(),
    val latestStatus: NIP90StatusEvent? = null,
    val errorMessage: String? = null,
)

/**
 * Manages the NIP-90 content-discovery RPC cycle for each favourite DVM the user
 * pins to the top-nav.
 *
 * The orchestrator is lazy: it starts a request/response cycle the first time any
 * consumer calls [observe] for a given DVM address, and keeps emitting updated
 * snapshots until [stop] (or account tear-down). Call [refresh] to re-issue the
 * kind-5300 request (e.g. pull-to-refresh).
 *
 * This class does not own the relay subscriptions that fetch DVM responses and
 * matching notes. Those are issued by `HomeOutboxEventsEoseManager` while the
 * user has a `TopFilter.FavoriteDvm` selected. The orchestrator merely observes
 * what the relays deliver into `LocalCache`.
 */
class FavoriteDvmOrchestrator(
    val account: Account,
    val scope: CoroutineScope,
) {
    private val flows = mutableMapOf<Address, MutableStateFlow<FavoriteDvmSnapshot>>()
    private val jobs = mutableMapOf<Address, Job>()
    private val mutex = Mutex()

    fun observe(dvmAddress: Address): StateFlow<FavoriteDvmSnapshot> {
        flows[dvmAddress]?.let { return it.asStateFlow() }

        val seed = MutableStateFlow(FavoriteDvmSnapshot())
        flows[dvmAddress] = seed
        scope.launch { startFor(dvmAddress, seed) }
        return seed.asStateFlow()
    }

    fun refresh(dvmAddress: Address) {
        val seed = flows[dvmAddress] ?: return
        scope.launch {
            mutex.withLock {
                jobs.remove(dvmAddress)?.cancel()
            }
            startFor(dvmAddress, seed)
        }
    }

    fun stop(dvmAddress: Address) {
        scope.launch {
            mutex.withLock {
                jobs.remove(dvmAddress)?.cancel()
                flows.remove(dvmAddress)
            }
        }
    }

    private suspend fun startFor(
        dvmAddress: Address,
        seed: MutableStateFlow<FavoriteDvmSnapshot>,
    ) {
        val user = account.cache.checkGetOrCreateUser(dvmAddress.pubKeyHex) ?: return
        val job =
            scope.launch(Dispatchers.IO) {
                try {
                    account.requestDVMContentDiscovery(user) { request ->
                        seed.update {
                            it.copy(
                                requestId = request.id,
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("FavoriteDvmOrchestrator", "Failed to start DVM request: ${e.message}", e)
                    seed.update { it.copy(errorMessage = e.message ?: "Unknown error") }
                }
            }

        mutex.withLock { jobs[dvmAddress] = job }
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
