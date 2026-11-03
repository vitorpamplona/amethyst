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
package com.vitorpamplona.amethyst.commons.nip34Git.authority

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryMembership
import com.vitorpamplona.quartz.nip34Git.repository.repositoryAddresses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Who may change an issue's, patch's or PR's state, from the repository announcements in the
 * cache. Anyone can publish a status, a PR update, a label or a cover note for any item; only the
 * item's author and the repository's confirmed members ([GitRepositoryMembership]) change what
 * the item shows. Everything else is at most a proposal.
 *
 * Without per-event role history this uses the membership as of now, as gitworkshop does while
 * history is loading. While a repository's announcement has not loaded, its coordinate's own
 * pubkey stands in for its members, so the owner's statuses show without a flash.
 */
@Immutable
class GitAuthority(
    private val announcements: Map<String, Map<HexKey, GitRepositoryEvent>>,
) {
    /** The membership seen from [repository]'s announcement, or null while it is unknown. */
    fun membership(repository: Address): GitRepositoryMembership? = announcements[repository.dTag]?.let { GitRepositoryMembership.resolve(repository.pubKeyHex, repository.dTag, it) }

    /**
     * Who may set status, labels, subject edits and cover notes on an item by [itemAuthor] filed
     * under [repositories]: the author plus every confirmed member of those repositories.
     */
    fun memberActors(
        itemAuthor: HexKey,
        repositories: List<Address>,
    ): Set<HexKey> =
        buildSet {
            add(itemAuthor)
            repositories.forEach { repository ->
                val membership = membership(repository)
                if (membership == null) add(repository.pubKeyHex) else addAll(membership.confirmedMembers)
            }
        }

    /** Who may move a PR's tip with an update (kind 1619): the author plus confirmed maintainers. */
    fun maintainerActors(
        itemAuthor: HexKey,
        repositories: List<Address>,
    ): Set<HexKey> =
        buildSet {
            add(itemAuthor)
            repositories.forEach { repository ->
                val membership = membership(repository)
                if (membership == null) add(repository.pubKeyHex) else addAll(membership.confirmedMaintainers)
            }
        }

    /** Whether [pubKey] is a confirmed maintainer of a repository [item] is filed against (not merely its author). */
    fun isRepositoryMaintainer(
        pubKey: HexKey,
        item: Event,
    ): Boolean =
        repositoriesOf(item).any { repository ->
            membership(repository)?.isMaintainer(pubKey) ?: (repository.pubKeyHex == pubKey)
        }

    /** [memberActors] of [item], for an issue, patch or PR event. */
    fun memberActorsOf(item: Event): Set<HexKey> = memberActors(item.pubKey, repositoriesOf(item))

    /** [maintainerActors] of [item]. */
    fun maintainerActorsOf(item: Event): Set<HexKey> = maintainerActors(item.pubKey, repositoriesOf(item))

    /**
     * Whether [change], a status (1630-1633) or a PR update (1619), counts for [item]. Without the
     * item its author is unknown, so only members of the repositories [change] names count: a
     * stranger's status about an item we haven't seen is not trusted.
     */
    fun isAuthorisedChange(
        change: Event,
        item: Event?,
    ): Boolean {
        val maintainersOnly = change is GitPullRequestUpdateEvent
        if (item != null) return change.pubKey in (if (maintainersOnly) maintainerActorsOf(item) else memberActorsOf(item))
        return change.tags.repositoryAddresses().any { repository ->
            val membership = membership(repository)
            when {
                membership == null -> repository.pubKeyHex == change.pubKey
                maintainersOnly -> membership.isMaintainer(change.pubKey)
                else -> membership.isMember(change.pubKey)
            }
        }
    }

    companion object {
        val EMPTY = GitAuthority(emptyMap())

        /** The kinds whose state the authority rules govern: issues, patches and pull requests. */
        val ITEM_KINDS = listOf(GitIssueEvent.KIND, GitPatchEvent.KIND, GitPullRequestEvent.KIND)

        /** Every repository coordinate an issue, patch or PR is filed against. */
        fun repositoriesOf(item: Event): List<Address> =
            when (item) {
                is GitIssueEvent -> item.repositoryAddresses()
                is GitPatchEvent -> item.repositoryAddresses()
                is GitPullRequestEvent -> item.repositoryAddresses()
                else -> emptyList()
            }
    }
}

/**
 * The [GitAuthority] over every repository announcement in the cache, kept current from
 * [LocalCache.observeEvents]. Eager, like the other NIP-34 indexes, so callers can read `.value`
 * synchronously; `null` until the first scan.
 */
object GitAuthorityIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val authority: StateFlow<GitAuthority?> =
        LocalCache
            .observeEvents<GitRepositoryEvent>(Filter(kinds = listOf(GitRepositoryEvent.KIND)))
            .map { events ->
                val byIdentifier = events.groupBy { it.dTag() }
                GitAuthority(byIdentifier.mapValues { (identifier, list) -> GitRepositoryMembership.latestByAuthor(list, identifier) })
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)
}
