package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils

@Immutable
class ChannelHideMessageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), IsInPublicChatChannel {
    override fun channel() = tags.firstOrNull {
        it.size > 3 && it[0] == "e" && it[3] == "root"
    }?.get(1) ?: tags.firstOrNull {
        it.size > 1 && it[0] == "e"
    }?.get(1)

    fun eventsToHide() = tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 43

        fun create(reason: String, messagesToHide: List<String>?, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ChannelHideMessageEvent {
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags =
                messagesToHide?.map {
                    listOf("e", it)
                } ?: emptyList()

            val id = generateId(pubKey, createdAt, kind, tags, reason)
            val sig = Utils.sign(id, privateKey)
            return ChannelHideMessageEvent(id.toHexKey(), pubKey, createdAt, tags, reason, sig.toHexKey())
        }
    }
}
