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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.model.LocalCache.observeEvents
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.status.GitStatusAppliedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusClosedEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-screen index of the most recent NIP-34 status event (kinds
 * 1630-1633) per target id, kept up to date from
 * [LocalCache.observeEvents]. Status events are not tracked in
 * `Note.replies` (see `LocalCache.computeReplyTo`), so the only way to
 * find them otherwise would be a full cache scan per row.
 *
 * The kind-indexed [observeEvents] subscription replaces both the full-cache
 * `onStart` scan and the per-bundle type filtering the old
 * `LocalCache.live.newEventBundles` flow needed: the observable's `init()` seeds
 * the matching set from the index and re-emits the whole list on every new
 * status event, so we just reduce it to the latest-per-target map each time.
 */
object GitStatusIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    private val statusKinds =
        listOf(
            GitStatusEvent.KIND_OPEN,
            GitStatusEvent.KIND_APPLIED,
            GitStatusEvent.KIND_CLOSED,
            GitStatusEvent.KIND_DRAFT,
        )

    private val mutableLatestByTarget = MutableStateFlow<Map<HexKey, GitStatusEvent>?>(null)
    val latestByTarget: StateFlow<Map<HexKey, GitStatusEvent>?> = mutableLatestByTarget.asStateFlow()

    fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            LocalCache
                .observeEvents<GitStatusEvent>(Filter(kinds = statusKinds))
                .collect { events -> mutableLatestByTarget.value = latestByTarget(events) }
        }
    }

    private fun latestByTarget(events: List<GitStatusEvent>): Map<HexKey, GitStatusEvent> {
        val latest = HashMap<HexKey, GitStatusEvent>()
        for (event in events) {
            val target = event.rootEventId() ?: continue
            val current = latest[target]
            if (current == null || event.createdAt > current.createdAt) {
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
