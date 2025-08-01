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
package com.vitorpamplona.quartz.nip37Drafts

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonParseException
import com.vitorpamplona.quartz.experimental.interactiveStories.InteractiveStoryBaseEvent
import com.vitorpamplona.quartz.experimental.zapPolls.PollNoteEvent
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import com.vitorpamplona.quartz.nip35Torrents.TorrentCommentEvent
import com.vitorpamplona.quartz.nip53LiveActivities.chat.LiveActivitiesChatMessageEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class DraftEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    AddressHintProvider,
    PubKeyHintProvider {
    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun eventHints() = tags.mapNotNull(ETag::parseAsHint)

    override fun linkedEventIds() = tags.mapNotNull(ETag::parseId)

    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    override fun isContentEncoded() = true

    fun isDeleted() = content == ""

    fun canDecrypt(signer: NostrSigner) = signer.pubKey == pubKey

    suspend fun createDeletedEvent(signer: NostrSigner): DraftEvent = signer.sign(createdAt, KIND, tags, "")

    suspend fun decryptInnerEvent(signer: NostrSigner): Event {
        if (!canDecrypt(signer)) throw SignerExceptions.UnauthorizedDecryptionException()

        val json = signer.nip44Decrypt(content, pubKey)
        return try {
            fromJson(json)
        } catch (e: JsonParseException) {
            Log.w("DraftEvent", "Unable to parse inner event of a draft: $json")
            throw e
        }
    }

    companion object {
        const val KIND = 31234
        const val ALT_DESCRIPTION = "Draft Event"

        fun createAddressTag(
            pubKey: HexKey,
            dTag: String,
        ): String = Address.assemble(KIND, pubKey, dTag)

        @Suppress("DEPRECATION")
        suspend fun create(
            dTag: String,
            originalNote: TorrentCommentEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tagsWithMarkers =
                originalNote.tags.filter {
                    it.size > 3 && (it[0] == "e" || it[0] == "a") && (it[3] == "root" || it[3] == "reply")
                }

            return create(dTag, originalNote, tagsWithMarkers, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: InteractiveStoryBaseEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tags = mutableListOf<Array<String>>()
            return create(dTag, originalNote, tags, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: LiveActivitiesChatMessageEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tags = mutableListOf<Array<String>>()
            originalNote.activity()?.let { tags.add(arrayOf("a", it.toTag(), "", "root")) }
            originalNote.replyingTo()?.let { tags.add(arrayOf("e", it, "", "reply")) }

            return create(dTag, originalNote, tags, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: ChannelMessageEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tags = mutableListOf<Array<String>>()
            originalNote.channelId()?.let { tags.add(arrayOf("e", it)) }

            return create(dTag, originalNote, tags, signer, createdAt)
        }

        @Suppress("DEPRECATION")
        suspend fun create(
            dTag: String,
            originalNote: GitReplyEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tags = mutableListOf<Array<String>>()
            originalNote.repository()?.let { tags.add(arrayOf("a", it.toTag())) }
            originalNote.replyingTo()?.let { tags.add(arrayOf("e", it)) }

            return create(dTag, originalNote, tags, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: PollNoteEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tagsWithMarkers =
                originalNote.tags.filter {
                    it.size > 3 && (it[0] == "e" || it[0] == "a") && (it[3] == "root" || it[3] == "reply")
                }

            return create(dTag, originalNote, tagsWithMarkers, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: CommentEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tagsWithMarkers = originalNote.rootScopes() + originalNote.directReplies()

            return create(dTag, originalNote, tagsWithMarkers, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            originalNote: TextNoteEvent,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tagsWithMarkers =
                originalNote.tags.filter {
                    it.size > 3 && (it[0] == "e" || it[0] == "a") && (it[3] == "root" || it[3] == "reply")
                }

            return create(dTag, originalNote, tagsWithMarkers, signer, createdAt)
        }

        suspend fun create(
            dTag: String,
            innerEvent: Event,
            anchorTagArray: List<Array<String>> = emptyList(),
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): DraftEvent {
            val tags = mutableListOf<Array<String>>()
            tags.add(arrayOf("d", dTag))
            tags.add(arrayOf("k", "${innerEvent.kind}"))

            if (anchorTagArray.isNotEmpty()) {
                tags.addAll(anchorTagArray)
            }

            val draft =
                signer.sign<DraftEvent>(
                    createdAt = createdAt,
                    kind = KIND,
                    tags = tags.toTypedArray(),
                    content = signer.nip44Encrypt(innerEvent.toJson(), signer.pubKey),
                )

            return draft
        }
    }
}
