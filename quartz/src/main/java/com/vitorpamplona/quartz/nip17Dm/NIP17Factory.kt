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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.utils.mapNotNullAsync

class NIP17Factory {
    data class Result(
        val msg: Event,
        val wraps: List<GiftWrapEvent>,
    )

    private suspend fun createWraps(
        event: Event,
        to: Set<HexKey>,
        signer: NostrSigner,
    ): List<GiftWrapEvent> =
        mapNotNullAsync(
            to.toList(),
        ) { next ->
            GiftWrapEvent.create(
                event =
                    SealedRumorEvent.create(
                        event = event,
                        encryptTo = next,
                        signer = signer,
                    ),
                recipientPubKey = next,
            )
        }

    suspend fun createMessageNIP17(
        template: EventTemplate<ChatMessageEvent>,
        signer: NostrSigner,
    ): Result {
        val senderMessage = signer.sign(template)
        val wraps = createWraps(senderMessage, senderMessage.groupMembers(), signer)
        return Result(
            msg = senderMessage,
            wraps = wraps,
        )
    }

    suspend fun createEncryptedFileNIP17(
        template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>,
        signer: NostrSigner,
    ): Result {
        val senderMessage = signer.sign(template)
        val wraps = createWraps(senderMessage, senderMessage.groupMembers(), signer)

        return Result(
            msg = senderMessage,
            wraps = wraps,
        )
    }

    suspend fun createReactionWithinGroup(
        content: String,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
    ): Result {
        val senderPublicKey = signer.pubKey
        val template = ReactionEvent.build(content, originalNote)

        val senderReaction = signer.sign(template)
        val wraps = createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer)
        return Result(
            msg = senderReaction,
            wraps = wraps,
        )
    }

    suspend fun createReactionWithinGroup(
        emojiUrl: EmojiUrlTag,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
    ): Result {
        val senderPublicKey = signer.pubKey
        val template = ReactionEvent.build(emojiUrl, originalNote)

        val senderReaction = signer.sign(template)
        val wraps = createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer)

        return Result(
            msg = senderReaction,
            wraps = wraps,
        )
    }
}
