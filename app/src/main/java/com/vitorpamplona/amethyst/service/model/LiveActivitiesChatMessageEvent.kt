package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

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
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            markAsSensitive: Boolean,
            zapRaiserAmount: Long?
        ): LiveActivitiesChatMessageEvent {
            val content = message
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
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

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return LiveActivitiesChatMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
