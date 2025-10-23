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
package com.vitorpamplona.amethyst.model.nip51Lists.blockPeopleList

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class BlockPeopleListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: PeopleListDecryptionCache,
    val scope: CoroutineScope,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val blockListNote = cache.getOrCreateAddressableNote(getBlockListAddress())

    fun getBlockListAddress() = PeopleListEvent.createBlockAddress(signer.pubKey)

    fun getBlockListFlow(): StateFlow<NoteState> = blockListNote.flow().metadata.stateFlow

    fun getBlockList(): PeopleListEvent? = blockListNote.event as? PeopleListEvent

    suspend fun blockListWithBackup(note: Note): List<MuteTag> {
        val event = note.event as? PeopleListEvent
        return event?.let { decryptionCache.usersAndWords(it) } ?: emptyList()
    }

    val flow =
        getBlockListFlow()
            .map { blockListWithBackup(it.note) }
            .onStart { emit(blockListWithBackup(blockListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun hideUser(pubkeyHex: String): PeopleListEvent {
        val blockList = getBlockList()

        return if (blockList != null) {
            PeopleListEvent.add(
                earlierVersion = blockList,
                person = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
            )
        } else {
            PeopleListEvent.create(
                name = PeopleListEvent.BLOCK_LIST_D_TAG,
                person = UserTag(pubkeyHex),
                isPrivate = true,
                signer = signer,
                dTag = PeopleListEvent.BLOCK_LIST_D_TAG,
            )
        }
    }

    suspend fun showUser(pubkeyHex: String): PeopleListEvent? {
        val blockList = getBlockList()

        return if (blockList != null) {
            PeopleListEvent.remove(
                earlierVersion = blockList,
                person = UserTag(pubkeyHex),
                signer = signer,
            )
        } else {
            null
        }
    }

    suspend fun showWord(word: String): PeopleListEvent? {
        val blockList = getBlockList()

        return if (blockList != null) {
            PeopleListEvent.remove(
                earlierVersion = blockList,
                person = WordTag(word),
                signer = signer,
            )
        } else {
            null
        }
    }
}
