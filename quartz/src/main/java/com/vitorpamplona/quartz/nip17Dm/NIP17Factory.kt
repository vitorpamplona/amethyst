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
package com.vitorpamplona.quartz.nip17Dm

import com.vitorpamplona.quartz.nip01Core.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrlTag
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent

class NIP17Factory {
    data class Result(
        val msg: Event,
        val wraps: List<GiftWrapEvent>,
    )

    private fun recursiveGiftWrapCreation(
        event: Event,
        remainingTos: List<HexKey>,
        signer: NostrSigner,
        output: MutableList<GiftWrapEvent>,
        onReady: (List<GiftWrapEvent>) -> Unit,
    ) {
        if (remainingTos.isEmpty()) {
            onReady(output)
            return
        }

        val next = remainingTos.first()

        SealedRumorEvent.create(
            event = event,
            encryptTo = next,
            signer = signer,
        ) { seal ->
            GiftWrapEvent.create(
                event = seal,
                recipientPubKey = next,
            ) { giftWrap ->
                output.add(giftWrap)
                recursiveGiftWrapCreation(event, remainingTos.minus(next), signer, output, onReady)
            }
        }
    }

    private fun createWraps(
        event: Event,
        to: Set<HexKey>,
        signer: NostrSigner,
        onReady: (List<GiftWrapEvent>) -> Unit,
    ) {
        val wraps = mutableListOf<GiftWrapEvent>()
        recursiveGiftWrapCreation(event, to.toList(), signer, wraps, onReady)
    }

    fun createMessageNIP17(
        template: EventTemplate<ChatMessageEvent>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        signer.sign(template) { senderMessage ->
            createWraps(senderMessage, senderMessage.groupMembers(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = wraps,
                    ),
                )
            }
        }
    }

    fun createEncryptedFileNIP17(
        template: EventTemplate<ChatMessageEncryptedFileHeaderEvent>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        signer.sign(template) { senderMessage ->
            createWraps(senderMessage, senderMessage.groupMembers(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = wraps,
                    ),
                )
            }
        }
    }

    fun createReactionWithinGroup(
        content: String,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        signer.sign(
            ReactionEvent.build(content, originalNote),
        ) { senderReaction ->
            createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderReaction,
                        wraps = wraps,
                    ),
                )
            }
        }
    }

    fun createReactionWithinGroup(
        emojiUrl: EmojiUrlTag,
        originalNote: EventHintBundle<Event>,
        to: List<HexKey>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        signer.sign(
            ReactionEvent.build(emojiUrl, originalNote),
        ) { senderReaction ->
            createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderReaction,
                        wraps = wraps,
                    ),
                )
            }
        }
    }
}
