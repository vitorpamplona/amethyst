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
package com.vitorpamplona.amethyst.commons.model.nip51Lists

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.remove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Account state for the user's bookmarked (starred) git repositories — NIP-51
 * kind 10018. Mirrors [BookmarkListState] but only the public list is used for
 * the star toggle; removal rebuilds the public tags and preserves the encrypted
 * private section untouched, so it never needs to decrypt.
 */
@Stable
class GitRepositoryListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    // Long-term reference so the GC keeps the note alive.
    val repositoryList = cache.getOrCreateAddressableNote(getListAddress())

    fun getListAddress() = GitRepositoryListEvent.createAddress(signer.pubKey)

    fun getListFlow(): StateFlow<NoteState> = repositoryList.flow().metadata.stateFlow

    fun getList(): GitRepositoryListEvent? = repositoryList.event as? GitRepositoryListEvent

    private fun publicAddresses(note: Note): Set<Address> = (note.event as? GitRepositoryListEvent)?.publicRepositories()?.map { it.address }?.toSet() ?: emptySet()

    @OptIn(FlowPreview::class)
    val publicRepositoryAddressSet: StateFlow<Set<Address>> =
        getListFlow()
            .map { publicAddresses(it.note) }
            .onStart { emit(publicAddresses(repositoryList)) }
            .debounce(100)
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isBookmarked(address: Address): Boolean = publicRepositoryAddressSet.value.contains(address)

    /** Adds [note]'s address to the public list, creating the list if needed. */
    suspend fun addRepository(note: AddressableNote): GitRepositoryListEvent {
        val list = getList()
        val bookmark = AddressBookmark(note.address, note.relayHintUrl())
        return if (list == null) {
            GitRepositoryListEvent.create(publicRepositories = listOf(bookmark), signer = signer)
        } else {
            GitRepositoryListEvent.add(earlierVersion = list, repository = bookmark, isPrivate = false, signer = signer)
        }
    }

    /** Removes [note]'s address from the public list, leaving the private section intact. */
    suspend fun removeRepository(note: AddressableNote): GitRepositoryListEvent? {
        val list = getList() ?: return null
        val idTag = AddressBookmark(note.address, note.relayHintUrl()).toTagIdOnly()
        val newTags = list.tags.remove(idTag)
        if (newTags.size == list.tags.size) return null
        return GitRepositoryListEvent.resign(content = list.content, tags = newTags, signer = signer)
    }
}
