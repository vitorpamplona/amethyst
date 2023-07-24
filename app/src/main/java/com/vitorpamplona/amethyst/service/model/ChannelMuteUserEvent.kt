package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.Utils

@Immutable
class ChannelMuteUserEvent(
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

    fun usersToMute() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 44

        fun create(reason: String, usersToMute: List<String>?, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ChannelMuteUserEvent {
            val content = reason
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags =
                usersToMute?.map {
                    listOf("p", it)
                } ?: emptyList()

            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMuteUserEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
