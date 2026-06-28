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

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-screen index of the most recent NIP-34 pull-request update event
 * (kind 1619) per parent pull-request id, kept up to date from
 * [LocalCache.live.newEventBundles]. A PR update *revises* its parent PR with a
 * newer commit / merge base, so the UI folds the latest one into the PR rather
 * than listing updates separately. Like [GitStatusIndex], updates aren't tracked
 * in `Note.replies`, so a per-row cache scan would otherwise be required.
 */
object GitPullRequestUpdateIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val started = AtomicBoolean(false)

    private val mutableLatestByPullRequest = MutableStateFlow<Map<HexKey, GitPullRequestUpdateEvent>?>(null)
    val latestByPullRequest: StateFlow<Map<HexKey, GitPullRequestUpdateEvent>?> = mutableLatestByPullRequest.asStateFlow()

    fun startIfNeeded() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            LocalCache.live.newEventBundles
                .onStart {
                    val initial = HashMap<HexKey, GitPullRequestUpdateEvent>()
                    LocalCache.notes.forEach { _, note ->
                        val event = note.event as? GitPullRequestUpdateEvent ?: return@forEach
                        val target = event.parentPullRequestId() ?: return@forEach
                        val current = initial[target]
                        if (current == null || event.createdAt > current.createdAt) {
                            initial[target] = event
                        }
                    }
                    mutableLatestByPullRequest.value = initial
                }.collect { bundle -> processBundle(bundle) }
        }
    }

    private fun processBundle(bundle: Set<Note>) {
        val snapshot = mutableLatestByPullRequest.value ?: emptyMap()
        var modified: HashMap<HexKey, GitPullRequestUpdateEvent>? = null
        for (note in bundle) {
            val event = note.event as? GitPullRequestUpdateEvent ?: continue
            val target = event.parentPullRequestId() ?: continue
            val map = modified ?: snapshot
            val current = map[target]
            if (current == null || event.createdAt > current.createdAt) {
                if (modified == null) modified = HashMap(snapshot)
                modified[target] = event
            }
        }
        modified?.let { mutableLatestByPullRequest.value = it }
    }
}
