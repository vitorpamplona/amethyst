package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.Nip26
import nostr.postr.Utils
import java.util.Date

@Immutable
class ChannelMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseTextNoteEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    fun channel() = tags.firstOrNull {
        it.size > 3 && it[0] == "e" && it[3] == "root"
    }?.get(1) ?: tags.firstOrNull {
        it.size > 1 && it[0] == "e"
    }?.get(1)

    override fun replyTos() = tags.filter { it.firstOrNull() == "e" && it.getOrNull(1) != channel() }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 42

        fun create(
            message: String,
            channel: String,
            replyTos: List<String>? = null,
            mentions: List<String>? = null,
            zapReceiver: String?,
            privateKey: ByteArray,
            createdAt: Long = Date().time / 1000,
            markAsSensitive: Boolean,
            delegationToken: String,
            delegationHexKey: String,
            delegationSignature: String
        ): ChannelMessageEvent {
            val content = message
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = mutableListOf(
                listOf("e", channel, "", "root")
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
            if (delegationToken.isNotBlank()) {
                tags.add(Nip26.toTags(delegationToken, delegationSignature, delegationHexKey))
            }
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMessageEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
