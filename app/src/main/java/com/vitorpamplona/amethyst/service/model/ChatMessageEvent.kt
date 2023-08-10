package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.ChatroomKey
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), ChatroomKeyable {
    /**
     * Recepients intended to receive this conversation
     */
    fun recipientsPubKey() = tags.mapNotNull {
        if (it.size > 1 && it[0] == "p") it[1] else null
    }

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        return if (pubKey == oneSideHex) {
            if (listedPubKeys.isEmpty()) {
                // talking to myself
                return setOf(pubKey)
            } else {
                listedPubKeys.minus(oneSideHex).toSet()
            }
        } else {
            listedPubKeys.plus(pubKey).minus(oneSideHex).toSet()
        }
    }

    override fun chatroomKey(toRemove: String): ChatroomKey {
        return ChatroomKey(talkingWith(toRemove).toImmutableSet())
    }

    companion object {
        const val kind = 14

        fun create(
            msg: String,
            to: List<String>? = null,
            subject: String? = null,
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
            subject?.let {
                tags.add(listOf("subject", it))
            }

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, ClassifiedsEvent.kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ChatMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}

interface ChatroomKeyable {
    fun chatroomKey(toRemove: HexKey): ChatroomKey
}
