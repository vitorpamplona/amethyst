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
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class ChannelCreateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
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

        fun create(
            name: String?,
            about: String?,
            picture: String?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelCreateEvent) -> Unit
        ) {
            return create(
                ChannelData(
                    name, about, picture
                ),
                signer,
                createdAt,
                onReady
            )
        }

        fun create(
            channelInfo: ChannelData?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (ChannelCreateEvent) -> Unit
        ) {
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

            signer.sign(createdAt, kind, emptyArray(), content, onReady)
        }
    }

    @Immutable
    data class ChannelData(val name: String?, val about: String?, val picture: String?)
}
