package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
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

        fun create(channelInfo: ChannelData?, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): ChannelCreateEvent {
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

            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = emptyList<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return ChannelCreateEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }

    @Immutable
    data class ChannelData(val name: String?, val about: String?, val picture: String?)
}
