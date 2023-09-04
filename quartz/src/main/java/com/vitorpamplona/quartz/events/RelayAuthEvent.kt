package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class RelayAuthEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    fun relay() = tags.firstOrNull() { it.size > 1 && it[0] == "relay" }?.get(1)
    fun challenge() = tags.firstOrNull() { it.size > 1 && it[0] == "challenge" }?.get(1)

    companion object {
        const val kind = 22242

        fun create(relay: String, challenge: String, pubKey: HexKey, privateKey: ByteArray?, createdAt: Long = TimeUtils.now()): RelayAuthEvent {
            val content = ""
            val tags = listOf(
                listOf("relay", relay),
                listOf("challenge", challenge)
            )
            val localPubKey = if (pubKey.isBlank() && privateKey != null) CryptoUtils.pubkeyCreate(privateKey).toHexKey() else pubKey
            val id = generateId(localPubKey, createdAt, kind, tags, content)
            val sig = if (privateKey == null) null else CryptoUtils.sign(id, privateKey)
            return RelayAuthEvent(id.toHexKey(), localPubKey, createdAt, tags, content, sig?.toHexKey() ?: "")
        }

        fun create(unsignedEvent: RelayAuthEvent, signature: String): RelayAuthEvent {
            return RelayAuthEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
