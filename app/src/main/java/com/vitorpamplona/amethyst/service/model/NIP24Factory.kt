package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils

class NIP24Factory {
    fun createMsgNIP24(
        msg: String,
        to: List<HexKey>,
        from: ByteArray,
        subject: String? = null,
        replyTos: List<String>? = null,
        mentions: List<String>? = null,
        zapReceiver: String? = null,
        markAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        geohash: String? = null
    ): List<GiftWrapEvent> {
        val senderPublicKey = CryptoUtils.pubkeyCreate(from).toHexKey()

        val senderMessage = ChatMessageEvent.create(
            msg = msg,
            to = to,
            privateKey = from,
            subject = subject,
            replyTos = replyTos,
            mentions = mentions,
            zapReceiver = zapReceiver,
            markAsSensitive = markAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash
        )

        return to.plus(senderPublicKey).map {
            GiftWrapEvent.create(
                event = SealedGossipEvent.create(
                    event = senderMessage,
                    encryptTo = it,
                    privateKey = from
                ),
                recipientPubKey = it
            )
        }
    }

    fun createReactionWithinGroup(content: String, originalNote: EventInterface, to: List<HexKey>, from: ByteArray): List<GiftWrapEvent> {
        val senderPublicKey = CryptoUtils.pubkeyCreate(from).toHexKey()

        val senderReaction = ReactionEvent.create(
            content,
            originalNote,
            from
        )

        return to.plus(senderPublicKey).map {
            GiftWrapEvent.create(
                event = SealedGossipEvent.create(
                    event = senderReaction,
                    encryptTo = it,
                    privateKey = from
                ),
                recipientPubKey = it
            )
        }
    }

    fun createReactionWithinGroup(emojiUrl: EmojiUrl, originalNote: EventInterface, to: List<HexKey>, from: ByteArray): List<GiftWrapEvent> {
        val senderPublicKey = CryptoUtils.pubkeyCreate(from).toHexKey()

        val senderReaction = ReactionEvent.create(
            emojiUrl,
            originalNote,
            from
        )

        return to.plus(senderPublicKey).map {
            GiftWrapEvent.create(
                event = SealedGossipEvent.create(
                    event = senderReaction,
                    encryptTo = it,
                    privateKey = from
                ),
                recipientPubKey = it
            )
        }
    }
}
