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

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PeopleListEvent
import com.vitorpamplona.quartz.utils.tryAndWait
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.resume

class BlockPeopleListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    fun getBlockListAddress() = PeopleListEvent.createBlockAddress(signer.pubKey)

    fun getBlockListNote() = LocalCache.getOrCreateAddressableNote(getBlockListAddress())

    fun getBlockListFlow(): StateFlow<NoteState> = getBlockListNote().flow().metadata.stateFlow

    fun getBlockList(): PeopleListEvent? = getBlockListNote().event as? PeopleListEvent

    suspend fun blockListWithBackup(note: Note): PeopleListEvent.UsersAndWords =
        blockList(
            note.event as? PeopleListEvent,
        )

    suspend fun blockList(event: PeopleListEvent?): PeopleListEvent.UsersAndWords =
        tryAndWait { continuation ->
            event?.publicAndPrivateUsersAndWords(signer) {
                continuation.resume(it)
            }
        } ?: PeopleListEvent.UsersAndWords()

    val flow =
        getBlockListFlow()
            .map { blockListWithBackup(it.note) }
            .onStart { emit(blockListWithBackup(getBlockListNote())) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                PeopleListEvent.UsersAndWords(),
            )

    fun hideUser(
        pubkeyHex: String,
        onDone: (PeopleListEvent) -> Unit,
    ) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.addUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        } else {
            PeopleListEvent.createListWithUser(
                name = PeopleListEvent.BLOCK_LIST_D_TAG,
                pubKeyHex = pubkeyHex,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun showUser(
        pubkeyHex: String,
        onDone: (PeopleListEvent) -> Unit,
    ) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeUser(
                earlierVersion = blockList,
                pubKeyHex = pubkeyHex,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun hideWord(
        word: String,
        onDone: (PeopleListEvent) -> Unit,
    ) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.addWord(
                earlierVersion = blockList,
                word = word,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        } else {
            PeopleListEvent.createListWithWord(
                name = PeopleListEvent.BLOCK_LIST_D_TAG,
                word = word,
                isPrivate = true,
                signer = signer,
                onReady = onDone,
            )
        }
    }

    fun showWord(
        word: String,
        onDone: (PeopleListEvent) -> Unit,
    ) {
        val blockList = getBlockList()

        if (blockList != null) {
            PeopleListEvent.removeWord(
                earlierVersion = blockList,
                word = word,
                signer = signer,
                onReady = onDone,
            )
        }
    }
}
