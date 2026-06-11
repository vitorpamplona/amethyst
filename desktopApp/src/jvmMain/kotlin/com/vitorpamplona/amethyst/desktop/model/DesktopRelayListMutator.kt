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
package com.vitorpamplona.amethyst.desktop.model

import com.vitorpamplona.amethyst.commons.model.nip65RelayList.Nip65RelayListState
import com.vitorpamplona.amethyst.commons.relays.health.RelayListKind
import com.vitorpamplona.amethyst.commons.relays.health.RelayListMutator
import com.vitorpamplona.amethyst.commons.relays.health.RelayRemovalResult
import com.vitorpamplona.amethyst.desktop.network.RelayConnectionManager
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Desktop implementation of [RelayListMutator]. Edits the user's NIP-65-style
 * relay lists in parallel (one signing+broadcast op per kind the relay is in),
 * then re-publishes via the connection manager so connected relays see the new
 * versions.
 *
 * Sign requests run in parallel via async/awaitAll so that a slow NIP-46 bunker
 * doesn't multiply latency by 4 lists.
 */
class DesktopRelayListMutator(
    private val signer: NostrSigner,
    private val nip65State: Nip65RelayListState,
    private val accountRelays: DesktopAccountRelays,
    private val relayManager: RelayConnectionManager,
) : RelayListMutator {
    override suspend fun removeFromAllUserLists(url: NormalizedRelayUrl): RelayRemovalResult =
        coroutineScope {
            val failed = mutableSetOf<RelayListKind>()
            val attempts = mutableListOf<RelayListKind>()

            val jobs = mutableListOf<kotlinx.coroutines.Deferred<RelayListKind?>>()

            val nip65Current =
                nip65State
                    .getNIP65RelayList()
                    ?.relays()
                    ?.toMutableList()
            if (nip65Current != null && nip65Current.any { it.relayUrl == url }) {
                attempts += RelayListKind.Nip65
                jobs +=
                    async {
                        runCatching {
                            val newRelays = nip65Current.filterNot { it.relayUrl == url }
                            val event = nip65State.saveRelayList(newRelays)
                            relayManager.broadcastToAll(event)
                        }.fold(onSuccess = { null }, onFailure = { RelayListKind.Nip65 })
                    }
            }

            val dmRelays = accountRelays.dmRelayList.value
            if (url in dmRelays) {
                attempts += RelayListKind.DmInbox
                jobs +=
                    async {
                        runCatching {
                            val newRelays = (dmRelays - url).toList()
                            val event = ChatMessageRelayListEvent.create(newRelays, signer)
                            accountRelays.consumePublishedEvent(event)
                            relayManager.broadcastToAll(event)
                        }.fold(onSuccess = { null }, onFailure = { RelayListKind.DmInbox })
                    }
            }

            val searchRelays = accountRelays.searchRelayList.value
            if (url in searchRelays) {
                attempts += RelayListKind.Search
                jobs +=
                    async {
                        runCatching {
                            val newRelays = (searchRelays - url).toList()
                            val event = SearchRelayListEvent.create(newRelays, signer)
                            accountRelays.consumePublishedEvent(event)
                            relayManager.broadcastToAll(event)
                        }.fold(onSuccess = { null }, onFailure = { RelayListKind.Search })
                    }
            }

            val blockedRelays = accountRelays.blockedRelayList.value
            if (url in blockedRelays) {
                attempts += RelayListKind.Blocked
                jobs +=
                    async {
                        runCatching {
                            val newRelays = (blockedRelays - url).toList()
                            val event = BlockedRelayListEvent.create(newRelays, signer)
                            accountRelays.consumePublishedEvent(event)
                            relayManager.broadcastToAll(event)
                        }.fold(onSuccess = { null }, onFailure = { RelayListKind.Blocked })
                    }
            }

            if (attempts.isEmpty()) return@coroutineScope RelayRemovalResult.Success
            jobs.awaitAll().filterNotNull().forEach { failed += it }

            when {
                failed.isEmpty() -> RelayRemovalResult.Success
                failed.size == attempts.size -> RelayRemovalResult.Failure("All lists failed to publish")
                else -> RelayRemovalResult.Partial(failed)
            }
        }
}

/** Compute which monitored lists each relay currently lives in. */
fun computeListMembership(
    nip65: Set<NormalizedRelayUrl>,
    dm: Set<NormalizedRelayUrl>,
    search: Set<NormalizedRelayUrl>,
    blocked: Set<NormalizedRelayUrl>,
): Map<NormalizedRelayUrl, Set<RelayListKind>> {
    val all = nip65 + dm + search + blocked
    return all.associateWith { url ->
        buildSet {
            if (url in nip65) add(RelayListKind.Nip65)
            if (url in dm) add(RelayListKind.DmInbox)
            if (url in search) add(RelayListKind.Search)
            if (url in blocked) add(RelayListKind.Blocked)
        }
    }
}
