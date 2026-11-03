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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthority
import com.vitorpamplona.amethyst.commons.nip34Git.authority.GitAuthorityIndex
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * Cross-screen index of the most recent NIP-34 status event (kinds
 * 1630-1633) per target id, kept up to date from
 * [LocalCache.observeEvents]. Status events are not tracked in
 * `Note.replies` (see `LocalCache.computeReplyTo`), so the only way to
 * find them otherwise would be a full cache scan per row.
 *
 * The kind-indexed [observeEvents] re-emits the whole matching list on every new
 * status event (and seeds it from the cache index via `init()`), so [latestByTarget]
 * is just that list reduced to the latest-per-target map. Shared [SharingStarted.Eagerly]
 * — never `WhileSubscribed` — because callers (e.g. [isClosedOrResolved] and the feed
 * filters) read `.value` synchronously and must not see a stale map when no one is
 * actively collecting. `null` means "not loaded yet".
 */
object GitStatusIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val statusKinds =
        listOf(
            GitStatusEvent.KIND_OPEN,
            GitStatusEvent.KIND_APPLIED,
            GitStatusEvent.KIND_CLOSED,
            GitStatusEvent.KIND_DRAFT,
        )

    val latestByTarget: StateFlow<Map<HexKey, GitStatusEvent>?> =
        combine(
            LocalCache.observeEvents<GitStatusEvent>(Filter(kinds = statusKinds)),
            LocalCache.observeEvents<Event>(Filter(kinds = GitAuthority.ITEM_KINDS)),
            GitAuthorityIndex.authority,
        ) { statuses, items, authority ->
            reduceLatestByTarget(statuses, items.associateBy { it.id }, authority ?: GitAuthority.EMPTY)
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The latest status per item among those its author or a confirmed repository member signed
     * ([GitAuthority]); anyone else's is a proposal and doesn't change the item. An item that hasn't
     * loaded has no known author, so its statuses wait for it. Ties go to the lower event id.
     */
    fun reduceLatestByTarget(
        events: List<GitStatusEvent>,
        itemsById: Map<HexKey, Event>,
        authority: GitAuthority,
    ): Map<HexKey, GitStatusEvent> {
        val latest = HashMap<HexKey, GitStatusEvent>()
        val actorsByItem = HashMap<HexKey, Set<HexKey>>()
        for (event in events) {
            val target = event.rootEventId() ?: continue
            val item = itemsById[target] ?: continue
            val actors = actorsByItem.getOrPut(target) { authority.memberActorsOf(item) }
            if (event.pubKey !in actors) continue
            val current = latest[target]
            if (current == null || event.createdAt > current.createdAt || (event.createdAt == current.createdAt && event.id < current.id)) {
                latest[target] = event
            }
        }
        return latest
    }

    /**
     * Whether the latest status for [targetId] marks it as closed (kind 1632)
     * or applied/resolved/merged (kind 1631). Items with no status event, or
     * whose latest status is open (1630) or draft (1633), are considered open.
     *
     * Reads from the synchronous snapshot in [latestByTarget]; pass an explicit
     * [map] to avoid re-reading the value across a batch.
     */
    fun isClosedOrResolved(
        targetId: HexKey,
        map: Map<HexKey, GitStatusEvent>? = latestByTarget.value,
    ): Boolean {
        val event = map?.get(targetId) ?: return false
        return event is GitStatusClosedEvent || event is GitStatusAppliedEvent
    }
}
