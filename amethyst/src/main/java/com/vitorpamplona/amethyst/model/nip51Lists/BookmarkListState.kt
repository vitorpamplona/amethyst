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
package com.vitorpamplona.amethyst.model.nip51Lists

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class BookmarkListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    class BookmarkList(
        val public: List<Note> = emptyList(),
        val private: List<Note> = emptyList(),
    )

    fun getBookmarkListAddress() = BookmarkListEvent.createBookmarkAddress(signer.pubKey)

    fun getBookmarkListNote() = cache.getOrCreateAddressableNote(getBookmarkListAddress())

    fun getBookmarkListFlow(): StateFlow<NoteState> = getBookmarkListNote().flow().metadata.stateFlow

    fun getBookmarkList(): BookmarkListEvent? = getBookmarkListNote().event as? BookmarkListEvent

    suspend fun publicBookmarks(note: Note): List<BookmarkIdTag> {
        val noteEvent = note.event as? BookmarkListEvent
        return noteEvent?.publicBookmarks() ?: emptyList()
    }

    suspend fun privateBookmarks(note: Note): List<BookmarkIdTag> {
        val noteEvent = note.event as? BookmarkListEvent
        return noteEvent?.privateBookmarks(signer) ?: emptyList()
    }

    @OptIn(FlowPreview::class)
    val publicBookmarks: StateFlow<List<BookmarkIdTag>> =
        getBookmarkListFlow()
            .map { noteState ->
                publicBookmarks(noteState.note)
            }.onStart {
                emit(publicBookmarks(getBookmarkListNote()))
            }.debounce(100)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    @OptIn(FlowPreview::class)
    val privateBookmarks: StateFlow<List<BookmarkIdTag>> =
        getBookmarkListFlow()
            .map { noteState ->
                privateBookmarks(noteState.note)
            }.onStart {
                emit(privateBookmarks(getBookmarkListNote()))
            }.debounce(100)
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    val publicBookmarkEventIdSet =
        publicBookmarks
            .map { bookmark ->
                bookmark
                    .mapNotNull {
                        if (it is EventBookmark) it.eventId else null
                    }.toSet()
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    val publicBookmarkAddressIdSet =
        publicBookmarks
            .map { bookmark ->
                bookmark
                    .mapNotNull {
                        if (it is AddressBookmark) it.address else null
                    }.toSet()
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    val privateBookmarkEventIdSet =
        privateBookmarks
            .map { bookmark ->
                bookmark
                    .mapNotNull {
                        if (it is EventBookmark) it.eventId else null
                    }.toSet()
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    val privateBookmarkAddressIdSet =
        privateBookmarks
            .map { bookmark ->
                bookmark
                    .mapNotNull {
                        if (it is AddressBookmark) it.address else null
                    }.toSet()
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    fun bookmarkList(
        privateBookmarks: List<BookmarkIdTag>,
        publicBookmarks: List<BookmarkIdTag>,
    ): BookmarkList =
        BookmarkList(
            public =
                publicBookmarks
                    .mapNotNull {
                        when (it) {
                            is EventBookmark -> cache.checkGetOrCreateNote(it.eventId)
                            is AddressBookmark -> cache.getOrCreateAddressableNote(it.address)
                        }
                    }.reversed(),
            private =
                privateBookmarks
                    .mapNotNull {
                        when (it) {
                            is EventBookmark -> cache.checkGetOrCreateNote(it.eventId)
                            is AddressBookmark -> cache.getOrCreateAddressableNote(it.address)
                        }
                    }.reversed(),
        )

    @OptIn(FlowPreview::class)
    val bookmarks: StateFlow<BookmarkList> =
        combineTransform(privateBookmarks, publicBookmarks) { private, public ->
            emit(bookmarkList(private, public))
        }.onStart {
            emit(bookmarkList(privateBookmarks.value, publicBookmarks.value))
        }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                BookmarkList(),
            )

    fun isInPrivateBookmarks(note: Note): Boolean {
        if (!signer.isWriteable()) return false

        return if (note is AddressableNote) {
            privateBookmarkAddressIdSet.value.contains(note.address)
        } else {
            privateBookmarkEventIdSet.value.contains(note.idHex)
        }
    }

    fun isInPublicBookmarks(note: Note): Boolean =
        if (note is AddressableNote) {
            publicBookmarkAddressIdSet.value.contains(note.address)
        } else {
            publicBookmarkEventIdSet.value.contains(note.idHex)
        }

    suspend fun addBookmark(
        note: Note,
        isPrivate: Boolean,
    ): BookmarkListEvent {
        val bookmarkList = getBookmarkList()

        return if (bookmarkList == null) {
            if (note is AddressableNote) {
                BookmarkListEvent.create(
                    bookmarkIdTag = AddressBookmark(note.address, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            } else {
                BookmarkListEvent.create(
                    bookmarkIdTag = EventBookmark(note.idHex, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            }
        } else {
            if (note is AddressableNote) {
                BookmarkListEvent.add(
                    earlierVersion = bookmarkList,
                    bookmarkIdTag = AddressBookmark(note.address, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            } else {
                BookmarkListEvent.add(
                    earlierVersion = bookmarkList,
                    bookmarkIdTag = EventBookmark(note.idHex, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            }
        }
    }

    suspend fun removeBookmark(
        note: Note,
        isPrivate: Boolean,
    ): BookmarkListEvent? {
        val bookmarkList = getBookmarkList()

        return if (bookmarkList != null) {
            if (note is AddressableNote) {
                BookmarkListEvent.remove(
                    earlierVersion = bookmarkList,
                    bookmarkIdTag = AddressBookmark(note.address, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            } else {
                BookmarkListEvent.remove(
                    earlierVersion = bookmarkList,
                    bookmarkIdTag = EventBookmark(note.idHex, note.relayHintUrl()),
                    isPrivate = isPrivate,
                    signer = signer,
                )
            }
        } else {
            null
        }
    }
}
