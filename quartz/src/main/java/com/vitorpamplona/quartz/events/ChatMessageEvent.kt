package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

@Immutable
class ChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChatMessageEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()
            to?.forEach {
                tags.add(arrayOf("p", it))
            }
            replyTos?.forEach {
                tags.add(arrayOf("e", it))
            }
            mentions?.forEach {
                tags.add(arrayOf("p", it, "", "mention"))
            }
            zapReceiver?.forEach {
                tags.add(arrayOf("zap", it.lnAddressOrPubKeyHex, it.relay ?: "", it.weight.toString()))
            }
            if (markAsSensitive) {
                tags.add(arrayOf("content-warning", ""))
            }
            zapRaiserAmount?.let {
                tags.add(arrayOf("zapraiser", "$it"))
            }
            geohash?.let {
                tags.addAll(geohashMipMap(it))
            }
            subject?.let {
                tags.add(arrayOf("subject", it))
            }

            signer.sign(createdAt, kind, tags.toTypedArray(), msg, onReady)
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