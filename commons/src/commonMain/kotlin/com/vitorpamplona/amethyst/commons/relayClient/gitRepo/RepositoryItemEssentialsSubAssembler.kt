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

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.FilterShape
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.RelayCursorHolder
import com.vitorpamplona.amethyst.commons.relayClient.eoseManagers.SingleSubEoseManager
import com.vitorpamplona.amethyst.commons.relays.SincePerRelayMap
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
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
    /**
     * Each item note keeps its own cursors ([Note.childCursors]), because it holds the statuses,
     * labels, cover notes and replies they vouch for: an item LocalCache dropped and rebuilt has
     * none and is asked for its whole history, and a newly listed item is asked from scratch in a
     * filter of its own without making the others refetch. Items asked from the same point share
     * a filter.
     */
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

        return idsPerRelay.flatMap { (relay, ids) ->
            ids
                .groupBy { cache.getNoteIfExists(it)?.childCursorsOrNull()?.since(relay, cursorSource) }
                .flatMap { (itemSince, group) -> filterRepositoryItemEssentials(relay, group, itemSince) }
        }
    }

    /** The items' own cursors decide, not one per relay. */
    override fun cursorHoldersOf(key: RepositoryQueryState): List<RelayCursorHolder> = emptyList()

    /**
     * An answer reaches an item only for filters that name it, and only when the filter asked from
     * scratch or from no later than the item's own cursor.
     */
    override fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
        filters: List<Filter>?,
    ) {
        val answered = mutableMapOf<Note, MutableList<Filter>>()
        val refused = mutableSetOf<Note>()
        filters.orEmpty().forEach { filter ->
            if (filter.until != null) return@forEach
            (filter.tags?.get("e").orEmpty() + filter.tags?.get("E").orEmpty()).forEach { id ->
                val note = cache.getNoteIfExists(id) ?: return@forEach
                val asked = filter.since
                val own = note.childCursorsOrNull()?.since(relay, cursorSource)
                if (asked == null || (own != null && own >= asked)) {
                    answered.getOrPut(note) { mutableListOf() }.add(filter)
                } else {
                    refused.add(note)
                }
            }
        }
        answered.forEach { (note, asked) ->
            if (note !in refused) note.childCursors().newEose(relay, asked.map { FilterShape.of(it) }, time, cursorSource)
        }
        super.newEose(relay, time, filters)
    }

    override fun distinct(key: RepositoryQueryState) = key.repository.idHex
}
