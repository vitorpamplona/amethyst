package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class HTTPAuthorizationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 27235

        fun create(
            url: String,
            method: String,
            body: String? = null,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): HTTPAuthorizationEvent {
            var hash = ""
            body?.let {
                hash = CryptoUtils.sha256(it.toByteArray()).toHexKey()
            }

            val tags = listOfNotNull(
                listOf("u", url),
                listOf("method", method),
                listOf("payload", hash)
            )

            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, "")
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return HTTPAuthorizationEvent(id.toHexKey(), pubKey, createdAt, tags, "", sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: HTTPAuthorizationEvent, signature: String
        ): HTTPAuthorizationEvent {
            return HTTPAuthorizationEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
