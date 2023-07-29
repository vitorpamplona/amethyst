package com.vitorpamplona.amethyst.service.model

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.TimeUtils
import com.vitorpamplona.amethyst.model.hexToByteArray
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.CryptoUtils
import com.vitorpamplona.amethyst.service.EncryptedInfo

@Immutable
class SealedGossipEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {
    private var innerEvent: Gossip? = null

    fun cachedGossip(privKey: ByteArray): Gossip? {
        if (innerEvent != null) return innerEvent

        val myInnerEvent = unseal(privKey = privKey)
        innerEvent = myInnerEvent
        return myInnerEvent
    }

    fun unseal(privKey: ByteArray): Gossip? = try {
        plainContent(privKey)?.let { gson.fromJson(it, Gossip::class.java) }
    } catch (e: Exception) {
        null
    }

    private fun plainContent(privKey: ByteArray): String? {
        if (content.isBlank()) return null

        return try {
            val sharedSecret = CryptoUtils.getSharedSecret(privKey, pubKey.hexToByteArray())

            val toDecrypt = gson.fromJson<EncryptedInfo>(
                content,
                EncryptedInfo::class.java
            )

            return CryptoUtils.decryptXChaCha(toDecrypt, sharedSecret)
        } catch (e: Exception) {
            Log.w("GeneralList", "Error decrypting the message ${e.message}")
            null
        }
    }

    companion object {
        const val kind = 13

        fun create(
            event: Event,
            encryptTo: HexKey,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): SealedGossipEvent {
            val gossip = Gossip.create(event)
            return create(gossip, encryptTo, privateKey, createdAt)
        }

        fun create(
            gossip: Gossip,
            encryptTo: HexKey,
            privateKey: ByteArray,
            createdAt: Long = TimeUtils.now()
        ): SealedGossipEvent {
            val sharedSecret = CryptoUtils.getSharedSecret(privateKey, encryptTo.hexToByteArray())

            val content = gson.toJson(
                CryptoUtils.encryptXChaCha(
                    gson.toJson(gossip),
                    sharedSecret
                )
            )
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val tags = listOf<List<String>>()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey)
            return SealedGossipEvent(id.toHexKey(), pubKey, createdAt, tags, content, sig.toHexKey())
        }
    }
}

open class Gossip(
    val id: HexKey,
    val pubKey: HexKey,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String
) {
    companion object {
        fun create(event: Event): Gossip {
            return Gossip(event.id, event.pubKey, event.createdAt, event.kind, event.tags, event.content)
        }
    }
}
