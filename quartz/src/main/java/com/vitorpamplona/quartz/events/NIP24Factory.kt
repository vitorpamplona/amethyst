package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import kotlin.math.sign

class NIP24Factory {
    data class Result(val msg: Event, val wraps: List<GiftWrapEvent>)

    private fun recursiveGiftWrapCreation(
        event: Event,
        remainingTos: List<HexKey>,
        signer: NostrSigner,
        output: MutableList<GiftWrapEvent>,
        onReady: (List<GiftWrapEvent>) -> Unit
    ) {
        if (remainingTos.isEmpty()) {
            onReady(output)
            return
        }

        val next = remainingTos.first()

        SealedGossipEvent.create(
            event = event,
            encryptTo = next,
            signer = signer
        ) { seal ->
            GiftWrapEvent.create(
                event = seal,
                recipientPubKey = next
            ) { giftWrap ->
                output.add(giftWrap)
                recursiveGiftWrapCreation(event, remainingTos.minus(next), signer, output, onReady)
            }
        }
    }

    private fun createWraps(event: Event, to: Set<HexKey>, signer: NostrSigner, onReady: (List<GiftWrapEvent>) -> Unit) {
        val wraps = mutableListOf<GiftWrapEvent>()
        recursiveGiftWrapCreation(event, to.toList(), signer, wraps, onReady)
    }

    fun createMsgNIP24(
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
        onReady: (Result) -> Unit
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
            geohash = geohash
        ) { senderMessage ->
            createWraps(senderMessage, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = wraps
                    )
                )
            }
        }
    }

    fun createReactionWithinGroup(content: String, originalNote: EventInterface, to: List<HexKey>, signer: NostrSigner, onReady: (Result) -> Unit) {
        val senderPublicKey = signer.pubKey

        ReactionEvent.create(
            content,
            originalNote,
            signer
        ) { senderReaction ->
            createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer) { wraps->
                onReady(
                    Result(
                        msg = senderReaction,
                        wraps = wraps
                    )
                )
            }
        }
    }

    fun createReactionWithinGroup(emojiUrl: EmojiUrl, originalNote: EventInterface, to: List<HexKey>, signer: NostrSigner, onReady: (Result) -> Unit) {
        val senderPublicKey = signer.pubKey

        ReactionEvent.create(
            emojiUrl,
            originalNote,
            signer
        ) { senderReaction ->
            createWraps(senderReaction, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderReaction,
                        wraps = wraps
                    )
                )
            }
        }
    }

    fun createTextNoteNIP24(
        msg: String,
        to: List<HexKey>,
        signer: NostrSigner,
        replyTos: List<String>? = null,
        mentions: List<String>? = null,
        addresses: List<ATag>?,
        extraTags: List<String>?,
        zapReceiver: List<ZapSplitSetup>? = null,
        markAsSensitive: Boolean = false,
        replyingTo: String?,
        root: String?,
        directMentions: Set<HexKey>,
        zapRaiserAmount: Long? = null,
        geohash: String? = null,
        onReady: (Result) -> Unit
    ) {
        val senderPublicKey = signer.pubKey

        TextNoteEvent.create(
            msg = msg,
            signer = signer,
            replyTos = replyTos,
            mentions = mentions,
            zapReceiver = zapReceiver,
            root = root,
            extraTags = extraTags,
            addresses = addresses,
            directMentions = directMentions,
            replyingTo = replyingTo,
            markAsSensitive = markAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash
        ) { senderMessage ->
            createWraps(senderMessage, to.plus(senderPublicKey).toSet(), signer) { wraps ->
                onReady(
                    Result(
                        msg = senderMessage,
                        wraps = wraps
                    )
                )
            }
        }
    }
}
