package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.util.Date

@Immutable
class ChannelMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun channel() = tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)
    fun channelInfo() =
        try {
            MetadataEvent.gson.fromJson(content, ChannelCreateEvent.ChannelData::class.java)
        } catch (e: Exception) {
            Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
            ChannelCreateEvent.ChannelData(null, null, null)
        }

    companion object {
        const val kind = 41

        fun create(newChannelInfo: ChannelCreateEvent.ChannelData?, originalChannelIdHex: String, privateKey: ByteArray, createdAt: Long = Date().time / 1000): ChannelMetadataEvent {
            val content =
                if (newChannelInfo != null) {
                    gson.toJson(newChannelInfo)
                } else {
                    ""
                }

            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf(listOf("e", originalChannelIdHex, "", "root"))
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return ChannelMetadataEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
