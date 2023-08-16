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

        fun create(relay: String, challenge: String, privateKey: ByteArray, createdAt: Long = TimeUtils.now()): RelayAuthEvent {
            val content = ""
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf(
                listOf("relay", relay),
                listOf("challenge", challenge)
            )
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return RelayAuthEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}
