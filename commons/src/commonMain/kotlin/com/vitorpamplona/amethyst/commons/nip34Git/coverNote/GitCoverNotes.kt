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
package com.vitorpamplona.amethyst.commons.nip34Git.coverNote

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache.observeEvents
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
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
 * Cover notes (kind 1624) on NIP-34 issues, patches and PRs, resolved by gitworkshop's rule: only a
 * note by the root item's author or a repository maintainer counts, and the latest of those by
 * `created_at` (ties broken by event id, descending) is the one displayed above the description.
 */
object GitCoverNotes {
    /**
     * Who may set an item's cover note: its author, the repository owner (the announcement's
     * pubkey) and the maintainers the announcement lists. While the announcement has not loaded
     * ([repository] null) the author and owner still count, so the author's own note shows without
     * a flash of nothing.
     */
    fun authorisedAuthors(
        rootAuthor: HexKey,
        repositoryAddress: Address?,
        repository: GitRepositoryEvent?,
    ): Set<HexKey> =
        buildSet {
            add(rootAuthor)
            repositoryAddress?.let { add(it.pubKeyHex) }
            repository?.let {
                add(it.pubKey)
                addAll(it.maintainers())
            }
        }

    /** The cover note to display on [rootId], or null when no authorised author has set one. */
    fun latestAuthorised(
        notes: Collection<GitCoverNoteEvent>,
        rootId: HexKey,
        authorised: Set<HexKey>,
    ): GitCoverNoteEvent? =
        notes
            .filter { it.rootEventId() == rootId && it.pubKey in authorised }
            .maxWithOrNull(compareBy<GitCoverNoteEvent> { it.createdAt }.thenBy { it.id })
}

/**
 * Cross-screen index of every cover note in the cache, grouped by the item it covers, kept up to
 * date from the kind-indexed [observeEvents] like the other NIP-34 indexes. Eager so a card reads
 * `.value` without waiting; `null` means "not loaded yet".
 */
object GitCoverNoteIndex {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val byRoot: StateFlow<Map<HexKey, List<GitCoverNoteEvent>>?> =
        LocalCache
            .observeEvents<GitCoverNoteEvent>(Filter(kinds = listOf(GitCoverNoteEvent.KIND)))
            .map { notes -> notes.groupBy { it.rootEventId() ?: "" }.filterKeys { it.isNotEmpty() } }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)
}
