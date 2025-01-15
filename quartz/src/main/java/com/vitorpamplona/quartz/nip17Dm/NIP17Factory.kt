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

import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip30CustomEmoji.EmojiUrl
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip59Giftwrap.GiftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.SealedGossipEvent
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip94FileMetadata.Dimension

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

        SealedGossipEvent.create(
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

    fun createMsgNIP17(
        msg: String,
        to: List<HexKey>,
        signer: NostrSigner,
        subject: String? = null,
        replyTos: List<String>? = null,
        mentions: List<String>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        markAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        imetas: List<IMetaTag>? = null,
        emojis: List<EmojiUrl>? = null,
        draftTag: String? = null,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        ChatMessageEvent.create(
            msg = msg,
            to = to,
            signer = signer,
            subject = subject,
            replyTos = replyTos,
            mentions = mentions,
            zapReceiver = zapReceiver,
            markAsSensitive = markAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash,
            isDraft = draftTag != null,
            imetas = imetas,
            emojis = emojis,
        ) { senderMessage ->
            if (draftTag != null) {
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = listOf(),
                    ),
                )
            } else {
                createWraps(senderMessage, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                    onReady(
                        Result(
                            msg = senderMessage,
                            wraps = wraps,
                        ),
                    )
                }
            }
        }
    }

    fun createEncryptedFileNIP17(
        url: String,
        to: List<HexKey>,
        repliesToHex: List<HexKey>? = null,
        contentType: String?,
        algo: String,
        key: ByteArray,
        nonce: ByteArray? = null,
        originalHash: String? = null,
        hash: String? = null,
        size: Int? = null,
        dimensions: Dimension? = null,
        blurhash: String? = null,
        sensitiveContent: Boolean? = null,
        alt: String?,
        draftTag: String? = null,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        ChatMessageEncryptedFileHeaderEvent.create(
            url = url,
            to = to,
            repliesTo = repliesToHex,
            contentType = contentType,
            algo = algo,
            key = key,
            nonce = nonce,
            originalHash = originalHash,
            hash = hash,
            size = size,
            dimensions = dimensions,
            blurhash = blurhash,
            sensitiveContent = sensitiveContent,
            alt = alt,
            signer = signer,
            isDraft = draftTag != null,
        ) { senderMessage ->
            if (draftTag != null) {
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = listOf(),
                    ),
                )
            } else {
                createWraps(senderMessage, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                    onReady(
                        Result(
                            msg = senderMessage,
                            wraps = wraps,
                        ),
                    )
                }
            }
        }
    }

    fun createReactionWithinGroup(
        content: String,
        originalNote: Event,
        to: List<HexKey>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        ReactionEvent.create(
            content,
            originalNote,
            signer,
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
        emojiUrl: EmojiUrl,
        originalNote: Event,
        to: List<HexKey>,
        signer: NostrSigner,
        onReady: (Result) -> Unit,
    ) {
        val senderPublicKey = signer.pubKey

        ReactionEvent.create(
            emojiUrl,
            originalNote,
            signer,
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
