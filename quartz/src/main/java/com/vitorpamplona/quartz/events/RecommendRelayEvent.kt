package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.signers.NostrSigner
import java.net.URI

@Immutable
class RecommendRelayEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun relay() = URI.create(content.trim())

    companion object {
        const val kind = 2

        fun create(
            relay: URI,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
            onReady: (RecommendRelayEvent) -> Unit
        ) {
            val content = relay.toString()
            val tags = listOf<List<String>>()
            signer.sign(createdAt, kind, tags, content, onReady)
        }
    }
}
