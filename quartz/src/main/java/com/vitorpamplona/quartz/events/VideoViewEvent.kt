package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner

@Immutable
class VideoViewEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 34237

        fun create(
            video: ATag,
            signer: NostrSigner,
            viewStart: Long?,
            viewEnd: Long?,
            createdAt: Long = TimeUtils.now(),
            onReady: (VideoViewEvent) -> Unit
        ) {
            val tags = mutableListOf<Array<String>>()

            val aTag = video.toTag()
            tags.add(arrayOf("d", aTag))
            tags.add(arrayOf("a", aTag))
            if (viewEnd != null)
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0", viewEnd.toString()))
            else
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0"))

            signer.sign(createdAt, kind, tags.toTypedArray(), "", onReady)
        }

        fun addViewedTime(
            event: VideoViewEvent,
            viewStart: Long?,
            viewEnd: Long?,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (VideoViewEvent) -> Unit
        ) {
            val tags = event.tags.toMutableList()
            if (viewEnd != null)
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0", viewEnd.toString()))
            else
                tags.add(arrayOf("viewed", viewStart?.toString() ?: "0"))

            signer.sign(createdAt, kind, tags.toTypedArray(), "", onReady)
        }
    }
}
