package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class ChannelMuteUserEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun usersToMute() = tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }

    companion object {
        const val kind = 44

        fun create(reason: String, usersToMute: List<String>?, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMuteUserEvent {
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
