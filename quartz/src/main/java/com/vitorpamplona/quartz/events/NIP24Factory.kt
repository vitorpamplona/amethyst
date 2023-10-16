package com.vitorpamplona.quartz.events

import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

class NIP24Factory {
    data class Result(val msg: Event, val wraps: List<GiftWrapEvent>)

    fun createMsgNIP24(
        msg: String,
        to: List<HexKey>,
        keyPair: KeyPair,
        subject: String? = null,
        replyTos: List<String>? = null,
        mentions: List<String>? = null,
        zapReceiver: List<ZapSplitSetup>? = null,
        markAsSensitive: Boolean = false,
        zapRaiserAmount: Long? = null,
        geohash: String? = null
    ): Result {
        val senderPublicKey = keyPair.pubKey.toHexKey()

        val senderMessage = ChatMessageEvent.create(
            msg = msg,
            to = to,
            keyPair = keyPair,
            subject = subject,
            replyTos = replyTos,
            mentions = mentions,
            zapReceiver = zapReceiver,
            markAsSensitive = markAsSensitive,
            zapRaiserAmount = zapRaiserAmount,
            geohash = geohash
        )

        return Result(
            msg = senderMessage,
            wraps = to.plus(senderPublicKey).map {
                GiftWrapEvent.create(
                    event = SealedGossipEvent.create(
                        event = senderMessage,
                        encryptTo = it,
                        privateKey = keyPair.privKey!!
                    ),
                    recipientPubKey = it
                )
            }
        )
    }

    fun createReactionWithinGroup(content: String, originalNote: EventInterface, to: List<HexKey>, from: KeyPair): Result {
        val senderPublicKey = from.pubKey.toHexKey()

        val senderReaction = ReactionEvent.create(
            content,
            originalNote,
            from
        )

        return Result(
            msg = senderReaction,
            wraps = to.plus(senderPublicKey).map {
                GiftWrapEvent.create(
                    event = SealedGossipEvent.create(
                        event = senderReaction,
                        encryptTo = it,
                        privateKey = from.privKey!!
                    ),
                    recipientPubKey = it
                )
            }
        )
    }

    fun createReactionWithinGroup(emojiUrl: EmojiUrl, originalNote: EventInterface, to: List<HexKey>, from: KeyPair): Result {
        val senderPublicKey = from.pubKey.toHexKey()

        val senderReaction = ReactionEvent.create(
            emojiUrl,
            originalNote,
            from
        )

        return Result(
            msg = senderReaction,
            wraps = to.plus(senderPublicKey).map {
                GiftWrapEvent.create(
                    event = SealedGossipEvent.create(
                        event = senderReaction,
                        encryptTo = it,
                        privateKey = from.privKey!!
                    ),
                    recipientPubKey = it
                )
            }
        )
    }

    fun createTextNoteNIP24(
        msg: String,
        to: List<HexKey>,
        keyPair: KeyPair,
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
        geohash: String? = null
    ): Result {
        val senderPublicKey = keyPair.pubKey.toHexKey()

        val senderMessage = TextNoteEvent.create(
            msg = msg,
            keyPair = keyPair,
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
        )

        return Result(
            msg = senderMessage,
            wraps = to.plus(senderPublicKey).map {
                GiftWrapEvent.create(
                    event = SealedGossipEvent.create(
                        event = senderMessage,
                        encryptTo = it,
                        privateKey = keyPair.privKey!!
                    ),
                    recipientPubKey = it
                )
            }
        )
    }
}
