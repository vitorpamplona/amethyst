/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.model.nip30CustomEmojis

import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.addressables.taggedAddresses
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.taggedEmojis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class EmojiPackState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    class EmojiMedia(
        val code: String,
        val link: MediaUrlImage,
    )

    fun getEmojiPackSelectionAddress() = EmojiPackSelectionEvent.createAddress(signer.pubKey)

    fun getEmojiPackSelection(): EmojiPackSelectionEvent? = getEmojiPackSelectionNote().event as? EmojiPackSelectionEvent

    fun getEmojiPackSelectionFlow(): StateFlow<NoteState> = getEmojiPackSelectionNote().flow().metadata.stateFlow

    fun getEmojiPackSelectionNote(): AddressableNote = cache.getOrCreateAddressableNote(getEmojiPackSelectionAddress())

    fun convertEmojiSelectionPack(selection: EmojiPackSelectionEvent?): List<StateFlow<NoteState>>? =
        selection?.taggedAddresses()?.map {
            cache
                .getOrCreateAddressableNote(it)
                .flow()
                .metadata.stateFlow
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveEmojiSelectionPack: StateFlow<List<StateFlow<NoteState>>?> by lazy {
        getEmojiPackSelectionFlow()
            .transformLatest {
                emit(convertEmojiSelectionPack(it.note.event as? EmojiPackSelectionEvent))
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                convertEmojiSelectionPack(getEmojiPackSelection()),
            )
    }

    fun convertEmojiPack(pack: EmojiPackEvent): List<EmojiMedia> =
        pack.taggedEmojis().map {
            EmojiMedia(it.code, MediaUrlImage(it.url))
        }

    fun mergePack(list: Array<NoteState>): List<EmojiMedia> =
        list
            .mapNotNull {
                val ev = it.note.event as? EmojiPackEvent
                if (ev != null) {
                    convertEmojiPack(ev)
                } else {
                    null
                }
            }.flatten()
            .distinctBy { it.link }

    @OptIn(ExperimentalCoroutinesApi::class)
    val myEmojis by lazy {
        liveEmojiSelectionPack
            .transformLatest { emojiList ->
                if (emojiList != null) {
                    emitAll(
                        combineTransform(emojiList) {
                            emit(mergePack(it))
                        },
                    )
                } else {
                    emit(emptyList())
                }
            }.flowOn(Dispatchers.Default)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                mergePack(convertEmojiSelectionPack(getEmojiPackSelection())?.map { it.value }?.toTypedArray() ?: emptyArray()),
            )
    }
}
