package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class LiveActivitiesChatMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    private fun innerActivity() = tags.firstOrNull {
        it.size > 3 && it[0] == "a" && it[3] == "root"
    } ?: tags.firstOrNull {
        it.size > 1 && it[0] == "a"
    }

    private fun activityHex() = innerActivity()?.let {
        it.getOrNull(1)
    }

    fun activity() = innerActivity()?.let {
        if (it.size > 1) {
            val aTagValue = it[1]
            val relay = it.getOrNull(2)

            ATag.parse(aTagValue, relay)
        } else {
            null
        }
    }

    override fun replyTos() = taggedEvents().minus(activityHex() ?: "")

    companion object {
        const val kind = 1311

        fun create(
            message: String,
            activity: ATag,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: String?,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now(),
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?,
            geohash: String? = null
        ): LiveActivitiesChatMessageEvent {
            val content = message
            val tags = mutableListOf(
                listOf("a", activity.toTag(), "", "root")
            )
            replyTos?.forEach {
                tags.add(listOf("e", it))
            }
            mentions?.forEach {
                tags.add(listOf("p", it))
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

            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return LiveActivitiesChatMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: LiveActivitiesChatMessageEvent, signature: String
        ): LiveActivitiesChatMessageEvent {
            return LiveActivitiesChatMessageEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
