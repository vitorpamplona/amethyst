package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils

@Immutable
class ChannelMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig), IsInPublicChatChannel {

    override fun channel() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)
    fun channelInfo() =
        try {
            MetadataEvent.gson.fromJson(content, ChannelCreateEvent.ChannelData::class.java)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelCreateEvent.ChannelData(null, null, null)
        }

    companion object {
        const val kind = 41

        fun create(newChannelInfo: ChannelCreateEvent.ChannelData?, originalChannelIdHex: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ChannelMetadataEvent {
            val content =
                if (newChannelInfo != null) {
                    gson.toJson(newChannelInfo)
                } else {
                    ""
                }

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf(listOf("e", originalChannelIdHex, "", "root"))
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ChannelMetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
