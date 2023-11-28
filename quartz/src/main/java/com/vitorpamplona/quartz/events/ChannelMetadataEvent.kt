package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class ChannelMetadataEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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

        fun create(
            name: String?,
            about: String?,
            picture: String?,
            originalChannelIdHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelMetadataEvent) -> Unit
        ) {
            create(
                ChannelCreateEvent.ChannelData(
                    name, about, picture
                ),
                originalChannelIdHex,
                signer,
                createdAt,
                onReady
            )
        }

        fun create(
            newChannelInfo: ChannelCreateEvent.ChannelData?,
            originalChannelIdHex: String,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelMetadataEvent) -> Unit
        ) {
            val content =
                if (newChannelInfo != null) {
                    mapper.writeValueAsString(newChannelInfo)
                } else {
                    ""
                }

            val tags = listOf(arrayOf("e", originalChannelIdHex, "", "root"))
            signer.sign(createdAt, kind, tags.toTypedArray(), content, onReady)
        }
    }
}
