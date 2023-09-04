package com.vitorpamplona.quartz.events

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
class StatusEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : BaseAddressableEvent(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 30315

        fun create(
            msg: String,
            type: String,
            expiration: Long?,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): StatusEvent {
            val tags = mutableListOf<List<String>>()

            tags.add(listOf("d", type))
            expiration?.let { tags.add(listOf("expiration", it.toString())) }

            val pubKey = keyPair.pubKey.toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return StatusEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig?.toHexKey() ?: "")
        }

        fun update(
            event: StatusEvent,
            newStatus: String,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): StatusEvent {
            val tags = event.tags
            val pubKey = event.pubKey()
            val id = generateId(pubKey, createdAt, kind, tags, newStatus)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return StatusEvent(id.toHexKey(), pubKey, createdAt, tags, newStatus, sig?.toHexKey() ?: "")
        }

        fun clear(
            event: StatusEvent,
            keyPair: KeyPair,
            createdAt: Long = TimeUtils.now()
        ): StatusEvent {
            val msg = ""
            val tags = event.tags.filter { it.size > 1 && it[0] == "d" }
            val pubKey = event.pubKey()
            val id = generateId(pubKey, createdAt, kind, tags, msg)
            val sig = if (keyPair.privKey == null) null else CryptoUtils.sign(id, keyPair.privKey)
            return StatusEvent(id.toHexKey(), pubKey, createdAt, tags, msg, sig?.toHexKey() ?: "")
        }

        fun create(
            unsignedEvent: StatusEvent,
            signature: String
        ): StatusEvent {
            return StatusEvent(unsignedEvent.id, unsignedEvent.pubKey, unsignedEvent.createdAt, unsignedEvent.tags, unsignedEvent.content, signature)
        }
    }
}
