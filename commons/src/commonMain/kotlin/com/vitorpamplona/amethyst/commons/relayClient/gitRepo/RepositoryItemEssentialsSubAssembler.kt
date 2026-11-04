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
package com.vitorpamplona.amethyst.commons.relayClient.gitRepo

import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.utils.mapOfSet

/**
 * Asks a repository's relays for what decides each of its items' state ([RepositoryItemEssentialKinds],
 * by the items' ids), as gitworkshop does for every item it lists. Without it, the statuses, labels
 * and cover notes that carry no `a` reached an item only when its thread was opened, and the
 * Open / Closed counts were taken over whatever happened to be loaded.
 */
class RepositoryItemEssentialsSubAssembler(
    val cache: ICacheProvider,
    client: INostrClient,
    allKeys: () -> Set<RepositoryQueryState>,
) : SingleSubEoseManager<RepositoryQueryState>(client, allKeys) {
    override fun updateFilter(
        keys: List<RepositoryQueryState>,
        since: SincePerRelayMap?,
    ): List<RelayBasedFilter> {
        if (keys.isEmpty()) return emptyList()

        val idsPerRelay =
            mapOfSet {
                keys.forEach { key ->
                    val event = key.repository.event as? GitRepositoryEvent ?: return@forEach
                    val ids = key.currentItemIds.value
                    if (ids.isEmpty()) return@forEach
                    repositoryRelays(cache, key.repository, event).forEach { relay -> ids.forEach { add(relay, it) } }
                }
            }

        // A newly listed item has no history on the relay yet: drop that relay's cursor.
        val sinceNow = sinceDroppingGrownRelays(idsPerRelay, since)

        return idsPerRelay.flatMap { (relay, ids) -> filterRepositoryItemEssentials(relay, ids, sinceNow?.get(relay)?.time) }
    }

    override fun distinct(key: RepositoryQueryState) = key.repository.idHex
}
