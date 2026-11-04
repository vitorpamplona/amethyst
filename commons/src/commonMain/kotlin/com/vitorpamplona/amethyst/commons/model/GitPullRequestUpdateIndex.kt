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
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Cross-screen index of the most recent NIP-34 pull-request update event
 * (kind 1619) per parent pull-request id, kept up to date from
 * [LocalCache.observeEvents]. A PR update *revises* its parent PR with a
 * newer commit / merge base, so the UI folds the latest one into the PR rather
 * than listing updates separately. Like [GitStatusIndex], updates aren't tracked
 * in `Note.replies`, so a per-row cache scan would otherwise be required.
 *
 * The kind-indexed [observeEvents] re-emits the whole matching list on every new
 * 1619 (and seeds it from the cache index via `init()`), so [latestByPullRequest]
 * is just that list reduced to the latest-per-parent map. Shared [SharingStarted.Eagerly]
 * — never `WhileSubscribed` — because callers read `.value` synchronously and must
 * not see a stale map when no one is actively collecting. `null` means "not loaded yet".
 */
object GitPullRequestUpdateIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Every update per parent pull-request id, oldest first ([GitPullRequestRevisions.order]). The
     * thread timeline and the update card read it to say which commit an update replaced.
     */
    val byPullRequest: StateFlow<Map<HexKey, List<GitPullRequestUpdateEvent>>?> =
        combine(
            LocalCache.observeEvents<GitPullRequestUpdateEvent>(Filter(kinds = listOf(GitPullRequestUpdateEvent.KIND))),
            LocalCache.observeEvents<GitPullRequestEvent>(Filter(kinds = listOf(GitPullRequestEvent.KIND))),
            GitAuthorityIndex.authority,
        ) { updates, pullRequests, authority ->
            groupByParent(updates, pullRequests.associateBy { it.id }, authority ?: GitAuthority.EMPTY)
        }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    val latestByPullRequest: StateFlow<Map<HexKey, GitPullRequestUpdateEvent>?> =
        byPullRequest
            .map { byParent -> byParent?.mapValues { it.value.last() } }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The updates per PR, oldest first, keeping only those the PR's author or a confirmed repository
     * maintainer signed, as of the update ([GitAuthority.isMaintainerAt]): anyone else's would let a stranger move
     * the tip, and the clone URL "View changes" fetches from. Updates of a PR that hasn't loaded wait
     * for it.
     */
    fun groupByParent(
        events: List<GitPullRequestUpdateEvent>,
        pullRequestsById: Map<HexKey, GitPullRequestEvent>,
        authority: GitAuthority,
    ): Map<HexKey, List<GitPullRequestUpdateEvent>> {
        val byParent = HashMap<HexKey, MutableList<GitPullRequestUpdateEvent>>()
        for (event in events) {
            val target = event.parentPullRequestId() ?: continue
            val pullRequest = pullRequestsById[target] ?: continue
            if (!authority.isMaintainerAt(event.pubKey, pullRequest, event.createdAt)) continue
            byParent.getOrPut(target) { ArrayList() }.add(event)
        }
        return byParent.mapValues { it.value.sortedWith(GitPullRequestRevisions.order) }
    }
}
