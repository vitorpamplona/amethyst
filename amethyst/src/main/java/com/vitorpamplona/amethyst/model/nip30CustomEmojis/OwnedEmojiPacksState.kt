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
package com.vitorpamplona.amethyst.model.nip30CustomEmojis

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
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip30CustomEmoji.emoji
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.EmojiPackEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.description
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.image
import com.vitorpamplona.quartz.nip30CustomEmoji.pack.title
import com.vitorpamplona.quartz.nip51Lists.encryption.PrivateTagsInContent
import com.vitorpamplona.quartz.nip51Lists.remove
import com.vitorpamplona.quartz.nip51Lists.tags.DescriptionTag
import com.vitorpamplona.quartz.nip51Lists.tags.ImageTag
import com.vitorpamplona.quartz.nip51Lists.tags.NameTag
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
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

class OwnedEmojiPacksState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingOwnedEmojiPackNotes() = cache.addressables.filter(EmojiPackEvent.KIND, user.pubkeyHex)

    val ownedEmojiPackVersions = MutableStateFlow(0)

    val ownedEmojiPackNotes =
        ownedEmojiPackVersions
            .map { existingOwnedEmojiPackNotes() }
            .onStart { emit(existingOwnedEmojiPackNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val ownedEmojiPackEventIds =
        ownedEmojiPackNotes
            .map { it.eventIdSet() }
            .onStart { emit(ownedEmojiPackNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestEmojiPacks: StateFlow<List<EmojiPackEvent>> =
        ownedEmojiPackNotes
            .transformLatest { emitAll(it.updateFlow<EmojiPackEvent>()) }
            .onStart { emit(ownedEmojiPackNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun EmojiPackEvent.toOwnedEmojiPack(): OwnedEmojiPack =
        OwnedEmojiPack(
            identifier = dTag(),
            title = titleOrName() ?: dTag(),
            description = description(),
            image = image(),
            publicEmojis = publicEmojis(),
            privateEmojis = privateEmojis(signer) ?: emptyList(),
        )

    suspend fun List<EmojiPackEvent>.toOwnedEmojiPackFeed() = map { it.toOwnedEmojiPack() }.sortedBy { it.title }

    val listFeedFlow =
        latestEmojiPacks
            .map { it.toOwnedEmojiPackFeed() }
            .onStart { emit(latestEmojiPacks.value.toOwnedEmojiPackFeed()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<OwnedEmojiPack>.getPack(packDTag: String) =
        this.firstOrNull {
            it.identifier == packDTag
        }

    fun getPack(dTag: String) = listFeedFlow.value.getPack(dTag)

    fun getOwnedEmojiPackNote(dTag: String): AddressableNote? = existingOwnedEmojiPackNotes().find { it.dTag() == dTag }

    fun getOwnedEmojiPackEvent(dTag: String): EmojiPackEvent? = getOwnedEmojiPackNote(dTag)?.event as? EmojiPackEvent

    fun getOwnedEmojiPackFlow(dTag: String) =
        listFeedFlow
            .map { it.getPack(dTag) }
            .onStart { emit(listFeedFlow.value.getPack(dTag)) }
            .flowOn(Dispatchers.IO)

    fun DeletionEvent.hasAnyDeletedOwnedEmojiPacks() = deleteAddressesWithKind(EmojiPackEvent.KIND) || deletesAnyEventIn(ownedEmojiPackEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is EmojiPackEvent || (event is DeletionEvent && event.hasAnyDeletedOwnedEmojiPacks())
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
        ownedEmojiPackVersions.update { it + 1 }
    }

    suspend fun createPack(
        title: String,
        description: String? = null,
        image: String? = null,
        account: Account,
    ) {
        val template =
            EmojiPackEvent.build(name = title) {
                if (!description.isNullOrBlank()) description(description)
                if (!image.isNullOrBlank()) image(image)
            }
        val newPack = signer.sign(template)
        account.sendMyPublicAndPrivateOutbox(newPack)
    }

    suspend fun updateMetadata(
        dTag: String,
        newTitle: String,
        newDescription: String?,
        newImage: String?,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return

        val template =
            packEvent.update<EmojiPackEvent> {
                remove(NameTag.TAG_NAME)
                remove(TitleTag.TAG_NAME)
                remove(DescriptionTag.TAG_NAME)
                remove(ImageTag.TAG_NAME)
                title(newTitle)
                if (!newDescription.isNullOrBlank()) description(newDescription)
                if (!newImage.isNullOrBlank()) image(newImage)
            }

        val signed = signer.sign(template)
        account.sendMyPublicAndPrivateOutbox(signed)
    }

    suspend fun addEmoji(
        dTag: String,
        emojiTag: EmojiUrlTag,
        isPrivate: Boolean,
        account: Account,
    ) {
        if (!EmojiUrlTag.isValidShortcode(emojiTag.code)) {
            throw IllegalArgumentException("Invalid emoji shortcode: ${emojiTag.code}")
        }

        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return

        val signed: EmojiPackEvent =
            if (isPrivate) {
                val privateTags = packEvent.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                val newPrivateTags: Array<Array<String>> = privateTags.plus<Array<String>>(emojiTag.toTagArray())
                val newContent = PrivateTagsInContent.encryptNip44(newPrivateTags, signer)
                signer.sign(
                    com.vitorpamplona.quartz.utils.TimeUtils
                        .now(),
                    packEvent.kind,
                    packEvent.tags,
                    newContent,
                )
            } else {
                val template =
                    packEvent.update<EmojiPackEvent> {
                        emoji(emojiTag)
                    }
                signer.sign(template)
            }

        account.sendMyPublicAndPrivateOutbox(signed)
    }

    suspend fun removeEmoji(
        dTag: String,
        shortcode: String,
        isPrivate: Boolean,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return

        val signed: EmojiPackEvent =
            if (isPrivate) {
                val privateTags = packEvent.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
                val newPrivateTags = privateTags.remove { it[0] == EmojiUrlTag.TAG_NAME && it.getOrNull(1) == shortcode }
                if (newPrivateTags.size == privateTags.size) return
                val newContent = PrivateTagsInContent.encryptNip44(newPrivateTags, signer)
                signer.sign(
                    com.vitorpamplona.quartz.utils.TimeUtils
                        .now(),
                    packEvent.kind,
                    packEvent.tags,
                    newContent,
                )
            } else {
                val newPublicTags = packEvent.tags.remove { it[0] == EmojiUrlTag.TAG_NAME && it.getOrNull(1) == shortcode }
                if (newPublicTags.size == packEvent.tags.size) return
                signer.sign(
                    com.vitorpamplona.quartz.utils.TimeUtils
                        .now(),
                    packEvent.kind,
                    newPublicTags,
                    packEvent.content,
                )
            }

        account.sendMyPublicAndPrivateOutbox(signed)
    }

    suspend fun deletePack(
        dTag: String,
        account: Account,
    ) {
        val packEvent = getOwnedEmojiPackEvent(dTag) ?: return
        val deletionEventTemplate = DeletionEvent.build(listOf(packEvent))
        val deletionEvent = signer.sign(deletionEventTemplate)
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }
}
