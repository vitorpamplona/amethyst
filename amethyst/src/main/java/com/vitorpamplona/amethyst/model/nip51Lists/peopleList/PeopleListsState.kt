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

import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.description
import com.vitorpamplona.quartz.nip51Lists.peopleList.image
import com.vitorpamplona.quartz.nip51Lists.peopleList.name
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
import java.util.UUID

/**
 * Maintains several stateflows for each step in processing PeopleLists
 *
 * This class must receive updates from Large Cache as it receives new events.
 */
class PeopleListsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: PeopleListDecryptionCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingPeopleListNotes() = cache.addressables.filter(PeopleListEvent.KIND, user.pubkeyHex)

    val peopleListVersions = MutableStateFlow(0)

    val peopleListNotes =
        peopleListVersions
            .map { existingPeopleListNotes() }
            .onStart { emit(existingPeopleListNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val peopleListsEventIds =
        peopleListNotes
            .map { it.eventIdSet() }
            .onStart { emit(peopleListNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestLists: StateFlow<List<PeopleListEvent>> =
        peopleListNotes
            .transformLatest { emitAll(it.updateFlow<PeopleListEvent>()) }
            .onStart { emit(peopleListNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun PeopleListEvent.userIdSet() = decryptionCache.userIdSet(this)

    suspend fun List<PeopleListEvent>.mapToUserIdSet() = this.map { it.userIdSet() }.flattenToSet()

    suspend fun List<PeopleListEvent>.mapGoodUsersToIdSet() =
        this
            .mapNotNull {
                if (it.dTag() != PeopleListEvent.BLOCK_LIST_D_TAG) {
                    it.userIdSet()
                } else {
                    null
                }
            }.flattenToSet()

    val allGoodPeopleListProfiles: StateFlow<Set<HexKey>> =
        latestLists
            .map { it.mapGoodUsersToIdSet() }
            .onStart { emit(latestLists.value.mapGoodUsersToIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun PeopleListEvent.toUI() =
        PeopleList(
            identifierTag = this.dTag(),
            title = this.nameOrTitle() ?: this.dTag(),
            description = this.description(),
            image = this.image(),
            privateMembers = cache.load(decryptionCache.privateUserIdSet(this)),
            publicMembers = cache.load(this.publicUsersIdSet()),
        )

    suspend fun List<PeopleListEvent>.toUI() = this.map { it.toUI() }.sortedBy { it.title }

    val uiListFlow =
        latestLists
            .map { it.toUI() }
            .onStart { emit(latestLists.value.toUI()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<PeopleList>.select(dTag: String) =
        this.firstOrNull {
            it.identifierTag == dTag
        }

    fun selectList(dTag: String) = uiListFlow.value.select(dTag)

    fun selectListFlow(dTag: String) =
        uiListFlow
            .map { it.select(dTag) }
            .onStart { emit(selectList(dTag)) }

    fun DeletionEvent.hasDeletedAnyPeopleList() = deleteAddressesWithKind(PeopleListEvent.KIND) || deletesAnyEventIn(peopleListsEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is PeopleListEvent || (event is DeletionEvent && event.hasDeletedAnyPeopleList())
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
        peopleListVersions.update { it + 1 }
    }

    // --------------
    // Updating Lists
    // --------------

    fun getPeopleListNote(noteIdentifier: String): AddressableNote? = existingPeopleListNotes().find { it.dTag() == noteIdentifier }

    fun getPeopleList(noteIdentifier: String): PeopleListEvent = getPeopleListNote(noteIdentifier)?.event as PeopleListEvent

    fun User.toUserTag() = UserTag(this.pubkeyHex, this.bestRelayHint())

    fun Set<User>.toUserTags() = map { it.toUserTag() }

    suspend fun addFollowList(
        listName: String,
        listDescription: String?,
        listImage: String?,
        member: User? = null,
        isPrivate: Boolean = false,
        account: Account,
    ): String {
        val dTag = UUID.randomUUID().toString()
        val newListTemplate =
            PeopleListEvent.build(
                dTag = dTag,
                name = listName,
                publicMembers = if (!isPrivate && member != null) listOf(member.toUserTag()) else emptyList(),
                privateMembers = if (isPrivate && member != null) listOf(member.toUserTag()) else emptyList(),
                signer = account.signer,
            ) {
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(newListTemplate)

        account.sendMyPublicAndPrivateOutbox(newList)
        return dTag
    }

    suspend fun updateMetadata(
        listName: String?,
        listDescription: String?,
        listImage: String?,
        peopleList: PeopleList,
        account: Account,
    ) {
        val listEvent = getPeopleList(peopleList.identifierTag)

        val template =
            listEvent.update {
                if (listName != null) name(listName)
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun cloneFollowSet(
        currentPeopleList: PeopleList,
        customCloneName: String?,
        customCloneDescription: String?,
        account: Account,
    ) {
        val newList =
            PeopleListEvent.createListWithDescription(
                dTag = UUID.randomUUID().toString(),
                title = customCloneName ?: currentPeopleList.title,
                description = customCloneDescription ?: currentPeopleList.description,
                publicMembers = currentPeopleList.publicMembers.toUserTags(),
                privateMembers = currentPeopleList.privateMembers.toUserTags(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun deleteFollowSet(
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val deletionEvent = account.signer.sign(DeletionEvent.build(listOf(followListEvent)))
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }

    suspend fun addUserToSet(
        user: User,
        identifierTag: String,
        shouldBePrivateMember: Boolean,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            PeopleListEvent.addUser(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                relayHint = user.bestRelayHint(),
                isPrivate = shouldBePrivateMember,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun addUserFirstToSet(
        user: User,
        identifierTag: String,
        shouldBePrivateMember: Boolean,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            PeopleListEvent.addUserFirst(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                relayHint = user.bestRelayHint(),
                isPrivate = shouldBePrivateMember,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun removeUserFromSet(
        user: User,
        isPrivate: Boolean,
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            PeopleListEvent.removeUser(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                isUserPrivate = isPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }
}
