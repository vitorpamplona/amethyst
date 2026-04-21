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
package com.vitorpamplona.amethyst.commons.model.nip30CustomEmojis

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.taggedAddresses
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.selection.EmojiPackSelectionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

class EmojiPackState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    class EmojiMedia(
        val code: String,
        val link: String,
    )

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val emojiPackListNote = cache.getOrCreateAddressableNote(getEmojiPackSelectionAddress())

    fun getEmojiPackSelectionAddress() = EmojiPackSelectionEvent.createAddress(signer.pubKey)

    fun getEmojiPackSelection(): EmojiPackSelectionEvent? = emojiPackListNote.event as? EmojiPackSelectionEvent

    fun getEmojiPackSelectionFlow(): StateFlow<NoteState> = emojiPackListNote.flow().metadata.stateFlow

    fun convertEmojiSelectionPack(selection: EmojiPackSelectionEvent?): List<StateFlow<NoteState>>? =
        selection?.taggedAddresses()?.map {
            cache
                .getOrCreateAddressableNote(it)
                .flow()
                .metadata.stateFlow
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<List<StateFlow<NoteState>>?> =
        getEmojiPackSelectionFlow()
            .transformLatest {
                emit(convertEmojiSelectionPack(it.note.event as? EmojiPackSelectionEvent))
            }.onStart {
                emit(convertEmojiSelectionPack(getEmojiPackSelection()))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    fun convertEmojiPack(pack: EmojiPackEvent): List<EmojiMedia> = pack.publicEmojis().toEmojiMedia()

    // Decrypts private (NIP-51) emojis when this signer authored the pack so they
    // surface alongside public ones in the `:` autocomplete. Foreign packs return
    // public-only because privateTags refuses to decrypt for non-authors.
    suspend fun convertEmojiPackWithPrivate(pack: EmojiPackEvent): List<EmojiMedia> = pack.allEmojis(signer).toEmojiMedia()

    private fun List<EmojiUrlTag>.toEmojiMedia() = map { EmojiMedia(it.code, it.url) }

    suspend fun mergePackWithPrivate(list: Array<NoteState>): List<EmojiMedia> =
        list
            .mapNotNull { it.note.event as? EmojiPackEvent }
            .flatMap { convertEmojiPackWithPrivate(it) }
            .distinctBy { it.link }

    @OptIn(ExperimentalCoroutinesApi::class)
    val myEmojis =
        flow
            .transformLatest { emojiList ->
                if (emojiList != null) {
                    emitAll(
                        combineTransform(emojiList) {
                            emit(mergePackWithPrivate(it))
                        },
                    )
                } else {
                    emit(emptyList())
                }
            }.onStart {
                emit(
                    mergePackWithPrivate(
                        convertEmojiSelectionPack(
                            getEmojiPackSelection(),
                        )?.map { it.value }?.toTypedArray() ?: emptyArray(),
                    ),
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun addEmojiPack(emojiPack: Note): EmojiPackSelectionEvent {
        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) throw IllegalArgumentException("Note is not an EmojiPackEvent; cannot add to emoji list.")

        val eventHint = emojiPack.toEventHint<EmojiPackEvent>() ?: throw IllegalArgumentException("Cannot build event hint for this emoji pack.")

        val usersEmojiList = getEmojiPackSelection()
        return if (usersEmojiList == null) {
            val template = EmojiPackSelectionEvent.build(listOf(eventHint))
            signer.sign(template)
        } else {
            val template = EmojiPackSelectionEvent.add(usersEmojiList, eventHint)
            signer.sign(template)
        }
    }

    suspend fun removeEmojiPack(emojiPack: Note): EmojiPackSelectionEvent? {
        val usersEmojiList = getEmojiPackSelection() ?: throw IllegalArgumentException("No emoji pack selection exists to remove from.")

        val emojiPackEvent = emojiPack.event
        if (emojiPackEvent !is EmojiPackEvent) return null

        val template = EmojiPackSelectionEvent.remove(usersEmojiList, emojiPackEvent)
        return signer.sign(template)
    }
}
