/**
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
package com.vitorpamplona.amethyst.model.nip51Lists.peopleList

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.anyNotNullEvent
import com.vitorpamplona.amethyst.model.eventIdSet
import com.vitorpamplona.amethyst.model.events
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.amethyst.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.utils.flattenToSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.collections.map

/**
 * Maintains several stateflows for each step in processing PeopleLists
 *
 * This class must receive updates from Large Cache as it receives new events.
 */
class FollowListsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingPeopleListNotes() = cache.addressables.filter(FollowListEvent.Companion.KIND, user.pubkeyHex)

    val followListVersions = MutableStateFlow(0)

    val followListNotes =
        followListVersions
            .map { existingPeopleListNotes() }
            .onStart { emit(existingPeopleListNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Companion.Eagerly, emptyList())

    val followListsEventIds =
        followListNotes
            .map { it.eventIdSet() }
            .onStart { emit(followListNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Companion.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestLists: StateFlow<List<FollowListEvent>> =
        followListNotes
            .transformLatest { emitAll(it.updateFlow<FollowListEvent>()) }
            .onStart { emit(followListNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Companion.Eagerly, emptyList())

    fun List<FollowListEvent>.mapToUserIdSet() = this.map { it.followIdSet() }.flattenToSet()

    val allPeopleListProfiles: StateFlow<Set<HexKey>> =
        latestLists
            .map { it.mapToUserIdSet() }
            .onStart { emit(latestLists.value.mapToUserIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Companion.Eagerly, emptySet())

    fun FollowListEvent.toUI() =
        PeopleList(
            identifierTag = this.dTag(),
            title = this.title() ?: this.dTag(),
            description = this.description(),
            privateMembers = emptySet(),
            publicMembers = cache.load(this.followIdSet()),
        )

    fun List<FollowListEvent>.toUI() = this.map { it.toUI() }

    val uiListFlow =
        latestLists
            .map { it.toUI() }
            .onStart { emit(latestLists.value.toUI()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Companion.Eagerly, emptyList())

    fun isUserInFollowSets(user: User): Boolean = allPeopleListProfiles.value.contains(user.pubkeyHex)

    fun DeletionEvent.hasDeletedAnyFollowList() = deleteAddressesWithKind(FollowListEvent.Companion.KIND) || deletesAnyEventIn(followListsEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is FollowListEvent || (event is DeletionEvent && event.hasDeletedAnyFollowList())
            } else {
                false
            }
        }

    fun newNotes(newNotes: Set<Note>) {
        if (hasItemInNoteList(newNotes)) {
            forceRefresh()
        }
    }

    fun deletedNotes(deletedNotes: Set<Note>) {
        if (hasItemInNoteList(deletedNotes)) {
            forceRefresh()
        }
    }

    fun forceRefresh() {
        followListVersions.update { it + 1 }
    }
}
