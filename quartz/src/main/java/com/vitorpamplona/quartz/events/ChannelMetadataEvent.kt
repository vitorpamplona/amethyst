package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey

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
            mapper.readValue(content, ChannelCreateEvent.ChannelData::class.java)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelCreateEvent.ChannelData(null, null, null)
        }

    companion object {
        const val kind = 41

        fun create(newChannelInfo: ChannelCreateEvent.ChannelData?, originalChannelIdHex: String, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ChannelMetadataEvent {
            val content =
                if (newChannelInfo != null) {
                    mapper.writeValueAsString(newChannelInfo)
                } else {
                    ""
                }

            val pubKey = keyPair.pubKey.toHexKey()
            val tags = listOf(listOf("e", originalChannelIdHex, "", "root"))
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return ChannelMetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(unsignedEvent: ChannelMetadataEvent, signature: String): ChannelMetadataEvent {
            return ChannelMetadataEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
