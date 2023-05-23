package com.vitorpamplona.amethyst.service.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import nostr.postr.Utils
import java.net.URI
import java.util.Date

@Immutable
class RecommendRelayEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey,
    val lenient: Boolean = false
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    fun relay() = if (lenient) {
        URI.create(content.trim())
    } else {
        URI.create(content)
    }

    companion object {
        const val kind = 2

        fun create(relay: URI, privateKey: ByteArray, createdAt: Long = Date().time / 1000): RecommendRelayEvent {
            val content = relay.toString()
            val pubKey = Utils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = Utils.sign(id, privateKey)
            return RecommendRelayEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
