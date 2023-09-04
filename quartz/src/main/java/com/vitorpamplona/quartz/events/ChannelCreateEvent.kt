package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class ChannelCreateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun channelInfo(): ChannelData = try {
        mapper.readValue(content)
    } catch (e: Exception) {
        Log.e("ChannelMetadataEvent", "Can't parse channel info $content", e)
        ChannelData(null, null, null)
    }

    companion object {
        const val kind = 40

        fun create(channelInfo: ChannelData?, keyPair: KeyPair, createdAt: Long = TimeUtils.now()): ChannelCreateEvent {
            val content = try {
                if (channelInfo != null) {
                    mapper.writeValueAsString(channelInfo)
                } else {
                    ""
                }
            } catch (t: Throwable) {
                Log.e("ChannelCreateEvent", "Couldn't parse channel information", t)
                ""
            }

            val pubKey = keyPair.pubKey.toHexKey()
            val tags = emptyList<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.pubKey)
            return ChannelCreateEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(unsignedEvent: ChannelCreateEvent, signature: String): ChannelCreateEvent {
            return ChannelCreateEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }

    @Immutable
    data class ChannelData(val name: String?, val about: String?, val picture: String?)
}
