package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class ChannelMuteUserEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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

        fun create(
            reason: String,
            usersToMute: List<String>?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelMuteUserEvent) -> Unit
        ) {
            val content = reason
            val tags = usersToMute?.map {
                arrayOf("p", it)
            }?.toTypedArray() ?: emptyArray()

            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
