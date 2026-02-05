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
package com.vitorpamplona.amethyst.model.nip51Lists.labeledBookmarkLists

import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.description
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.image
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.name
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

class LabeledBookmarkListsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingLabeledBookmarkNotes() = cache.addressables.filter(LabeledBookmarkListEvent.KIND, user.pubkeyHex)

    val labeledBookmarkListVersions = MutableStateFlow(0)

    val labeledBookmarkListNotes =
        labeledBookmarkListVersions
            .map { existingLabeledBookmarkNotes() }
            .onStart { emit(existingLabeledBookmarkNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val labeledBookmarkListEventIds =
        labeledBookmarkListNotes
            .map { it.eventIdSet() }
            .onStart { emit(labeledBookmarkListNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestBookmarkLists: StateFlow<List<LabeledBookmarkListEvent>> =
        labeledBookmarkListNotes
            .transformLatest { emitAll(it.updateFlow<LabeledBookmarkListEvent>()) }
            .onStart { emit(labeledBookmarkListNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun LabeledBookmarkListEvent.toLabeledBookmarkList() =
        LabeledBookmarkList(
            identifier = dTag(),
            title = titleOrName() ?: dTag(),
            description = description(),
            image = image(),
            privateBookmarks = privateBookmarks(signer)?.toSet() ?: emptySet(),
            publicBookmarks = publicBookmarks().toSet(),
        )

    suspend fun List<LabeledBookmarkListEvent>.toLabeledBookmarkListsFeed() = map { it.toLabeledBookmarkList() }.sortedBy { it.title }

    val listFeedFlow =
        latestBookmarkLists
            .map { it.toLabeledBookmarkListsFeed() }
            .onStart { emit(latestBookmarkLists.value.toLabeledBookmarkListsFeed()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<LabeledBookmarkList>.getList(bookmarkListId: String) =
        this.firstOrNull {
            it.identifier == bookmarkListId
        }

    fun getBookmarkList(dTag: String) = listFeedFlow.value.getList(bookmarkListId = dTag)

    fun DeletionEvent.hasAnyDeletedBookmarkLists() = deleteAddressesWithKind(LabeledBookmarkListEvent.KIND) || deletesAnyEventIn(labeledBookmarkListEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is LabeledBookmarkListEvent || (event is DeletionEvent && event.hasAnyDeletedBookmarkLists())
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
        labeledBookmarkListVersions.update { it + 1 }
    }

    fun getLabeledBookmarkListNote(bookmarkIdentifier: String): AddressableNote? = existingLabeledBookmarkNotes().find { it.dTag() == bookmarkIdentifier }

    fun getLabeledBookmarkListEvent(bookmarkIdentifier: String): LabeledBookmarkListEvent = getLabeledBookmarkListNote(bookmarkIdentifier)?.event as LabeledBookmarkListEvent

    fun getLabeledBookmarkListFlow(bookmarkIdentifier: String) =
        listFeedFlow
            .map { it.getList(bookmarkIdentifier) }
            .onStart {
                emit(
                    listFeedFlow.value.getList(bookmarkIdentifier),
                )
            }.flowOn(Dispatchers.IO)

    suspend fun addLabeledBookmarkList(
        listName: String,
        listDescription: String? = null,
        listImage: String? = null,
        firstBookmark: BookmarkIdTag? = null,
        isBookmarkPrivate: Boolean = false,
        account: Account,
    ) {
        val newList =
            LabeledBookmarkListEvent.create(
                title = listName,
                description = listDescription,
                image = listImage,
                publicBookmarks = if (!isBookmarkPrivate && firstBookmark != null) listOf(firstBookmark) else emptyList(),
                privateBookmarks = if (isBookmarkPrivate && firstBookmark != null) listOf(firstBookmark) else emptyList(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun updateMetadata(
        listName: String?,
        listDescription: String?,
        listImage: String?,
        bookmarkList: LabeledBookmarkList,
        account: Account,
    ) {
        val listEvent = getLabeledBookmarkListEvent(bookmarkList.identifier)

        val template =
            listEvent.update {
                if (listName != null) name(listName)
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun renameBookmarkList(
        newName: String,
        bookmarkList: LabeledBookmarkList,
        account: Account,
    ) {
        val listEvent = getLabeledBookmarkListEvent(bookmarkList.identifier)
        val renamedList =
            LabeledBookmarkListEvent.modifyName(
                earlierVersion = listEvent,
                newTitle = newName,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(renamedList)
    }

    suspend fun modifyListDescription(
        newDescription: String?,
        bookmarkList: LabeledBookmarkList,
        account: Account,
    ) {
        val listEvent = getLabeledBookmarkListEvent(bookmarkList.identifier)
        val modifiedList =
            LabeledBookmarkListEvent.modifyDescription(
                earlierVersion = listEvent,
                newDescription = newDescription,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(modifiedList)
    }

    suspend fun cloneBookmarkList(
        currentBookmarkList: LabeledBookmarkList,
        customCloneName: String?,
        customCloneDescription: String?,
        account: Account,
    ) {
        val clonedList =
            LabeledBookmarkListEvent.create(
                title = customCloneName ?: currentBookmarkList.title,
                description = customCloneDescription ?: currentBookmarkList.description,
                publicBookmarks = currentBookmarkList.publicBookmarks.toList(),
                privateBookmarks = currentBookmarkList.privateBookmarks.toList(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(clonedList)
    }

    suspend fun deleteBookmarkList(
        bookmarkListIdentifier: String,
        account: Account,
    ) {
        val listEvent = getLabeledBookmarkListEvent(bookmarkListIdentifier)
        val deletionEventTemplate = DeletionEvent.build(listOf(listEvent))
        val deletionEvent = account.signer.sign(deletionEventTemplate)
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }

    suspend fun addBookmarkToList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkPrivate: Boolean,
        account: Account,
    ) {
        val currentBookmarkList = getLabeledBookmarkListEvent(bookmarkListIdentifier)
        val updatedList =
            LabeledBookmarkListEvent.addBookmark(
                earlierVersion = currentBookmarkList,
                bookmarkIdTag = bookmark,
                isPrivate = isBookmarkPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }

    suspend fun moveBookmarkInList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkCurrentlyPrivate: Boolean,
        account: Account,
    ) {
        val bookmarkList = getLabeledBookmarkListEvent(bookmarkListIdentifier)
        val updatedList =
            LabeledBookmarkListEvent.moveBookmark(
                earlierVersion = bookmarkList,
                bookmarkIdTag = bookmark,
                isCurrentlyPrivate = isBookmarkCurrentlyPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }

    suspend fun removeBookmarkFromList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkPrivate: Boolean,
        account: Account,
    ) {
        val currentBookmarkList = getLabeledBookmarkListEvent(bookmarkListIdentifier)
        val updatedList =
            LabeledBookmarkListEvent.removeBookmark(
                earlierVersion = currentBookmarkList,
                bookmarkIdTag = bookmark,
                isPrivate = isBookmarkPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }
}
