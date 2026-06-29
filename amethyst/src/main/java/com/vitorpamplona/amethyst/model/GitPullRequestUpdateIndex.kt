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
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-screen index of the most recent NIP-34 pull-request update event
 * (kind 1619) per parent pull-request id, kept up to date from
 * [LocalCache.observeEvents]. A PR update *revises* its parent PR with a
 * newer commit / merge base, so the UI folds the latest one into the PR rather
 * than listing updates separately. Like [GitStatusIndex], updates aren't tracked
 * in `Note.replies`, so a per-row cache scan would otherwise be required.
 *
 * The kind-indexed [observeEvents] subscription replaces both the full-cache
 * `onStart` scan and the per-bundle type filtering the old
 * `LocalCache.live.newEventBundles` flow needed: the observable's `init()` seeds
 * the matching set from the index and re-emits the whole list on every new 1619,
 * so we just reduce it to the latest-per-parent map each time. PR updates are
 * rare, so recomputing the full map per emission is cheaper than it scanning
 * every event of every kind.
 */
object GitPullRequestUpdateIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    private val mutableLatestByPullRequest = MutableStateFlow<Map<HexKey, GitPullRequestUpdateEvent>?>(null)
    val latestByPullRequest: StateFlow<Map<HexKey, GitPullRequestUpdateEvent>?> = mutableLatestByPullRequest.asStateFlow()

    fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            LocalCache
                .observeEvents<GitPullRequestUpdateEvent>(Filter(kinds = listOf(GitPullRequestUpdateEvent.KIND)))
                .collect { events -> mutableLatestByPullRequest.value = latestByParent(events) }
        }
    }

    private fun latestByParent(events: List<GitPullRequestUpdateEvent>): Map<HexKey, GitPullRequestUpdateEvent> {
        val latest = HashMap<HexKey, GitPullRequestUpdateEvent>()
        for (event in events) {
            val target = event.parentPullRequestId() ?: continue
            val current = latest[target]
            if (current == null || event.createdAt > current.createdAt) {
                latest[target] = event
            }
        }
        return latest
    }
}
