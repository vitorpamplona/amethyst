package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    /**
     * Recepients intended to receive this conversation
     */
    private fun recipientsPubKey() = tags.mapNotNull {
        if (it.size > 1 && it[0] == "p") it[1] else null
    }

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    companion object {
        const val kind = 14

        fun create(
            msg: String,
            to: List<String>? = null,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: String? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): ChatMessageEvent {
            val content = msg
            val tags = mutableListOf<List<String>>()
            to?.forEach {
                tags.add(listOf("p", it))
            }
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it, "", "mention"))
            }
            zapReceiver?.let {
                tags.add(listOf("zap", it))
            }
            if (markAsSensitive) {
                tags.add(listOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(listOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.add(listOf("g", it))
            }

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, ClassifiedsEvent.kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ChatMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
