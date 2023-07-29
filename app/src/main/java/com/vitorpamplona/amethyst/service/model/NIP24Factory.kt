package com.vitorpamplona.amethyst.service.model

import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils

class NIP24Factory {
    fun createMsgNIP24(
        msg: String,
        to: List<HexKey>,
        from: ByteArray
    ): List<GiftWrapEvent> {
        val senderPublicKey = CryptoUtils.pubkeyCreate(from).toHexKey()

        val senderMessage = ChatMessageEvent.create(
            msg = msg,
            to = to,
            privateKey = from
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
}
