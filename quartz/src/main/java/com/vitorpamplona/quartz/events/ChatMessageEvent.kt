package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : WrappedEvent(id, pubKey, createdAt, kind, tags, content, sig), ChatroomKeyable {
    /**
     * Recepients intended to receive this conversation
     */
    fun recipientsPubKey() = tags.mapNotNull {
        if (it.size > 1 && it[0] == "p") it[1] else null
    }

    fun replyTo() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)

    fun talkingWith(oneSideHex: String): Set<HexKey> {
        val listedPubKeys = recipientsPubKey()

        val result = if (pubKey == oneSideHex) {
            listedPubKeys.toSet().minus(oneSideHex)
        } else {
            listedPubKeys.plus(pubKey).toSet().minus(oneSideHex)
        }

        if (result.isEmpty()) {
            // talking to myself
            return setOf(pubKey)
        }

        return result
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
            zapReceiver: List<ZapSplitSetup>? = null,
            markAsSensitive: Boolean = false,
            zapRaiserAmount: Long? = null,
            geohash: String? = null,
            keyPair: KeyPair,
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
            zapReceiver?.forEach {
                tags.add(listOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
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

            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, ClassifiedsEvent.kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return ChatMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: ChatMessageEvent,
            signature: String
        ): ChatMessageEvent {
            return ChatMessageEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}

interface ChatroomKeyable {
    fun chatroomKey(toRemove: HexKey): ChatroomKey
}

@Stable
data class ChatroomKey(
    val users: ImmutableSet<HexKey>
)